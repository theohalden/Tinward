package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.CategorySplit
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.SplitCodec
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Refunds and split categories feed the totals correctly (spec ledger rules 6 and the category engine). */
class SpendingAdjustmentsTest {
    private val zone = ZoneId.of("UTC")
    private val feb = YearMonth.of(2026, 2)
    private val at = Instant.parse("2026-02-10T10:00:00Z")
    private fun d(v: String) = BigDecimal(v)

    private fun tx(type: TransactionType, dir: TransactionDirection, amt: String, cat: TransactionCategory, splits: List<CategorySplit> = emptyList(), id: Long = 1) = Transaction(
        id = id, timestamp = at, amount = d(amt), direction = dir, type = type, category = cat, merchant = "m", rawMerchant = "m",
        accountId = null, accountSuffix = null, bank = null, paymentMethod = PaymentMethod.UNKNOWN, source = TransactionSource.MANUAL,
        sourceMessageId = null, rawSourceText = null, confidence = 1.0, notes = null, createdAt = at, splits = splits,
    )

    @Test fun `a full refund cancels the purchase in the month total and the category`() {
        val list = listOf(
            tx(TransactionType.EXPENSE, TransactionDirection.DEBIT, "500", TransactionCategory.SHOPPING, id = 1),
            tx(TransactionType.REFUND, TransactionDirection.CREDIT, "500", TransactionCategory.SHOPPING, id = 2),
        )
        assertEquals(BigDecimal.ZERO, FinanceCalculator.summaryForMonth(list, feb, zone).expense.stripTrailingZeros().let { if (it.signum() == 0) BigDecimal.ZERO else it })
        assertEquals(emptyList<Any>(), FinanceCalculator.categoryBreakdownForMonth(list, feb, zone))
    }

    @Test fun `a partial refund leaves the remainder as spending and is not income`() {
        val list = listOf(
            tx(TransactionType.EXPENSE, TransactionDirection.DEBIT, "500", TransactionCategory.SHOPPING, id = 1),
            tx(TransactionType.REFUND, TransactionDirection.CREDIT, "200", TransactionCategory.SHOPPING, id = 2),
        )
        val s = FinanceCalculator.summaryForMonth(list, feb, zone)
        assertEquals(0, d("300").compareTo(s.expense))
        assertEquals(0, BigDecimal.ZERO.compareTo(s.income))
        assertEquals(0, d("300").compareTo(FinanceCalculator.categoryBreakdownForMonth(list, feb, zone).single().amount))
    }

    @Test fun `a refund lowers only its own category`() {
        val list = listOf(
            tx(TransactionType.EXPENSE, TransactionDirection.DEBIT, "500", TransactionCategory.SHOPPING, id = 1),
            tx(TransactionType.EXPENSE, TransactionDirection.DEBIT, "400", TransactionCategory.FOOD, id = 2),
            tx(TransactionType.REFUND, TransactionDirection.CREDIT, "100", TransactionCategory.SHOPPING, id = 3),
        )
        val by = FinanceCalculator.categoryBreakdownForMonth(list, feb, zone).associate { it.category to it.amount }
        assertEquals(0, d("400").compareTo(by[TransactionCategory.SHOPPING]))
        assertEquals(0, d("400").compareTo(by[TransactionCategory.FOOD]))
    }

    @Test fun `a split transaction counts each part in its own category and the total once`() {
        val parts = listOf(CategorySplit(TransactionCategory.GROCERIES, d("700")), CategorySplit(TransactionCategory.HOME, d("300")))
        val list = listOf(tx(TransactionType.EXPENSE, TransactionDirection.DEBIT, "1000", TransactionCategory.GROCERIES, parts))
        assertEquals(0, d("1000").compareTo(FinanceCalculator.summaryForMonth(list, feb, zone).expense))
        val by = FinanceCalculator.categoryBreakdownForMonth(list, feb, zone).associate { it.category to it.amount }
        assertEquals(0, d("700").compareTo(by[TransactionCategory.GROCERIES]))
        assertEquals(0, d("300").compareTo(by[TransactionCategory.HOME]))
    }

