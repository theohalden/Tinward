package dev.kosh.financetracker.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class LockState { NO_PIN, LOCKED, UNLOCKED }

/** How long the app may sit in the background before it asks for the PIN again. */
enum class AutoLock(val millis: Long, val label: String) {
    IMMEDIATELY(0, "Immediately"),
    THIRTY_SECONDS(30_000, "After 30 seconds"),
    ONE_MINUTE(60_000, "After 1 minute"),
    FIVE_MINUTES(300_000, "After 5 minutes"),
}

/**
 * Process-wide lock. Holds the data key in memory only while unlocked; on lock it is zeroed.
 * Nothing that reads the database or budget data may run while [state] is not UNLOCKED.
 */
@Singleton
class Vault @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("tinward_vault", Context.MODE_PRIVATE)
    private val storage = object : VaultStorage {
        override fun get(key: String): String? = prefs.getString(key, null)
        override fun put(key: String, value: String?) {
            prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.commit()
        }
    }
    private val core = VaultCore(storage)

    private val _state = MutableStateFlow(if (core.isConfigured) LockState.LOCKED else LockState.NO_PIN)
    val state: StateFlow<LockState> = _state.asStateFlow()

    @Volatile private var dek: ByteArray? = null
    @Volatile private var backgroundedAt: Long = 0L

    private val _autoLock = MutableStateFlow(readAutoLock())
    val autoLock: StateFlow<AutoLock> = _autoLock.asStateFlow()

    private val _secureScreen = MutableStateFlow(prefs.getBoolean(K_SECURE, !isDebuggable()))
    val secureScreen: StateFlow<Boolean> = _secureScreen.asStateFlow()

    val isUnlocked: Boolean get() = dek != null
    val failedAttempts: Int get() = core.failedAttempts()
    fun lockedUntil(): Long = core.lockedUntil()

    /** SQLCipher raw-key passphrase. Callers must only use this when [isUnlocked]. */
    fun databasePassphrase(): ByteArray? = dek?.let(DatabaseEncryptor::passphrase)

    /** AES key for small encrypted values stored outside the database. */
    fun dataKey(): ByteArray? = dek

    /** Creates the PIN and converts any existing plaintext data. Returns an error message or null. */
    suspend fun setup(secret: CharArray): String? = withContext(Dispatchers.IO) {
        VaultCore.pinProblem(secret)?.let { return@withContext it }
        val newDek = core.setup(secret)
        finishUnlock(newDek)
    }

    suspend fun unlock(secret: CharArray): UnlockResult = withContext(Dispatchers.IO) {
        val r = core.unlock(secret)
        if (r is UnlockResult.Success) {
            val err = finishUnlock(r.dek)
            if (err != null) return@withContext UnlockResult.Failed(err)
        }
        r
    }

    /** Biometric path: the key was already released by the Keystore-gated cipher. */
    suspend fun unlockWithKey(key: ByteArray): String? = withContext(Dispatchers.IO) { finishUnlock(key) }

    private fun finishUnlock(key: ByteArray): String? = try {
        DatabaseEncryptor.ensureEncrypted(context, key)
        dek = key
        _state.value = LockState.UNLOCKED
        null
    } catch (e: Exception) {
        "Could not open your data safely: ${e.message}"
    }

    fun lock() {
        dek?.fill(0)
        dek = null
        if (core.isConfigured) _state.value = LockState.LOCKED
    }

    /** Verifies the current secret, then re-protects the same data key with the new one. */
    suspend fun changeSecret(current: CharArray, new: CharArray): String? = withContext(Dispatchers.IO) {
        VaultCore.pinProblem(new)?.let { return@withContext it }
        when (val r = core.unlock(current)) {
            is UnlockResult.Success -> { core.wrap(new, r.dek); clearBiometric(); null }
            is UnlockResult.Throttled -> "Too many attempts. Try again later."
            else -> "Current PIN is incorrect."
        }
    }

    /** Verifies a secret without unlocking (used before sensitive actions like restore). */
    suspend fun verify(secret: CharArray): UnlockResult = withContext(Dispatchers.IO) { core.unlock(secret) }

    fun snapshotRecord() = core.snapshot() + ("bio_iv" to prefs.getString(K_BIO_IV, null)) + ("bio_wrapped" to prefs.getString(K_BIO_WRAPPED, null))
    fun restoreRecord(s: Map<String, String?>) {
        core.restoreSnapshot(s.filterKeys { it != K_BIO_IV && it != K_BIO_WRAPPED })
        prefs.edit().apply {
            s[K_BIO_IV]?.let { putString(K_BIO_IV, it) } ?: remove(K_BIO_IV)
            s[K_BIO_WRAPPED]?.let { putString(K_BIO_WRAPPED, it) } ?: remove(K_BIO_WRAPPED)
        }.commit()
    }

    /** Adopts a restored backup's data key under a (verified) current secret. */
    fun rewrap(secret: CharArray, key: ByteArray) { core.wrap(secret, key); clearBiometric() }

    // --- biometric wrap (key material is released only by Keystore + BiometricPrompt) ---
    val biometricEnabled: Boolean get() = prefs.getString(K_BIO_WRAPPED, null) != null
    fun biometricIv(): ByteArray? = prefs.getString(K_BIO_IV, null)?.let(Crypto::fromHex)
    fun biometricWrapped(): ByteArray? = prefs.getString(K_BIO_WRAPPED, null)?.let(Crypto::fromHex)
    fun saveBiometric(iv: ByteArray, wrapped: ByteArray) {
        prefs.edit().putString(K_BIO_IV, Crypto.toHex(iv)).putString(K_BIO_WRAPPED, Crypto.toHex(wrapped)).commit()
    }
    fun clearBiometric() {
        prefs.edit().remove(K_BIO_IV).remove(K_BIO_WRAPPED).commit()
        BiometricKeys.delete()
    }

    // --- lifecycle ---
    fun onBackgrounded() { backgroundedAt = System.currentTimeMillis() }

    /** Called when the app returns to the foreground. */
    fun onForegrounded() {
        if (!isUnlocked) return
        val away = System.currentTimeMillis() - backgroundedAt
        if (backgroundedAt != 0L && away >= _autoLock.value.millis) lock()
    }

    fun setAutoLock(value: AutoLock) {
        prefs.edit().putString(K_AUTO, value.name).apply()
        _autoLock.value = value
    }

    fun setSecureScreen(on: Boolean) {
        prefs.edit().putBoolean(K_SECURE, on).apply()
        _secureScreen.value = on
    }

    /** Set by the SMS receiver when a message arrived while locked; consumed after the next unlock. */
    var smsMissedWhileLocked: Boolean
        get() = prefs.getBoolean(K_SMS_MISSED, false)
        set(v) { prefs.edit().putBoolean(K_SMS_MISSED, v).commit() }

    private fun readAutoLock() = runCatching { AutoLock.valueOf(prefs.getString(K_AUTO, null) ?: "THIRTY_SECONDS") }
        .getOrDefault(AutoLock.THIRTY_SECONDS)

    private fun isDebuggable() =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private companion object {
        const val K_BIO_IV = "bio_iv"
        const val K_BIO_WRAPPED = "bio_wrapped"
        const val K_AUTO = "auto_lock"
        const val K_SECURE = "secure_screen"
        const val K_SMS_MISSED = "sms_missed"
    }
}
