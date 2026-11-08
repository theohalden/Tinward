package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.random.Random

/**
 * A long-time user: three years, five accounts, 20,000 transactions. These guard against a
 * calculation that is fine at a few hundred rows and freezes the screen at tens of thousands.
 * The bounds are generous on purpose (a slow CI machine must still pass) but far below what
 * a quadratic slowdown would need.
 */
class LargeDataPerformanceTest {
    private val zone = ZoneId.of("UTC")
    private val rnd = Random(42)
    private val categories = TransactionCategory.values()
    private val start = LocalDate.of(2024, 1, 1)
    private val accounts = (1L..5L).map { Account(it, "Acct$it", if (it == 5L) AccountType.CREDIT_CARD else AccountType.BANK, openingBalance = BigDecimal("1000"), openingDate = Instant.parse("2023-12-01T00:00:00Z"), creditLimit = BigDecimal("100000")) }

    private val data: List<Transaction> = (0 until 20_000).map { i ->
        val day = start.plusDays(rnd.nextLong(0, 1000))
        val debit = rnd.nextInt(10) < 9
        Transaction(
            id = i.toLong() + 1, timestamp = day.atTime(rnd.nextInt(24), rnd.nextInt(60)).atZone(zone).toInstant(), amount = BigDecimal(rnd.nextInt(10, 5000)),
            direction = if (debit) TransactionDirection.DEBIT else TransactionDirection.CREDIT,
            type = if (debit) TransactionType.EXPENSE else TransactionType.INCOME, category = categories[rnd.nextInt(categories.size - 1)],
            merchant = "Merchant ${rnd.nextInt(300)}", rawMerchant = "m", accountId = (rnd.nextInt(5) + 1).toLong(), accountSuffix = "1234", bank = "HDFC",
            paymentMethod = PaymentMethod.UPI, source = TransactionSource.SMS, sourceMessageId = "s$i",
            rawSourceText = "Rs.${rnd.nextInt(10, 5000)}.00 debited from A/c XX1234 to VPA m$i@upi UPI Ref No $i", confidence = 0.9, notes = null, createdAt = Instant.EPOCH,
        )
    }

    private inline fun timed(label: String, limitMs: Long, block: () -> Unit) {
        val t0 = System.nanoTime(); block(); val ms = (System.nanoTime() - t0) / 1_000_000
        println("PERF $label: ${ms} ms")
        assertTrue("$label took ${ms} ms (limit $limitMs ms)", ms <= limitMs)
    }

    @Test fun `ledger projection stays fast`() = timed("LedgerProjection.project (20k)", 3_000) {
        // the screens read this on every database change
        dev.kosh.financetracker.core.finance.LedgerProjection.project(data)
    }

    @Test fun `month summaries and breakdowns stay fast`() = timed("12 months summary+breakdown+daily (20k)", 4_000) {
        val end = YearMonth.of(2026, 9)
        (0 until 12).forEach { k ->
            val m = end.minusMonths(k.toLong())
            FinanceCalculator.summaryForMonth(data, m, zone); FinanceCalculator.categoryBreakdownForMonth(data, m, zone); FinanceCalculator.dailySpendForMonth(data, m, zone)
        }
    }

    @Test fun `account balances and trends stay fast`() = timed("positions + 6 month trend (20k)", 4_000) {
        AccountLedger.positions(accounts, data); Trends.lastMonths(data, YearMonth.of(2026, 9), 6, zone)
        Utilization.history(accounts.last(), data, YearMonth.of(2026, 9), 6, zone)
    }

    @Test fun `financial review stays fast`() = timed("FinancialReview (20k)", 4_000) {
        FinancialReview.build(data, YearMonth.of(2026, 9), emptyList(), BigDecimal("100000"), "INR", LocalDate.of(2026, 9, 20), zone)
    }

    @Test fun `rollover over 24 months stays fast`() = timed("Rollover x 24 months (20k)", 4_000) {
        Rollover.carryIn(BigDecimal("1000"), YearMonth.of(2024, 1), YearMonth.of(2026, 9)) { m -> FinanceCalculator.categoryBreakdownForMonth(data, m, zone).firstOrNull()?.amount ?: BigDecimal.ZERO }
    }

    @Test fun `timeline with everyday estimate stays fast`() = timed("EverydaySpend + timeline (20k)", 4_000) {
        val bills = listOf(RecurringItem(1, "Rent", BigDecimal("15000"), "INR", RecurringKind.BILL, Frequency.MONTHLY, LocalDate.of(2026, 9, 25)))
        val perDay = EverydaySpend.perDay(data, bills, "INR", LocalDate.of(2026, 9, 20), 90, zone)
        val positions = AccountLedger.positions(accounts, data)
        TimelineBuilder.build(positions, bills.flatMap { Recurrence.pending(it, data, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 12, 19), zone) }, emptyList(), "INR", LocalDate.of(2026, 9, 20), 90, perDay)
    }

    /**
     * Guards the shape of the curve, not just one point: doubling the data must roughly double the
     * time. A quadratic version (the one that froze at 11.7 s for 40k rows) fails this by a wide margin.
     */
    @Test fun `ledger projection grows roughly linearly`() {
        fun make(n: Int) = data.take(n).let { base -> if (n <= base.size) base else base + base.take(n - base.size).mapIndexed { i, t -> t.copy(id = 100_000L + i, sourceMessageId = "x$i") } }
        fun time(n: Int): Long { val d = make(n); val t0 = System.nanoTime(); LedgerProjection.project(d); return (System.nanoTime() - t0) / 1_000_000 }
        time(5_000)                                           // warm up the JIT
        val small = maxOf(time(10_000), 20L); val big = time(40_000)
        println("PERF scaling: 10k=${small}ms 40k=${big}ms ratio=${big.toDouble() / small}")
        assertTrue("40k took ${big} ms against ${small} ms for 10k: that is not linear", big.toDouble() / small < 10.0)   // linear is about 4, quadratic about 16
    }
}