    @Test fun `splits that do not add up fall back to the single category`() {
        val parts = listOf(CategorySplit(TransactionCategory.GROCERIES, d("700")), CategorySplit(TransactionCategory.HOME, d("200")))
        val list = listOf(tx(TransactionType.EXPENSE, TransactionDirection.DEBIT, "1000", TransactionCategory.FOOD, parts))
        assertEquals(TransactionCategory.FOOD, FinanceCalculator.categoryBreakdownForMonth(list, feb, zone).single().category)
    }

    @Test fun `split validation`() {
        val ok = listOf(CategorySplit(TransactionCategory.FOOD, d("40")), CategorySplit(TransactionCategory.HOME, d("60")))
        assertNull(SplitCodec.problem(d("100"), ok))
        assertNotNull(SplitCodec.problem(d("100"), ok.take(1)))
        assertNotNull(SplitCodec.problem(d("90"), ok))
        assertNotNull(SplitCodec.problem(d("100"), listOf(CategorySplit(TransactionCategory.FOOD, d("100")), CategorySplit(TransactionCategory.HOME, d("0")))))
    }

    @Test fun `a partial refund of a split purchase scales the parts and always adds up`() {
        val parts = listOf(CategorySplit(TransactionCategory.GROCERIES, d("60")), CategorySplit(TransactionCategory.HOME, d("40")))
        val scaled = SplitCodec.scale(parts, d("100"), d("30"))
        assertEquals(listOf(d("18.00"), d("12.00")), scaled.map { it.amount })
        // awkward rounding: 3 equal thirds of 100 refunded 50 -> still sums to exactly 50
        val thirds = listOf(CategorySplit(TransactionCategory.FOOD, d("33.34")), CategorySplit(TransactionCategory.HOME, d("33.33")), CategorySplit(TransactionCategory.FUEL, d("33.33")))
        assertEquals(0, d("50").compareTo(SplitCodec.scale(thirds, d("100"), d("50")).fold(BigDecimal.ZERO) { a, s -> a + s.amount }))
        // a refund too small to give every part a cent drops back to a single category
        assertEquals(emptyList<CategorySplit>(), SplitCodec.scale(parts, d("100"), d("0.01")))
        assertEquals(emptyList<CategorySplit>(), SplitCodec.scale(emptyList(), d("100"), d("30")))
    }

    @Test fun `a scaled split refund lowers each category by its share`() {
        val parts = listOf(CategorySplit(TransactionCategory.GROCERIES, d("60")), CategorySplit(TransactionCategory.HOME, d("40")))
        val refund = tx(TransactionType.REFUND, TransactionDirection.CREDIT, "30", TransactionCategory.GROCERIES, SplitCodec.scale(parts, d("100"), d("30")), id = 2)
        val list = listOf(tx(TransactionType.EXPENSE, TransactionDirection.DEBIT, "100", TransactionCategory.GROCERIES, parts, id = 1), refund)
        val by = FinanceCalculator.categoryBreakdownForMonth(list, feb, zone).associate { it.category to it.amount }
        assertEquals(0, d("42").compareTo(by[TransactionCategory.GROCERIES]))
        assertEquals(0, d("28").compareTo(by[TransactionCategory.HOME]))
    }

    @Test fun `split text round trips`() {
        val parts = listOf(CategorySplit(TransactionCategory.FOOD, d("40.50")), CategorySplit(TransactionCategory.HOME, d("59.50")))
        assertEquals(parts, SplitCodec.decode(SplitCodec.encode(parts)))
        assertEquals(emptyList<CategorySplit>(), SplitCodec.decode(null))
        assertEquals(emptyList<CategorySplit>(), SplitCodec.decode("garbage"))
    }
}
