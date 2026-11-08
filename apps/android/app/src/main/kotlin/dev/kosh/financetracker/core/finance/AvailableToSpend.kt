package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.TransactionCategory
import java.math.BigDecimal
import java.time.LocalDate

/** A saved amount toward a target. Free tier: one active goal. */
data class SavingsGoal(
    val id: Long = 0,
    val name: String,
    val target: BigDecimal,
    val saved: BigDecimal = BigDecimal.ZERO,
    val currency: String = "INR",
    val targetDate: LocalDate? = null,
    val archived: Boolean = false,
) {
    val fraction: Float get() = if (target.signum() <= 0) 0f else saved.divide(target, 4, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
    val remaining: BigDecimal get() = (target - saved).max(BigDecimal.ZERO)
    /** What to set aside each month to hit [targetDate], or null when there is no date or it has passed. */
    fun monthlyNeeded(today: LocalDate): BigDecimal? {
        val date = targetDate ?: return null
        if (!date.isAfter(today) || remaining.signum() == 0) return null
        val months = java.time.temporal.ChronoUnit.MONTHS.between(today.withDayOfMonth(1), date.withDayOfMonth(1)).coerceAtLeast(1)
        return remaining.divide(BigDecimal(months), 2, java.math.RoundingMode.UP)
    }
}

/** What the estimate is made of, so the screen can show the working instead of just a number. */
data class AvailableToSpend(
    val currency: String,
    val windowEnd: LocalDate,
    val accountBalances: BigDecimal,
    val cardDebt: BigDecimal,
    val billsDue: BigDecimal,
    val billCount: Int,
    val reservedForGoals: BigDecimal,
    val accountsWithoutBalance: Int,
) {
    val available: BigDecimal get() = accountBalances - cardDebt - billsDue - reservedForGoals
}

object AvailableToSpendCalculator {
    /**
     * Tracked account balances, minus card debt, minus unpaid bills due by [windowEnd], minus money
     * set aside for goals. Income that has not arrived yet is deliberately not counted, so the
     * figure leans cautious. Only [currency] is used; other currencies are never converted.
     */
    fun compute(
        positions: List<AccountPosition>, pendingBills: List<Occurrence>, goals: List<SavingsGoal>,
        currency: String, windowEnd: LocalDate,
    ): AvailableToSpend {
        val active = positions.filter { !it.account.archived && it.account.currency == currency }
        val assets = active.filter { it.account.type != AccountType.CREDIT_CARD && it.account.type != AccountType.DEBIT_CARD }
        val balances = assets.fold(BigDecimal.ZERO) { a, p -> a + (p.amount ?: BigDecimal.ZERO) }
        val debt = active.filter { it.account.type == AccountType.CREDIT_CARD }.fold(BigDecimal.ZERO) { a, p -> a + (p.amount ?: BigDecimal.ZERO) }
        val bills = pendingBills.filter { it.item.kind == RecurringKind.BILL && it.item.currency == currency && !it.date.isAfter(windowEnd) }
        val reserved = goals.filter { !it.archived && it.currency == currency }.fold(BigDecimal.ZERO) { a, g -> a + g.saved }
        return AvailableToSpend(
            currency = currency, windowEnd = windowEnd, accountBalances = balances, cardDebt = debt,
            billsDue = bills.fold(BigDecimal.ZERO) { a, o -> a + o.item.amount }, billCount = bills.size,
            reservedForGoals = reserved,
            accountsWithoutBalance = assets.count { it.amount == null || it.amount.signum() == 0 },
        )
    }
}

/** Spending against a limit, for the overall monthly budget or one category. */
data class BudgetStatus(val limit: BigDecimal, val spent: BigDecimal) {
    val remaining: BigDecimal get() = limit - spent
    val over: Boolean get() = spent > limit
    val fraction: Float get() = if (limit.signum() <= 0) 0f else spent.divide(limit, 4, java.math.RoundingMode.HALF_UP).toFloat().coerceAtLeast(0f)
    /** Over 100% reads as over budget; 80% and up is a warning. */
    val warning: Boolean get() = !over && fraction >= 0.8f
}

object Budgets {
    /** Free tier allows one overall budget plus this many category budgets. */
    const val FREE_CATEGORY_LIMIT = 3

    fun overall(limit: BigDecimal, monthlySpent: BigDecimal) = BudgetStatus(limit, monthlySpent.max(BigDecimal.ZERO))

    fun category(limit: BigDecimal, category: TransactionCategory, breakdown: List<CategorySlice>) =
        BudgetStatus(limit, breakdown.firstOrNull { it.category == category }?.amount ?: BigDecimal.ZERO)
}
