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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MoneyTimelineTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 8)
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private fun d(v: String) = BigDecimal(v)
    private fun day(s: String) = LocalDate.parse(s)

    private val bank = Account(1, "HDFC", AccountType.BANK, openingBalance = d("30000"), openingDate = t0)
    private val cash = Account(2, "Cash", AccountType.CASH, openingBalance = d("1000"), openingDate = t0)
    private val usd = Account(3, "Wise", AccountType.BANK, currency = "USD", openingBalance = d("500"), openingDate = t0)
    private val card = Account(4, "Visa", AccountType.CREDIT_CARD, openingBalance = d("4000"), openingDate = t0, dueDay = 20)

    private fun item(name: String, amt: String, due: String, kind: RecurringKind = RecurringKind.BILL, freq: Frequency = Frequency.MONTHLY, currency: String = "INR") =
        RecurringItem(name.hashCode().toLong(), name, d(amt), currency, kind, freq, day(due))

    private fun build(accounts: List<Account>, items: List<RecurringItem>, days: Int = 30, everyday: BigDecimal? = null, whatIfs: List<WhatIf> = emptyList(), txs: List<Transaction> = emptyList()): MoneyTimeline {
        val positions = AccountLedger.positions(accounts, txs)
        val pending = items.flatMap { Recurrence.pending(it, txs, today, today.plusDays(days.toLong()), zone) }
        return TimelineBuilder.build(positions, pending, CardDues.upcoming(positions, today, days.toLong()), "INR", today, days, everyday, whatIfs)
    }

    @Test fun `starts from tracked asset balances and has one point per day`() {
        val t = build(listOf(bank, cash), emptyList())
        assertEquals(d("31000.00"), t.startBalance); assertEquals(31, t.points.size); assertEquals(today, t.points.first().date); assertEquals(today.plusDays(30), t.end.date)
        assertEquals(d("31000.00"), t.end.balance)
    }

    @Test fun `a bill lowers the balance from its due date`() {
        val t = build(listOf(bank), listOf(item("Rent", "15000", "2026-11-20")))
        assertEquals(d("30000.00"), t.points.first { it.date == day("2026-11-19") }.balance)
        assertEquals(d("15000.00"), t.points.first { it.date == day("2026-11-20") }.balance)
        assertEquals(d("15000.00"), t.end.balance)
    }

    @Test fun `income raises it`() {
        val t = build(listOf(bank), listOf(item("Salary", "80000", "2026-11-25", RecurringKind.INCOME)))
        assertEquals(d("110000.00"), t.end.balance)
    }

    @Test fun `the lowest point and first shortfall are found`() {
        val t = build(listOf(bank), listOf(item("Rent", "35000", "2026-11-12"), item("Salary", "50000", "2026-11-28", RecurringKind.INCOME)))
        assertEquals(d("-5000.00"), t.lowest.balance); assertEquals(day("2026-11-12"), t.lowest.date)
        assertEquals(day("2026-11-12"), t.firstShortfall!!.date)
        assertEquals(d("45000.00"), t.end.balance)
    }

    @Test fun `no shortfall means none reported`() = assertNull(build(listOf(bank), listOf(item("Rent", "1000", "2026-11-12"))).firstShortfall)

    @Test fun `overdue items are assumed to happen today`() {
        val t = build(listOf(bank), listOf(item("Old bill", "2000", "2026-11-01")), 30)
        assertEquals(d("28000.00"), t.points.first().balance)
        assertEquals(today, t.events.first().date)
    }

    @Test fun `window length limits what is included`() {
        val items = listOf(item("Rent", "15000", "2026-11-20"), item("Insurance", "9000", "2026-12-30", freq = Frequency.YEARLY))
        assertEquals(d("15000.00"), build(listOf(bank), items, 30).end.balance)            // only November rent
        assertEquals(d("-9000.00"), build(listOf(bank), items, 60).end.balance)           // Nov and Dec rent plus the yearly insurance on 30 Dec
    }

    @Test fun `monthly bills repeat inside a long window`() {
        val t = build(listOf(bank), listOf(item("Rent", "10000", "2026-11-20")), 90)
        assertEquals(3, t.events.count { it.kind == EventKind.BILL })       // 20 Nov, 20 Dec, 20 Jan
        assertEquals(d("0.00"), t.end.balance)
    }

    @Test fun `card payment assumes the full balance on the due day`() {
        val t = build(listOf(bank, card), emptyList())
        val e = t.events.single { it.kind == EventKind.CARD_PAYMENT }
        assertEquals(day("2026-11-20"), e.date); assertEquals(d("-4000"), e.amount)
        assertEquals(d("26000.00"), t.end.balance)
    }

    @Test fun `a card already added as a bill is not paid twice`() {
        val t = build(listOf(bank, card), listOf(item("Visa payment", "4000", "2026-11-20")))
        assertEquals(1, t.events.size)
        assertEquals(d("26000.00"), t.end.balance)
    }

    @Test fun `other currencies are excluded and noted`() {
        val t = build(listOf(bank, usd), listOf(item("Netflix", "10", "2026-11-15", currency = "USD")))
        assertEquals(d("30000.00"), t.startBalance); assertTrue(t.events.isEmpty())
        assertTrue(t.assumptions.any { it.contains("never converted") })
    }

    @Test fun `everyday spending lowers it a day at a time and is stated`() {
        val t = build(listOf(bank), emptyList(), 30, everyday = d("100"))
        assertEquals(d("27000.00"), t.end.balance)
        assertTrue(t.assumptions.any { it.contains("100.00 a day") })
        assertTrue(build(listOf(bank), emptyList()).assumptions.any { it.contains("Does not include everyday spending") })
    }

    @Test fun `what ifs change the forecast and are labelled`() {
        val base = build(listOf(bank), emptyList())
        val t = build(listOf(bank), emptyList(), whatIfs = listOf(WhatIf(day("2026-11-25"), "Laptop", d("-60000"))))
        assertEquals(d("-30000.00"), t.end.balance); assertEquals(d("30000.00"), base.end.balance)
        assertEquals(EventKind.WHAT_IF, t.events.single().kind); assertTrue(t.assumptions.any { it.contains("what-if") })
    }

    @Test fun `what ifs outside the window are ignored`() {
        assertEquals(d("30000.00"), build(listOf(bank), emptyList(), whatIfs = listOf(WhatIf(day("2027-03-01"), "x", d("-1")), WhatIf(day("2026-10-01"), "y", d("-1")))).end.balance)
    }

    @Test fun `archived accounts and paused schedules are left out`() {
        val t = build(listOf(bank, cash.copy(archived = true)), listOf(item("Rent", "5000", "2026-11-20").copy(active = false)))
        assertEquals(d("30000.00"), t.end.balance)
    }

    // ---- everyday spending estimate ----
    private fun spend(date: String, amt: String, merchant: String) = Transaction(
        id = merchant.hashCode().toLong() + date.hashCode(), timestamp = Instant.parse("${date}T10:00:00Z"), amount = d(amt), direction = TransactionDirection.DEBIT,
        type = TransactionType.EXPENSE, category = TransactionCategory.FOOD, merchant = merchant, rawMerchant = merchant, accountId = null, accountSuffix = null, bank = null,
        paymentMethod = PaymentMethod.UNKNOWN, source = TransactionSource.MANUAL, sourceMessageId = null, rawSourceText = null, confidence = 1.0, notes = null, createdAt = Instant.EPOCH,
    )

    @Test fun `everyday average leaves out bill payments and uses the days that have data`() {
        val rent = item("Rent", "15000", "2026-10-20")
        val txs = listOf(spend("2026-10-09", "500", "Cafe"), spend("2026-10-15", "300", "Grocer"), spend("2026-10-20", "15000", "Rent"), spend("2026-11-02", "200", "Cafe"))
        val avg = EverydaySpend.perDay(txs, listOf(rent), "INR", today, 90, zone)
        assertNotNull(avg)
        // 1000 of loose spending over the 30 days since the first transaction
        assertEquals(0, d("33.33").compareTo(avg))
    }

    @Test fun `no spending means no estimate`() = assertNull(EverydaySpend.perDay(emptyList(), emptyList(), "INR", today, 90, zone))
}
