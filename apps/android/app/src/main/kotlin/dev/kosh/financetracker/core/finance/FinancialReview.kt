package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class MerchantSpend(val merchant: String, val amount: BigDecimal, val count: Int, val previous: BigDecimal) {
    val changePercent: Int? get() = if (previous.signum() <= 0) null else (amount - previous).multiply(BigDecimal(100)).divide(previous, 0, RoundingMode.HALF_UP).toInt()
}

data class CategoryMove(val category: TransactionCategory, val amount: BigDecimal, val previous: BigDecimal) {
    val delta: BigDecimal get() = amount - previous
}

/** How fast the month is going compared with last month and with the month's likely end. */
data class SpendingPace(val spentToDate: BigDecimal, val lastMonthByThisDay: BigDecimal, val projectedMonthEnd: BigDecimal?, val dayOfMonth: Int, val daysInMonth: Int)

data class ReviewReport(
    val month: YearMonth,
    val topMerchants: List<MerchantSpend>,
    val risers: List<CategoryMove>,
    val fallers: List<CategoryMove>,
    val pace: SpendingPace?,
    /** Share of income left after spending, as a whole percent. Null without income. */
    val savingsRatePercent: Int?,
    /** Monthly bills as a share of income, as a whole percent. Null without income. */
    val fixedObligationPercent: Int?,
    val monthlyBills: BigDecimal,
    /** Spending in transactions that carry no merchant name, left out of the merchant ranking. */
    val unnamedSpend: BigDecimal = BigDecimal.ZERO,
)

object FinancialReview {
    /** What a bill costs per month, however often it repeats. */
    fun monthlyEquivalent(item: RecurringItem): BigDecimal = when (item.frequency) {
        Frequency.MONTHLY -> item.amount
        Frequency.WEEKLY -> item.amount.multiply(BigDecimal(52)).divide(BigDecimal(12), 2, RoundingMode.HALF_UP)
        Frequency.YEARLY -> item.amount.divide(BigDecimal(12), 2, RoundingMode.HALF_UP)
    }

    fun build(
        transactions: List<Transaction>, month: YearMonth, bills: List<RecurringItem>, income: BigDecimal, currency: String,
        today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(),
    ): ReviewReport {
        val tx = transactions.filter { it.currency == currency && it.reconciliationNote == null }
        val spent = FinanceCalculator.summaryForMonth(tx, month, zone).expense.max(BigDecimal.ZERO)

        fun isNamed(t: Transaction) = !t.merchant.isNullOrBlank() && !t.merchant.trim().equals("unknown", ignoreCase = true)
        fun merchantTotals(m: YearMonth): Map<String, Pair<BigDecimal, Int>> =
            tx.filter { YearMonth.from(it.timestamp.atZone(zone)) == m }
                .filter { FinanceCalculator.spendParts(it).isNotEmpty() && isNamed(it) }
                .groupBy { it.merchant!!.trim().lowercase() }
                .mapValues { (_, list) -> list.fold(BigDecimal.ZERO) { a, t -> a + FinanceCalculator.spendParts(t).fold(BigDecimal.ZERO) { x, p -> x + p.second } } to list.size }
        val nowM = merchantTotals(month); val before = merchantTotals(month.minusMonths(1))
        val display = tx.filter { isNamed(it) }.associate { it.merchant!!.trim().lowercase() to it.merchant!!.trim() }
        val top = nowM.entries.filter { it.value.first.signum() > 0 }.sortedByDescending { it.value.first }.take(5)
            .map { MerchantSpend(display[it.key] ?: "Unknown", it.value.first, it.value.second, before[it.key]?.first ?: BigDecimal.ZERO) }

        val catNow = FinanceCalculator.categoryBreakdownForMonth(tx, month, zone).associate { it.category to it.amount }
        val catBefore = FinanceCalculator.categoryBreakdownForMonth(tx, month.minusMonths(1), zone).associate { it.category to it.amount }
        val moves = (catNow.keys + catBefore.keys).map { CategoryMove(it, catNow[it] ?: BigDecimal.ZERO, catBefore[it] ?: BigDecimal.ZERO) }
        val risers = moves.filter { it.delta.signum() > 0 && it.previous.signum() > 0 }.sortedByDescending { it.delta }.take(3)
        val fallers = moves.filter { it.delta.signum() < 0 }.sortedBy { it.delta }.take(3)

        val pace = if (month == YearMonth.from(today)) {
            val day = today.dayOfMonth
            val last = month.minusMonths(1)
            val lastByDay = tx.filter { val d = it.timestamp.atZone(zone).toLocalDate(); YearMonth.from(d) == last && d.dayOfMonth <= day }
            val lastToDate = FinanceCalculator.summaryForMonth(lastByDay, last, zone).expense.max(BigDecimal.ZERO)
            val projected = if (day >= 5) spent.multiply(BigDecimal(month.lengthOfMonth())).divide(BigDecimal(day), 2, RoundingMode.HALF_UP) else null   // too early in the month to extrapolate
            SpendingPace(spent, lastToDate, projected, day, month.lengthOfMonth())
        } else null

        val monthlyBills = bills.filter { it.active && it.kind == RecurringKind.BILL && it.currency == currency }.fold(BigDecimal.ZERO) { a, b -> a + monthlyEquivalent(b) }
        fun pct(part: BigDecimal): Int? = if (income.signum() <= 0) null else part.multiply(BigDecimal(100)).divide(income, 0, RoundingMode.HALF_UP).toInt()
        val unnamed = tx.filter { YearMonth.from(it.timestamp.atZone(zone)) == month && !isNamed(it) }
            .flatMap { FinanceCalculator.spendParts(it) }.fold(BigDecimal.ZERO) { a, p -> a + p.second }.max(BigDecimal.ZERO)
        return ReviewReport(month, top, risers, fallers, pace, pct(income - spent), pct(monthlyBills), monthlyBills, unnamed)
    }
}
