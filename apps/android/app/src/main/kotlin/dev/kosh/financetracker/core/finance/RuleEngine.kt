package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionType
import java.math.BigDecimal

enum class RuleDirection(val label: String) { ANY("Any"), SPENT("Money out"), RECEIVED("Money in") }

/**
 * A rule that tidies up how Tinward labels transactions (Plus). It only ever changes the category
 * and the user's own label. It never edits an amount, a date, an account or any imported evidence,
 * and it never moves money: Tinward records, it does not act.
 */
data class AutoRule(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val merchantContains: String? = null,
    val amountMin: BigDecimal? = null,
    val amountMax: BigDecimal? = null,
    val direction: RuleDirection = RuleDirection.ANY,
    val accountId: Long? = null,
    val setCategory: TransactionCategory? = null,
    val setLabel: String? = null,
    /** Off (the default): only fill in what is still blank (no category, or Other). On: replace a category or label that is already there. */
    val overwrite: Boolean = false,
) {
    /** A rule with no condition would match everything; it is not allowed. */
    val hasCondition: Boolean get() = !merchantContains.isNullOrBlank() || amountMin != null || amountMax != null || direction != RuleDirection.ANY || accountId != null
    val hasAction: Boolean get() = setCategory != null || !setLabel.isNullOrBlank()
}

data class RuleChange(val transactionId: Long, val ruleId: Long, val category: TransactionCategory?, val label: String?)

object RuleEngine {
    /** Only ordinary spending and income entries are relabelled; transfers, card payments and adjustments are left alone. */
    private val relabellable = setOf(TransactionType.EXPENSE, TransactionType.CREDIT_CARD_PURCHASE, TransactionType.INCOME, TransactionType.REFUND)

    fun matches(rule: AutoRule, tx: Transaction): Boolean {
        if (!rule.hasCondition || tx.type !in relabellable) return false
        rule.merchantContains?.takeIf { it.isNotBlank() }?.let { needle ->
            val n = needle.trim().lowercase()
            if (tx.merchant?.lowercase()?.contains(n) != true && tx.rawMerchant?.lowercase()?.contains(n) != true) return false
        }
        rule.amountMin?.let { if (tx.amount < it) return false }
        rule.amountMax?.let { if (tx.amount > it) return false }
        when (rule.direction) {
            RuleDirection.SPENT -> if (tx.direction != TransactionDirection.DEBIT) return false
            RuleDirection.RECEIVED -> if (tx.direction != TransactionDirection.CREDIT) return false
            RuleDirection.ANY -> Unit
        }
        rule.accountId?.let { if (tx.accountId != it) return false }
        return true
    }

    /**
     * The change [rules] would make to [tx], or null. Rules are in priority order and the first one
     * that matches decides, even if it ends up changing nothing, so a higher rule always beats a lower one.
     */
    fun changeFor(rules: List<AutoRule>, tx: Transaction): RuleChange? {
        val rule = rules.firstOrNull { it.enabled && it.hasAction && matches(it, tx) } ?: return null
        // "Other" is the categoriser's fallback, so it counts as not yet decided.
        val blankCategory = tx.category == null || tx.category == TransactionCategory.UNCATEGORIZED || tx.category == TransactionCategory.OTHER
        val category = rule.setCategory?.takeIf { (rule.overwrite || blankCategory) && it != tx.category }
        val label = rule.setLabel?.trim()?.takeIf { it.isNotEmpty() && (rule.overwrite || tx.customLabel.isNullOrBlank()) && it != tx.customLabel }
        return if (category == null && label == null) null else RuleChange(tx.id, rule.id, category, label)
    }

    fun changes(rules: List<AutoRule>, transactions: List<Transaction>): List<RuleChange> = transactions.mapNotNull { changeFor(rules, it) }

    /** How many transactions a rule matches right now, ignoring priority (for the live preview while editing). */
    fun matchCount(rule: AutoRule, transactions: List<Transaction>): Int = transactions.count { matches(rule, it) }
}
