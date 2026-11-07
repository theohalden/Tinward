package dev.kosh.financetracker.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Test

class BackupCodecTest {
    private val pass = "correct horse battery".toCharArray()
    private val payload = ByteArray(200_000) { (it * 31).toByte() }

    private fun encrypt(): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCodec.encrypt(pass, ByteArrayInputStream(payload), out, iterations = 10_000)
        return out.toByteArray()
    }

    private fun decrypt(file: ByteArray, p: CharArray = pass): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCodec.decrypt(p, ByteArrayInputStream(file), out)
        return out.toByteArray()
    }

    @Test fun `round trip`() = assertArrayEquals(payload, decrypt(encrypt()))

    @Test fun `file does not contain the plaintext`() {
        val needle = payload.copyOfRange(1000, 1032)
        val file = encrypt()
        assertFalse(String(file, Charsets.ISO_8859_1).contains(String(needle, Charsets.ISO_8859_1)))
    }

    @Test fun `wrong passphrase is rejected`() {
        try { decrypt(encrypt(), "wrong passphrase".toCharArray()); fail() } catch (e: BadBackupException) { }
    }

    @Test fun `a flipped byte anywhere is detected`() {
        val file = encrypt()
        for (pos in listOf(6, 20, 40, file.size / 2, file.size - 1)) {
            val bad = file.copyOf().also { it[pos] = (it[pos].toInt() xor 1).toByte() }
            try { decrypt(bad); fail("undetected change at $pos") } catch (e: BadBackupException) { }
        }
    }

    @Test fun `truncated file is rejected`() {
        val file = encrypt()
        try { decrypt(file.copyOf(file.size - 10)); fail() } catch (e: BadBackupException) { }
    }

    @Test fun `non backup file is rejected`() {
        try { decrypt("hello world, not a backup".toByteArray()); fail() } catch (e: BadBackupException) { }
    }
}
