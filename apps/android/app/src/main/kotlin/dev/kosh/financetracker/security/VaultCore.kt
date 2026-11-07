package dev.kosh.financetracker.security

import java.security.GeneralSecurityException
import java.util.Base64

/** Where the vault's non-secret record lives. Nothing here lets anyone decrypt without the PIN. */
interface VaultStorage {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class MemoryVaultStorage : VaultStorage {
    private val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

sealed interface UnlockResult {
    class Success(val dek: ByteArray) : UnlockResult
    object WrongSecret : UnlockResult
    class Throttled(val retryAtMillis: Long) : UnlockResult
    object NotConfigured : UnlockResult
    /** PIN was right but the data could not be opened safely. */
    class Failed(val message: String) : UnlockResult
}

/**
 * PIN logic with no Android dependencies, so it is unit-tested on the JVM.
 *
 * Design: a random 256-bit data key (DEK) encrypts the database and budget data. The DEK is
 * stored only wrapped (AES-GCM) under a key derived from the PIN with PBKDF2 and a random salt.
 * A wrong PIN fails the GCM tag check; the PIN itself is never stored, not even hashed.
 */
class VaultCore(
    private val storage: VaultStorage,
    private val clock: () -> Long = System::currentTimeMillis,
    private val iterations: Int = Crypto.DEFAULT_ITERATIONS,
) {
    private val b64 = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    val isConfigured: Boolean get() = storage.get(K_WRAPPED) != null

    /** Creates a fresh DEK protected by [secret]. Returns the DEK. */
    fun setup(secret: CharArray): ByteArray {
        val dek = Crypto.randomBytes(Crypto.KEY_BYTES)
        wrap(secret, dek)
        clearFailures()
        return dek
    }

    /** Re-protects an existing DEK (PIN change, or adopting a restored backup's key). */
    fun wrap(secret: CharArray, dek: ByteArray) {
        val salt = Crypto.randomBytes(Crypto.SALT_BYTES)
        val kek = Crypto.deriveKey(secret, salt, iterations)
        val sealed = Crypto.seal(kek, dek, AAD)
        kek.fill(0)
        storage.put(K_SALT, b64.encodeToString(salt))
        storage.put(K_ITER, iterations.toString())
        storage.put(K_WRAPPED, b64.encodeToString(sealed))
    }

    fun unlock(secret: CharArray): UnlockResult {
        val wrapped = storage.get(K_WRAPPED) ?: return UnlockResult.NotConfigured
        val now = clock()
        val until = lockedUntil()
        if (now < until) return UnlockResult.Throttled(until)

        val salt = b64d.decode(storage.get(K_SALT))
        val iters = storage.get(K_ITER)?.toIntOrNull() ?: iterations
        val kek = Crypto.deriveKey(secret, salt, iters)
        return try {
            val dek = Crypto.open(kek, b64d.decode(wrapped), AAD)
            clearFailures()
            UnlockResult.Success(dek)
        } catch (e: GeneralSecurityException) {
            val fails = (storage.get(K_FAILS)?.toIntOrNull() ?: 0) + 1
            storage.put(K_FAILS, fails.toString())
            val delay = delayForFailures(fails)
            if (delay > 0) storage.put(K_UNTIL, (now + delay).toString())
            if (delay > 0) UnlockResult.Throttled(now + delay) else UnlockResult.WrongSecret
        } finally {
            kek.fill(0)
        }
    }

    /** For rolling back a failed restore. */
    fun snapshot(): Map<String, String?> = KEYS.associateWith { storage.get(it) }
    fun restoreSnapshot(s: Map<String, String?>) = s.forEach { (k, v) -> storage.put(k, v) }

    fun lockedUntil(): Long = storage.get(K_UNTIL)?.toLongOrNull() ?: 0L

    fun failedAttempts(): Int = storage.get(K_FAILS)?.toIntOrNull() ?: 0

    private fun clearFailures() {
        storage.put(K_FAILS, null)
        storage.put(K_UNTIL, null)
    }

    companion object {
        private val AAD = "tinward-vault-v1".toByteArray()
        private const val K_SALT = "salt"
        private const val K_ITER = "iterations"
        private const val K_WRAPPED = "wrapped_dek"
        private const val K_FAILS = "fails"
        private const val K_UNTIL = "locked_until"
        private val KEYS = listOf(K_SALT, K_ITER, K_WRAPPED, K_FAILS, K_UNTIL)

        /**
         * Escalating delay after repeated wrong PINs. Deliberately never wipes data: with no
         * account or server, a wipe-after-N policy would make an accidental lockout permanent.
         */
        fun delayForFailures(fails: Int): Long = when {
            fails < 5 -> 0L
            fails == 5 -> 30_000L
            fails == 6 -> 60_000L
            fails == 7 -> 300_000L
            fails == 8 -> 900_000L
            else -> 3_600_000L
        }

        /** Rejects secrets the spec calls too weak: under 6 characters, one repeated character, or a simple run. */
        fun weakness(secret: CharArray): String? {
            if (secret.size < 6) return "Use at least 6 characters."
            if (secret.all { it == secret[0] }) return "Choose something less repetitive."
            val s = String(secret)
            if (s.all { it.isDigit() }) {
                val asc = s.zipWithNext().all { (a, b) -> b - a == 1 }
                val desc = s.zipWithNext().all { (a, b) -> a - b == 1 }
                if (asc || desc) return "Avoid simple runs like 123456."
            }
            return null
        }
    }
}
