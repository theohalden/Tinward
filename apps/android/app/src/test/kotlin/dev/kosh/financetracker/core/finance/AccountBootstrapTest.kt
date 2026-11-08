package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class AccountBootstrapTest {
    private val now = Instant.parse("2026-11-08T00:00:00Z")
    private fun tx(id: Long, day: Int, dir: TransactionDirection, amt: String, bal: String? = null) = Transaction(
        id = id, timestamp = Instant.parse("2026-10-%02dT10:00:00Z".format(day)), amount = BigDecimal(amt), direction = dir,
        type = TransactionType.EXPENSE, category = null, merchant = "m", rawMerchant = "m", accountId = null, accountSuffix = "1234",
        bank = "X", paymentMethod = PaymentMethod.UNKNOWN, source = TransactionSource.SMS, sourceMessageId = "s$id", rawSourceText = null,
        confidence = 1.0, notes = null, createdAt = now, balanceAfter = bal?.let(::BigDecimal),
    )

    @Test fun `reported balance makes tracked balance equal it`() {
        val list = listOf(tx(1, 1, TransactionDirection.CREDIT, "1000"), tx(2, 2, TransactionDirection.DEBIT, "300", bal = "5700"), tx(3, 5, TransactionDirection.DEBIT, "200"))
        val o = AccountBootstrap.openingFor(list, setOf(1, 2, 3), isCard = false, now = now)
        assertTrue(o.fromReportedBalance)
        // opening + (+1000 -300) = 5700  ->  opening 5000; then -200 afterwards -> 5500
        assertEquals(BigDecimal("5000"), o.amount)
        assertEquals(BigDecimal("5500"), o.amount + BigDecimal("1000") - BigDecimal("300") - BigDecimal("200"))
    }

    @Test fun `no reported balance starts at zero from now instead of inventing one`() {
        val list = listOf(tx(1, 1, TransactionDirection.DEBIT, "9000"), tx(2, 2, TransactionDirection.DEBIT, "4000"))
        val o = AccountBootstrap.openingFor(list, setOf(1, 2), isCard = false, now = now)
        assertFalse(o.fromReportedBalance)
        assertEquals(BigDecimal.ZERO, o.amount)
        assertEquals(now, o.date)
    }

    @Test fun `cards always start from now`() {
        val o = AccountBootstrap.openingFor(listOf(tx(1, 1, TransactionDirection.DEBIT, "900", bal = "10")), setOf(1), isCard = true, now = now)
        assertEquals(now, o.date); assertEquals(BigDecimal.ZERO, o.amount)
    }

    @Test fun `duplicate observations that are not canonical do not distort the opening`() {
        val list = listOf(tx(1, 1, TransactionDirection.DEBIT, "100", bal = "900"), tx(2, 1, TransactionDirection.DEBIT, "100"))
        val o = AccountBootstrap.openingFor(list, setOf(1), isCard = false, now = now)
        assertEquals(BigDecimal("1000"), o.amount)
    }
}
