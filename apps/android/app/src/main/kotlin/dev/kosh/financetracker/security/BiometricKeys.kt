package dev.kosh.financetracker.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Biometric unlock. The data key is wrapped by an AES key that lives in the Android Keystore and
 * can only be used after a strong biometric check (per use). Adding a fingerprint/face to the
 * device invalidates the key, which falls back to the PIN: that is the intended behaviour.
 * The PIN stays the root credential; biometrics never replace it.
 */
object BiometricKeys {
    private const val ALIAS = "tinward_biometric_wrap"
    private const val PROVIDER = "AndroidKeyStore"

    fun canUse(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun delete() {
        runCatching { KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(ALIAS) }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION") setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()
    }

    private fun prompt(
        activity: FragmentActivity,
        title: String,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onFail: (String) -> Unit,
    ) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    result.cryptoObject?.cipher?.let(onSuccess) ?: onFail("Biometric check returned no key")
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onFail(errString.toString()) }
            },
        ).authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    /** Turns biometric unlock on. Must be called while unlocked, with the data key. */
    fun enable(activity: FragmentActivity, vault: Vault, onDone: (String?) -> Unit) {
        val dek = vault.dataKey() ?: return onDone("Unlock first.")
        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        } catch (e: Exception) { delete(); return onDone("Biometrics are not available: ${e.message}") }
        prompt(activity, "Turn on biometric unlock", cipher, { c ->
            vault.saveBiometric(c.iv, c.doFinal(dek))
            onDone(null)
        }, { onDone(it) })
    }

    /** Releases the data key after a biometric check. [onKey] gets the key; [onFail] a short reason. */
    fun unlock(activity: FragmentActivity, vault: Vault, onKey: (ByteArray) -> Unit, onFail: (String) -> Unit) {
        val iv = vault.biometricIv()
        val wrapped = vault.biometricWrapped()
        if (iv == null || wrapped == null) return onFail("Biometric unlock is off.")
        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            vault.clearBiometric(); return onFail("Your biometrics changed. Use your PIN, then turn biometrics on again.")
        } catch (e: Exception) { return onFail("Biometric unlock is unavailable.") }
        prompt(activity, "Unlock Tinward", cipher, { c ->
            runCatching { c.doFinal(wrapped) }.onSuccess(onKey).onFailure { onFail("Biometric unlock failed.") }
        }, onFail)
    }
}
