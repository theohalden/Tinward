package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.TransactionCategory
import java.math.BigDecimal

/** 50/30/20: needs, wants and what is left as savings, against declared income. */
data class BudgetRuleBreakdown(
    val needs: BigDecimal,
    val wants: BigDecimal,
    val unclassified: BigDecimal,
    val income: BigDecimal,
) {
    val spent: BigDecimal get() = needs + wants + unclassified
    val savings: BigDecimal get() = income - spent
    fun fractionOfIncome(amount: BigDecimal): Float? =
        if (income.signum() <= 0) null else (amount.toFloat() / income.toFloat())
}

object BudgetRule {
    val NEEDS = setOf(
        TransactionCategory.GROCERIES, TransactionCategory.BILLS, TransactionCategory.RENT,
        TransactionCategory.FUEL, TransactionCategory.HEALTH, TransactionCategory.EDUCATION,
        TransactionCategory.TRAVEL,
    )
    val WANTS = setOf(
        TransactionCategory.FOOD, TransactionCategory.SHOPPING, TransactionCategory.ENTERTAINMENT,
        TransactionCategory.SUBSCRIPTIONS, TransactionCategory.PERSONAL_CARE,
        TransactionCategory.TECHNOLOGY, TransactionCategory.HOME, TransactionCategory.FEES,
    )

    fun breakdown(slices: List<CategorySlice>, income: BigDecimal): BudgetRuleBreakdown {
        var needs = BigDecimal.ZERO
        var wants = BigDecimal.ZERO
        var other = BigDecimal.ZERO
        for (s in slices) when (s.category) {
            in NEEDS -> needs += s.amount
            in WANTS -> wants += s.amount
            else -> other += s.amount
        }
        return BudgetRuleBreakdown(needs, wants, other, income)
    }
}
