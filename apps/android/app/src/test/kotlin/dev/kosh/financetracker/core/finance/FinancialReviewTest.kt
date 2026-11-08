package dev.kosh.financetracker.core.finance

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
import java.time.YearMonth
import java.time.ZoneId

class FinancialReviewTest {
    private val zone = ZoneId.of("UTC")
    private val nov = YearMonth.of(2026, 11)
    private fun d(v: String) = BigDecimal(v)
    private fun tx(date: String, amt: String, merchant: String, cat: TransactionCategory, type: TransactionType = TransactionType.EXPENSE, cur: String = "INR") = Transaction(
        id = (date + merchant + amt).hashCode().toLong(), timestamp = Instant.parse("${date}T10:00:00Z"), amount = d(amt),
        direction = if (type == TransactionType.REFUND) TransactionDirection.CREDIT else TransactionDirection.DEBIT, type = type, category = cat,
        merchant = merchant, rawMerchant = merchant, accountId = null, accountSuffix = null, bank = null, paymentMethod = PaymentMethod.UNKNOWN,
        source = TransactionSource.MANUAL, sourceMessageId = null, rawSourceText = null, confidence = 1.0, notes = null, createdAt = Instant.EPOCH, currency = cur,
    )
    private fun review(list: List<Transaction>, income: String = "50000", bills: List<RecurringItem> = emptyList(), today: String = "2026-11-20") =
        FinancialReview.build(list, nov, bills, d(income), "INR", LocalDate.parse(today), zone)

    private val data = listOf(
        tx("2026-10-05", "1000", "Swiggy", TransactionCategory.FOOD), tx("2026-10-12", "2000", "Zomato", TransactionCategory.FOOD),
        tx("2026-10-20", "3000", "Uber", TransactionCategory.TRAVEL), tx("2026-10-10", "500", "Cafe", TransactionCategory.FOOD),
        tx("2026-11-03", "2500", "Swiggy", TransactionCategory.FOOD), tx("2026-11-10", "800", "Swiggy", TransactionCategory.FOOD),
        tx("2026-11-12", "1000", "Uber", TransactionCategory.TRAVEL), tx("2026-11-15", "600", "Cafe", TransactionCategory.FOOD),
    )

    @Test fun `top merchants are grouped case-insensitively with last month for comparison`() {
        val r = review(data)
        assertEquals(listOf("Swiggy", "Uber", "Cafe"), r.topMerchants.map { it.merchant })
        val swiggy = r.topMerchants.first()
        assertEquals(0, d("3300").compareTo(swiggy.amount)); assertEquals(2, swiggy.count); assertEquals(0, d("1000").compareTo(swiggy.previous))
        assertEquals(230, swiggy.changePercent)
        assertNull(review(listOf(tx("2026-11-03", "100", "New place", TransactionCategory.FOOD))).topMerchants.single().changePercent)
    }

    @Test fun `transactions without a merchant are not ranked but are counted aside`() {
        val r = review(data + tx("2026-11-04", "700", "Unknown", TransactionCategory.OTHER) + tx("2026-11-05", "300", " ", TransactionCategory.OTHER))
        assertTrue(r.topMerchants.none { it.merchant.equals("unknown", true) || it.merchant.isBlank() })
        assertEquals(0, d("1000").compareTo(r.unnamedSpend))
    }

    @Test fun `refunds reduce a merchant's total`() {
        val r = review(data + tx("2026-11-11", "300", "Swiggy", TransactionCategory.FOOD, TransactionType.REFUND))
        assertEquals(0, d("3000").compareTo(r.topMerchants.first().amount))
    }

    @Test fun `category risers and fallers compare with last month`() {
        val r = review(data)
        // Food: Oct 3500 -> Nov 3900 (+400). Travel: Oct 3000 -> Nov 1000 (-2000).
        assertEquals(TransactionCategory.FOOD, r.risers.single().category); assertEquals(0, d("400").compareTo(r.risers.single().delta))
        assertEquals(TransactionCategory.TRAVEL, r.fallers.single().category); assertEquals(0, d("-2000").compareTo(r.fallers.single().delta))
    }

    @Test fun `a new category is not called a riser because there is nothing to compare`() {
        val r = review(listOf(tx("2026-11-03", "900", "Gym", TransactionCategory.PERSONAL_CARE)))
        assertTrue(r.risers.isEmpty())
    }

    @Test fun `pace compares the same days last month and projects the end`() {
        val r = review(data, today = "2026-11-15")
        val p = r.pace!!
        assertEquals(0, d("4900").compareTo(p.spentToDate))
        assertEquals(0, d("3500").compareTo(p.lastMonthByThisDay))                    // Oct 5, 10, 12 = 1000+500+2000
        assertEquals(0, d("9800.00").compareTo(p.projectedMonthEnd))                  // 4900 over 15 days, scaled to 30
    }

    @Test fun `no projection in the first days of a month and no pace for past months`() {
        assertNull(review(data, today = "2026-11-03").pace!!.projectedMonthEnd)
        assertNull(FinancialReview.build(data, YearMonth.of(2026, 10), emptyList(), d("50000"), "INR", LocalDate.parse("2026-11-20"), zone).pace)
    }

    @Test fun `savings rate and fixed obligation share`() {
        val bills = listOf(
            RecurringItem(1, "Rent", d("15000"), "INR", RecurringKind.BILL, Frequency.MONTHLY, LocalDate.of(2026, 11, 1)),
            RecurringItem(2, "Insurance", d("12000"), "INR", RecurringKind.BILL, Frequency.YEARLY, LocalDate.of(2026, 11, 1)),
            RecurringItem(3, "Paused", d("999"), "INR", RecurringKind.BILL, Frequency.MONTHLY, LocalDate.of(2026, 11, 1), active = false),
            RecurringItem(4, "Salary", d("50000"), "INR", RecurringKind.INCOME, Frequency.MONTHLY, LocalDate.of(2026, 11, 1)),
        )
        val r = review(data, income = "50000", bills = bills)
        assertEquals(0, d("16000.00").compareTo(r.monthlyBills))            // 15000 + 12000/12
        assertEquals(32, r.fixedObligationPercent)
        assertEquals(90, r.savingsRatePercent)                               // spent 4900 of 50000, so 90.2% is left
    }

    @Test fun `no income means no rates`() {
        val r = review(data, income = "0")
        assertNull(r.savingsRatePercent); assertNull(r.fixedObligationPercent)
        assertNotNull(r.topMerchants)
    }

    @Test fun `weekly bills are converted to a monthly amount`() {
        val weekly = RecurringItem(1, "Cleaner", d("500"), "INR", RecurringKind.BILL, Frequency.WEEKLY, LocalDate.of(2026, 11, 1))
        assertEquals(0, d("2166.67").compareTo(FinancialReview.monthlyEquivalent(weekly)))
    }

    @Test fun `other currencies are left out`() {
        assertTrue(review(listOf(tx("2026-11-03", "50", "Netflix", TransactionCategory.SUBSCRIPTIONS, cur = "USD"))).topMerchants.isEmpty())
    }
}
