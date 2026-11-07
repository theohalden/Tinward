package dev.kosh.financetracker.security

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BadBackupException(message: String) : IOException(message)

/**
 * File format for encrypted backups, independent of Android so it is unit-tested on the JVM.
 *
 * `"TWBK" | version(1) | iterations(4) | salt(16) | iv(12) | AES-256-GCM(payload) | tag`
 *
 * The key comes from a backup passphrase (PBKDF2), deliberately separate from the app PIN so a
 * backup stays restorable after a PIN change or on a new phone. The header is bound in as AAD.
 */
object BackupCodec {
    private val MAGIC = byteArrayOf('T'.code.toByte(), 'W'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION = 1
    private const val IV_BYTES = 12

    fun encrypt(
        passphrase: CharArray,
        payload: InputStream,
        out: OutputStream,
        iterations: Int = Crypto.DEFAULT_ITERATIONS,
    ) = encryptWith(passphrase, out, iterations) { payload.copyTo(it) }

    /** Streams whatever [writer] produces straight into the cipher, so no plaintext is staged on disk. */
    fun encryptWith(
        passphrase: CharArray,
        out: OutputStream,
        iterations: Int = Crypto.DEFAULT_ITERATIONS,
        writer: (OutputStream) -> Unit,
    ) {
        val salt = Crypto.randomBytes(Crypto.SALT_BYTES)
        val iv = Crypto.randomBytes(IV_BYTES)
        val header = header(iterations, salt, iv)
        out.write(header)
        val key = Crypto.deriveKey(passphrase, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            CipherOutputStream(out, cipher).use { writer(it) }
        } finally {
            key.fill(0)
        }
    }

    /** Writes the decrypted payload to [out]. Throws [BadBackupException] for a wrong passphrase or a damaged file. */
    fun decrypt(passphrase: CharArray, input: InputStream, out: OutputStream) =
        decryptWith(passphrase, input) { it.copyTo(out) }

    /**
     * Streams the payload to [reader]. AES-GCM only authenticates at the very end, so the rest of
     * the stream is drained afterwards to force that check: if it fails this throws, and the
     * caller must discard anything [reader] produced.
     */
    fun decryptWith(passphrase: CharArray, input: InputStream, reader: (InputStream) -> Unit) {
        val data = DataInputStream(input)
        val magic = ByteArray(4)
        try {
            data.readFully(magic)
            if (!magic.contentEquals(MAGIC)) throw BadBackupException("This is not a Tinward backup file.")
            val version = data.readUnsignedByte()
            if (version != VERSION) throw BadBackupException("This backup was made by a newer version of Tinward.")
            val iterations = data.readInt()
            if (iterations !in 10_000..5_000_000) throw BadBackupException("The backup header is damaged.")
            val salt = ByteArray(Crypto.SALT_BYTES).also(data::readFully)
            val iv = ByteArray(IV_BYTES).also(data::readFully)
            val key = Crypto.deriveKey(passphrase, salt, iterations)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                cipher.updateAAD(header(iterations, salt, iv))
                CipherInputStream(data, cipher).use { s -> reader(s); val sink = ByteArray(8192); while (s.read(sink) >= 0) { } }
            } finally {
                key.fill(0)
            }
        } catch (e: BadBackupException) {
            throw e
        } catch (e: GeneralSecurityException) {
            throw BadBackupException("Wrong passphrase, or the backup file is damaged.")
        } catch (e: IOException) {
            throw BadBackupException("Wrong passphrase, or the backup file is damaged.")
        }
    }

    private fun header(iterations: Int, salt: ByteArray, iv: ByteArray): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        DataOutputStream(buf).apply {
            write(MAGIC); writeByte(VERSION); writeInt(iterations); write(salt); write(iv)
        }
        return buf.toByteArray()
    }
}
