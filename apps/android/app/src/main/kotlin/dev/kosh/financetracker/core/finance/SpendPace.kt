package dev.kosh.financetracker.core.finance

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Running total of spending through a month, one value per calendar day. [FinanceCalculator.dailySpendForMonth]
 * only lists days that had spending, so this fills the quiet days in. Comparing "so far this month" with
 * "the same point last month" is the honest way to show whether spending is on a faster or slower pace.
 */
object SpendPace {
    /** Cumulative spend for day 1..N where N is today (current month), the whole month (past) or nothing (future). */
    fun cumulative(days: List<DaySpend>, month: YearMonth, today: LocalDate): List<BigDecimal> {
        val last = when {
            month.isAfter(YearMonth.from(today)) -> 0
            month == YearMonth.from(today) -> today.dayOfMonth
            else -> month.lengthOfMonth()
        }
        val byDay = days.filter { YearMonth.from(it.date) == month }.groupBy { it.date.dayOfMonth }.mapValues { (_, v) -> v.fold(BigDecimal.ZERO) { a, d -> a + d.amount } }
        var running = BigDecimal.ZERO
        return (1..last).map { d -> running += byDay[d] ?: BigDecimal.ZERO; running }
    }

    /** Value at a 1-based [day], holding the last known total if the month is shorter (or not yet that far along). */
    fun at(cumulative: List<BigDecimal>, day: Int): BigDecimal? =
        if (cumulative.isEmpty()) null else cumulative[(day - 1).coerceIn(0, cumulative.lastIndex)]

    /** Percent difference of [now] against [before]; null when there is nothing earlier to compare with. */
    fun percentVs(now: BigDecimal, before: BigDecimal?): Int? =
        before?.takeIf { it.signum() > 0 }?.let { now.subtract(it).multiply(BigDecimal(100)).divide(it, 0, java.math.RoundingMode.HALF_UP).toInt() }
}
