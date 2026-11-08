package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** What one account is worth right now, labelled honestly: it is calculated from recorded history. */
data class AccountPosition(
    val account: Account,
    /** Asset: tracked balance. Credit card: outstanding amount owed. Debit card: the linked account's balance, or null. */
    val amount: BigDecimal?,
    val transactionCount: Int,
) {
    val available: BigDecimal? get() = if (account.type == AccountType.CREDIT_CARD && account.creditLimit != null && amount != null) account.creditLimit - amount else null
    val utilizationPercent: Int? get() = if (account.type == AccountType.CREDIT_CARD && account.creditLimit != null && account.creditLimit.signum() > 0 && amount != null)
        amount.multiply(BigDecimal(100)).divide(account.creditLimit, 0, java.math.RoundingMode.HALF_UP).toInt() else null
}

/**
 * The single source of truth for account balances. Pure functions, no I/O, so every accounting
 * rule in the product spec is covered by unit tests.
 *
 * Rule of thumb: money in is +, money out is −, for the account whose balance moved.
 *  - Asset balance = opening + net.
 *  - Credit-card outstanding = opening − net (a purchase is money "out" of the card and raises what is owed).
 *  - A debit card moves its linked bank account, never a balance of its own.
 *  - Transactions in another currency than the account are not mixed in.
 */
object AccountLedger {

    fun effectiveAccountId(tx: Transaction, byId: Map<Long, Account>): Long? {
        val account = tx.accountId?.let(byId::get) ?: return null
        return if (account.type == AccountType.DEBIT_CARD) account.linkedAccountId else account.id
    }

    private fun signed(tx: Transaction): BigDecimal = when (tx.direction) {
        TransactionDirection.CREDIT -> tx.amount
        TransactionDirection.DEBIT -> tx.amount.negate()
        TransactionDirection.UNKNOWN -> BigDecimal.ZERO
    }

    private fun counts(tx: Transaction, account: Account) =
        !tx.isUnmatchedOverlap && tx.currency == account.currency && !tx.timestamp.isBefore(account.openingDate)

    fun positions(accounts: List<Account>, transactions: List<Transaction>): List<AccountPosition> {
        val byId = accounts.associateBy { it.id }
        val grouped = transactions.groupBy { effectiveAccountId(it, byId) }
        return accounts.map { account ->
            when (account.type) {
                AccountType.DEBIT_CARD -> {
                    val linked = account.linkedAccountId?.let(byId::get)
                    val own = transactions.count { it.accountId == account.id }
                    AccountPosition(account, linked?.let { balanceOf(it, grouped[it.id].orEmpty()) }, own)
                }
                else -> {
                    val txs = grouped[account.id].orEmpty()
                    AccountPosition(account, balanceOf(account, txs), txs.size)
                }
            }
        }
    }

    fun balanceOf(account: Account, txs: List<Transaction>): BigDecimal {
        val net = txs.filter { counts(it, account) }.fold(BigDecimal.ZERO) { a, t -> a + signed(t) }
        return if (account.isLiability) account.openingBalance - net else account.openingBalance + net
    }

    /** Net worth per currency (assets minus card debt). Different currencies are never added together. */
    fun netByCurrency(positions: List<AccountPosition>): Map<String, BigDecimal> =
        positions.filter { !it.account.archived && it.account.type != AccountType.DEBIT_CARD && it.amount != null }
            .groupBy { it.account.currency }
            .mapValues { (_, list) -> list.fold(BigDecimal.ZERO) { a, p -> if (p.account.isLiability) a - p.amount!! else a + p.amount!! } }

    // ---- building ledger entries ----

    /**
     * Moves [amount] between two of the user's own accounts as two linked legs. It is neither
     * income nor spending. Different currencies are refused rather than silently converted.
     */
    fun transfer(from: Account, to: Account, amount: BigDecimal, at: Instant, note: String? = null): Result<List<Transaction>> {
        if (from.id == to.id) return Result.failure(IllegalArgumentException("Pick two different accounts."))
        if (amount.signum() <= 0) return Result.failure(IllegalArgumentException("Enter an amount above zero."))
        if (from.currency != to.currency) return Result.failure(IllegalArgumentException("These accounts use different currencies. Tinward does not convert between them."))
        val type = when {
            to.type == AccountType.CREDIT_CARD -> TransactionType.CREDIT_CARD_PAYMENT
            to.type == AccountType.CASH && from.type != AccountType.CASH -> TransactionType.CASH_WITHDRAWAL
            else -> TransactionType.TRANSFER
        }
        val group = UUID.randomUUID().toString()
        fun leg(account: Account, direction: TransactionDirection, other: Account) = entry(
            account = account, direction = direction, amount = amount, at = at, type = type,
            merchant = if (direction == TransactionDirection.DEBIT) "To ${other.name}" else "From ${other.name}",
            note = note, group = group,
        )
        return Result.success(listOf(leg(from, TransactionDirection.DEBIT, to), leg(to, TransactionDirection.CREDIT, from)))
    }

    /** Records the gap between tracked and real balance. Returns null when they already agree. */
    fun adjustment(account: Account, tracked: BigDecimal, actual: BigDecimal, at: Instant): Transaction? {
        val gap = actual - tracked
        if (gap.signum() == 0) return null
        // For a card, "actual" is the amount owed: owing more means money out of the card.
        val moneyIn = if (account.isLiability) gap.signum() < 0 else gap.signum() > 0
        return entry(
            account = account,
            direction = if (moneyIn) TransactionDirection.CREDIT else TransactionDirection.DEBIT,
            amount = gap.abs(), at = at, type = TransactionType.ADJUSTMENT,
            merchant = "Balance adjustment", note = "Tracked ${tracked.toPlainString()} → actual ${actual.toPlainString()}", group = null,
        )
    }

    private fun entry(
        account: Account, direction: TransactionDirection, amount: BigDecimal, at: Instant,
        type: TransactionType, merchant: String, note: String?, group: String?,
    ) = Transaction(
        timestamp = at, amount = amount, direction = direction, type = type,
        category = if (type == TransactionType.ADJUSTMENT) null else TransactionCategory.OTHER,
        merchant = merchant, rawMerchant = merchant,
        accountId = account.id, accountSuffix = account.last4, bank = account.institution,
        paymentMethod = PaymentMethod.UNKNOWN, source = TransactionSource.MANUAL,
        sourceMessageId = null, rawSourceText = null, confidence = 1.0, notes = note,
        createdAt = Instant.now(), currency = account.currency, transferGroupId = group,
    )
}
