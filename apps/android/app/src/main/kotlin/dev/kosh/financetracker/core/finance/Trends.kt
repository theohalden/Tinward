package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Income and spending for one month. Transfers, adjustments and refunds' effects follow [FinanceCalculator]. */
data class MonthPoint(val month: YearMonth, val income: BigDecimal, val spent: BigDecimal) {
    val hasActivity: Boolean get() = income.signum() != 0 || spent.signum() != 0
}

/** This month against the one before it. A change is null when the earlier month has nothing to compare with. */
data class MonthComparison(
    val spent: BigDecimal, val previousSpent: BigDecimal,
    val income: BigDecimal, val previousIncome: BigDecimal,
) {
    val spentChangePercent: Int? get() = pct(spent, previousSpent)
    val incomeChangePercent: Int? get() = pct(income, previousIncome)
    private fun pct(now: BigDecimal, before: BigDecimal): Int? =
        if (before.signum() <= 0) null else (now - before).multiply(BigDecimal(100)).divide(before, 0, RoundingMode.HALF_UP).toInt()
}

object Trends {
    /** The [count] months ending at [end], oldest first, so a chart reads left to right. */
    fun lastMonths(transactions: List<Transaction>, end: YearMonth, count: Int = 6, zone: ZoneId = ZoneId.systemDefault()): List<MonthPoint> =
        (count - 1 downTo 0).map { back ->
            val m = end.minusMonths(back.toLong())
            val s = FinanceCalculator.summaryForMonth(transactions, m, zone)
            MonthPoint(m, s.income, s.expense.max(BigDecimal.ZERO))
        }

    fun comparison(transactions: List<Transaction>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): MonthComparison {
        val now = FinanceCalculator.summaryForMonth(transactions, month, zone)
        val before = FinanceCalculator.summaryForMonth(transactions, month.minusMonths(1), zone)
        return MonthComparison(now.expense, before.expense, now.income, before.income)
    }

    /** Average monthly spending over months that had any activity, ignoring empty months so a new user is not diluted. */
    fun averageSpent(points: List<MonthPoint>): BigDecimal? {
        val active = points.filter { it.spent.signum() > 0 }
        if (active.isEmpty()) return null
        return active.fold(BigDecimal.ZERO) { a, p -> a + p.spent }.divide(BigDecimal(active.size), 2, RoundingMode.HALF_UP)
    }
}

/** A credit card payment coming up. */
data class CardDue(val account: Account, val date: LocalDate, val owed: BigDecimal)

object CardDues {
    /** The next due date for a day-of-month, clamped to short months (the 31st becomes the 28th or 30th). */
    fun nextDue(dueDay: Int, today: LocalDate): LocalDate {
        fun inMonth(base: LocalDate) = base.withDayOfMonth(minOf(dueDay, base.lengthOfMonth()))
        val thisMonth = inMonth(today)
        return if (!thisMonth.isBefore(today)) thisMonth else inMonth(today.plusMonths(1).withDayOfMonth(1))
    }

    /** Cards that owe something and are due within [days]. Cards in credit or without a due day are left out. */
    fun upcoming(positions: List<AccountPosition>, today: LocalDate, days: Long = 14): List<CardDue> =
        positions.filter { it.account.type == dev.kosh.financetracker.core.model.AccountType.CREDIT_CARD && !it.account.archived }
            .mapNotNull { p ->
                val day = p.account.dueDay ?: return@mapNotNull null
                val owed = p.amount ?: return@mapNotNull null
                if (owed.signum() <= 0) return@mapNotNull null
                val due = nextDue(day, today)
                if (java.time.temporal.ChronoUnit.DAYS.between(today, due) > days) null else CardDue(p.account, due, owed)
            }
            .sortedBy { it.date }
}
