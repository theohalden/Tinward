package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class TrendsTest {
    private val zone = ZoneId.of("UTC")
    private fun d(v: String) = BigDecimal(v)
    private fun tx(date: String, type: TransactionType, dir: TransactionDirection, amt: String) = Transaction(
        id = 1, timestamp = Instant.parse("${date}T10:00:00Z"), amount = d(amt), direction = dir, type = type, category = TransactionCategory.FOOD,
        merchant = "m", rawMerchant = "m", accountId = null, accountSuffix = null, bank = null, paymentMethod = PaymentMethod.UNKNOWN,
        source = TransactionSource.MANUAL, sourceMessageId = null, rawSourceText = null, confidence = 1.0, notes = null, createdAt = Instant.EPOCH,
    )
    private val list = listOf(
        tx("2026-09-10", TransactionType.EXPENSE, TransactionDirection.DEBIT, "100"),
        tx("2026-10-10", TransactionType.EXPENSE, TransactionDirection.DEBIT, "200"),
        tx("2026-10-12", TransactionType.INCOME, TransactionDirection.CREDIT, "1000"),
        tx("2026-11-03", TransactionType.EXPENSE, TransactionDirection.DEBIT, "300"),
        tx("2026-11-04", TransactionType.TRANSFER, TransactionDirection.DEBIT, "999"),     // never counted
        tx("2026-11-05", TransactionType.REFUND, TransactionDirection.CREDIT, "50"),         // reduces spending
    )

    @Test fun `last months run oldest to newest and include empty months`() {
        val p = Trends.lastMonths(list, YearMonth.of(2026, 11), 4, zone)
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), p.map { it.month })
        assertEquals(listOf(false, true, true, true), p.map { it.hasActivity })
        assertEquals(0, d("250").compareTo(p.last().spent))   // 300 - 50 refund; the transfer is ignored
        assertEquals(0, d("1000").compareTo(p[2].income))
    }

    @Test fun `comparison with the previous month`() {
        val c = Trends.comparison(list, YearMonth.of(2026, 11), zone)
        assertEquals(0, d("250").compareTo(c.spent)); assertEquals(0, d("200").compareTo(c.previousSpent))
        assertEquals(25, c.spentChangePercent)
        assertEquals(-100, c.incomeChangePercent)   // 1000 last month, nothing this month
    }

    @Test fun `no percent when the earlier month is empty`() {
        val c = Trends.comparison(list, YearMonth.of(2026, 9), zone)   // August is empty
        assertNull(c.spentChangePercent); assertNull(c.incomeChangePercent)
    }

    @Test fun `average spend ignores empty months`() {
        val p = Trends.lastMonths(list, YearMonth.of(2026, 11), 6, zone)   // May-Aug empty, Sep 100, Oct 200, Nov 250
        assertEquals(0, d("183.33").compareTo(Trends.averageSpent(p)))
        assertNull(Trends.averageSpent(emptyList()))
    }

    // ---- card dues ----
    @Test fun `next due date clamps and rolls to next month`() {
        assertEquals(LocalDate.of(2026, 11, 20), CardDues.nextDue(20, LocalDate.of(2026, 11, 8)))
        assertEquals(LocalDate.of(2026, 11, 20), CardDues.nextDue(20, LocalDate.of(2026, 11, 20)))     // today counts
        assertEquals(LocalDate.of(2026, 12, 5), CardDues.nextDue(5, LocalDate.of(2026, 11, 8)))
        assertEquals(LocalDate.of(2026, 2, 28), CardDues.nextDue(31, LocalDate.of(2026, 2, 3)))
        assertEquals(LocalDate.of(2026, 12, 31), CardDues.nextDue(31, LocalDate.of(2026, 12, 31)))
        assertEquals(LocalDate.of(2027, 1, 31), CardDues.nextDue(31, LocalDate.of(2027, 1, 1)))
    }

    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private fun card(id: Long, owed: String, due: Int?, archived: Boolean = false) =
        Account(id, "Card$id", AccountType.CREDIT_CARD, openingBalance = d(owed), openingDate = t0, dueDay = due, archived = archived)

    @Test fun `only cards that owe something and fall due soon are listed, soonest first`() {
        val cards = listOf(card(1, "5000", 20), card(2, "0", 15), card(3, "900", null), card(4, "700", 12), card(5, "800", 25, archived = true), card(6, "300", 30))
        val due = CardDues.upcoming(AccountLedger.positions(cards, emptyList()), LocalDate.of(2026, 11, 8), 14)
        assertEquals(listOf(4L, 1L), due.map { it.account.id })    // 12th and 20th; 30th is more than 14 days out
        assertTrue(due.all { it.owed.signum() > 0 })
    }
}
