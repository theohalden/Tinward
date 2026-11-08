package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
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
import java.time.YearMonth
import java.time.ZoneId

class DebtPlannerTest {
    private fun d(v: String) = BigDecimal(v)

    @Test fun `zero interest divides evenly`() {
        val r = DebtPlanner.payoff(d("1200"), d("0"), d("100"))
        assertEquals(12, r.months); assertEquals(0, BigDecimal.ZERO.compareTo(r.totalInterest)); assertEquals(0, d("1200").compareTo(r.totalPaid))
    }

    @Test fun `interest adds months and cost`() {
        val r = DebtPlanner.payoff(d("10000"), d("24"), d("500"))     // 2% a month
        assertEquals(26, r.months)                                                // n = -ln(1 - 10000*0.02/500) / ln(1.02) = 25.8
        assertTrue(r.totalInterest > d("2800") && r.totalInterest < d("3100"))   // about 2,950 of interest
        assertEquals(0, (d("10000") + r.totalInterest).compareTo(r.totalPaid))
    }

    @Test fun `a payment that does not cover the interest never pays off`() {
        val r = DebtPlanner.payoff(d("10000"), d("36"), d("250"))     // 3% a month = 300 interest
        assertNull(r.months)
    }

    @Test fun `a bigger payment finishes sooner and costs less`() {
        val slow = DebtPlanner.payoff(d("10000"), d("24"), d("400")); val fast = DebtPlanner.payoff(d("10000"), d("24"), d("1000"))
        assertTrue(fast.months!! < slow.months!!); assertTrue(fast.totalInterest < slow.totalInterest)
    }

    @Test fun `nothing owed is already done`() = assertEquals(0, DebtPlanner.payoff(BigDecimal.ZERO, d("20"), d("100")).months)

    @Test fun `payment for a target number of months clears the debt`() {
        val pay = DebtPlanner.paymentFor(d("10000"), d("24"), 12)
        val r = DebtPlanner.payoff(d("10000"), d("24"), pay)
        assertEquals(12, r.months)
        assertEquals(d("1000.00"), DebtPlanner.paymentFor(d("12000"), d("0"), 12))
    }

    private val a = DebtCard(1, "Visa", d("20000"), d("36"), d("100000"))      // high interest, big
    private val b = DebtCard(2, "Amex", d("3000"), d("12"), d("50000"))        // low interest, small

    @Test fun `avalanche costs no more interest than snowball`() {
        val out = DebtPlanner.compare(listOf(a, b), d("3000")).associate { it.strategy to it.result }
        val av = out.getValue(Strategy.AVALANCHE); val sn = out.getValue(Strategy.SNOWBALL)
        assertNotNull(av.months); assertNotNull(sn.months)
        assertTrue(av.totalInterest <= sn.totalInterest)
    }

    @Test fun `any extra beats minimums only`() {
        val out = DebtPlanner.compare(listOf(a, b), d("3000")).associate { it.strategy to it.result }
        assertTrue(out.getValue(Strategy.AVALANCHE).months!! < (out.getValue(Strategy.MINIMUMS).months ?: Int.MAX_VALUE))
        assertTrue(out.getValue(Strategy.AVALANCHE).totalInterest < out.getValue(Strategy.MINIMUMS).totalInterest)
    }

    @Test fun `everything is paid in full in the end`() {
        val r = DebtPlanner.simulate(listOf(a, b), d("3000"), Strategy.AVALANCHE)
        assertEquals(0, (d("23000") + r.totalInterest).compareTo(r.totalPaid))
    }

    @Test fun `a budget below the minimums is spread no further than the budget`() {
        val r = DebtPlanner.simulate(listOf(a, b), d("100"), Strategy.AVALANCHE)
        assertNull(r.months)   // 100 a month cannot clear 23,000 at those rates
    }

    @Test fun `no cards no debt`() = assertEquals(0, DebtPlanner.simulate(emptyList(), d("100"), Strategy.AVALANCHE).months)

    // ---- utilization history ----
    private val zone = ZoneId.of("UTC")
    private val card = Account(1, "Visa", AccountType.CREDIT_CARD, openingBalance = d("1000"), openingDate = Instant.parse("2026-01-01T00:00:00Z"), creditLimit = d("10000"))
    private fun tx(date: String, amt: String, dir: TransactionDirection) = Transaction(
        id = (date + amt).hashCode().toLong(), timestamp = Instant.parse("${date}T10:00:00Z"), amount = d(amt), direction = dir, type = TransactionType.CREDIT_CARD_PURCHASE,
        category = null, merchant = "m", rawMerchant = "m", accountId = 1, accountSuffix = null, bank = null, paymentMethod = PaymentMethod.UNKNOWN, source = TransactionSource.MANUAL,
        sourceMessageId = null, rawSourceText = null, confidence = 1.0, notes = null, createdAt = Instant.EPOCH,
    )

    @Test fun `utilization at each month end`() {
        val txs = listOf(tx("2026-09-10", "2000", TransactionDirection.DEBIT), tx("2026-10-05", "500", TransactionDirection.CREDIT), tx("2026-11-02", "4000", TransactionDirection.DEBIT))
        val h = Utilization.history(card, txs, YearMonth.of(2026, 11), 3, zone)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), h.map { it.month })
        assertEquals(0, d("3000").compareTo(h[0].owed)); assertEquals(30, h[0].percent)       // 1000 opening + 2000
        assertEquals(0, d("2500").compareTo(h[1].owed)); assertEquals(25, h[1].percent)
        assertEquals(0, d("6500").compareTo(h[2].owed)); assertEquals(65, h[2].percent)
    }

    @Test fun `months before tracking began are not invented`() {
        val late = card.copy(openingDate = Instant.parse("2026-10-15T00:00:00Z"))
        val h = Utilization.history(late, emptyList(), YearMonth.of(2026, 11), 3, zone)
        assertEquals(listOf(false, true, true), h.map { it.tracked })       // Sep ended before Oct 15; Oct ends after it
        assertNull(h[0].percent)
    }

    @Test fun `no limit means no percent`() {
        val h = Utilization.history(card.copy(creditLimit = null), emptyList(), YearMonth.of(2026, 11), 1, zone)
        assertNull(h.single().percent)
    }
}
