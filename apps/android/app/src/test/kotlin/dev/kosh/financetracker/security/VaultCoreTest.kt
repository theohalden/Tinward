package dev.kosh.financetracker.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VaultCoreTest {
    private var now = 1_000_000L
    // Low iteration count keeps tests fast; production uses Crypto.DEFAULT_ITERATIONS.
    private fun vault(storage: VaultStorage = MemoryVaultStorage()) = VaultCore(storage, { now }, iterations = 1_000)

    @Test fun `setup then unlock with the right PIN returns the same key`() {
        val v = vault()
        val dek = v.setup("482915".toCharArray())
        val r = v.unlock("482915".toCharArray())
        assertTrue(r is UnlockResult.Success)
        assertArrayEquals(dek, (r as UnlockResult.Success).dek)
    }

    @Test fun `wrong PIN does not unlock`() {
        val v = vault()
        v.setup("482915".toCharArray())
        assertEquals(UnlockResult.WrongSecret, v.unlock("000111".toCharArray()))
    }

    @Test fun `unconfigured vault reports it`() {
        assertFalse(vault().isConfigured)
        assertEquals(UnlockResult.NotConfigured, vault().unlock("482915".toCharArray()))
    }

    @Test fun `PIN is not stored in any stored value`() {
        val s = MemoryVaultStorage()
        vault(s).setup("482915".toCharArray())
        listOf("salt", "iterations", "wrapped_dek").forEach { assertFalse(s.get(it)!!.contains("482915")) }
    }

    @Test fun `five wrong PINs throttle, even the right PIN waits, then works`() {
        val v = vault()
        v.setup("482915".toCharArray())
        repeat(4) { assertEquals(UnlockResult.WrongSecret, v.unlock("111222".toCharArray())) }
        assertTrue(v.unlock("111222".toCharArray()) is UnlockResult.Throttled)
        assertTrue(v.unlock("482915".toCharArray()) is UnlockResult.Throttled)
        now += 31_000
        assertTrue(v.unlock("482915".toCharArray()) is UnlockResult.Success)
        assertEquals(0, v.failedAttempts())
    }

    @Test fun `throttle survives a new VaultCore over the same storage (process death)`() {
        val s = MemoryVaultStorage()
        val a = vault(s)
        a.setup("482915".toCharArray())
        repeat(5) { a.unlock("111222".toCharArray()) }
        assertTrue(vault(s).unlock("482915".toCharArray()) is UnlockResult.Throttled)
    }

    @Test fun `delay escalates`() {
        assertEquals(0L, VaultCore.delayForFailures(4))
        assertTrue(VaultCore.delayForFailures(6) > VaultCore.delayForFailures(5))
        assertTrue(VaultCore.delayForFailures(9) > VaultCore.delayForFailures(8))
    }

    @Test fun `changing the PIN keeps the data key`() {
        val v = vault()
        val dek = v.setup("482915".toCharArray())
        v.wrap("739104".toCharArray(), dek)
        assertEquals(UnlockResult.WrongSecret, v.unlock("482915".toCharArray()))
        assertArrayEquals(dek, (v.unlock("739104".toCharArray()) as UnlockResult.Success).dek)
    }

    @Test fun `weak secrets are rejected`() {
        assertNotNull(VaultCore.weakness("1234".toCharArray()))
        assertNotNull(VaultCore.weakness("111111".toCharArray()))
        assertNotNull(VaultCore.weakness("123456".toCharArray()))
        assertNotNull(VaultCore.weakness("654321".toCharArray()))
        assertNull(VaultCore.weakness("482915".toCharArray()))
        assertNull(VaultCore.weakness("correct horse".toCharArray()))
    }

    @Test fun `tampered wrapped key fails closed`() {
        val s = MemoryVaultStorage()
        val v = vault(s)
        v.setup("482915".toCharArray())
        val w = s.get("wrapped_dek")!!
        s.put("wrapped_dek", w.dropLast(4) + (if (w.takeLast(4) == "AAAA") "BBBB" else "AAAA"))
        try { v.unlock("482915".toCharArray()).let { assertFalse(it is UnlockResult.Success) } } catch (e: Exception) { /* also acceptable: fails closed */ }
    }
}
