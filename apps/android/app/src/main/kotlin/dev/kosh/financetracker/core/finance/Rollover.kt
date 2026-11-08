package dev.kosh.financetracker.core.finance

import java.math.BigDecimal
import java.time.YearMonth

/**
 * Budget rollover (Tinward Plus): money left unspent in a month is added to the next month's limit.
 * Overspending never reduces next month's allowance (the carry is floored at zero), and carrying
 * starts at the month the budget was created, never before, so setting a budget does not conjure
 * a pile of back-dated savings.
 */
object Rollover {
    /** Longest chain of months carried, so a very old budget stays quick to compute. */
    const val MAX_MONTHS = 24

    /**
     * The amount carried into [month]. [spentIn] gives what was spent in a past month against this
     * budget. Zero if rollover starts at or after [month].
     */
    fun carryIn(limit: BigDecimal, start: YearMonth, month: YearMonth, spentIn: (YearMonth) -> BigDecimal): BigDecimal {
        var cursor = maxOf(start, month.minusMonths(MAX_MONTHS.toLong()))
        var carry = BigDecimal.ZERO
        while (cursor.isBefore(month)) {
            carry = (limit + carry - spentIn(cursor)).max(BigDecimal.ZERO)
            cursor = cursor.plusMonths(1)
        }
        return carry
    }
}
