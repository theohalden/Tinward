package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionDirection
import java.math.BigDecimal
import java.time.Instant

/**
 * Chooses the opening position for an account Tinward discovers from existing messages.
 *
 * If a message or statement reported a real balance, the opening amount is set so the tracked
 * balance equals it from the first recorded transaction. If none ever did, the true opening is
 * unknown: Tinward starts at zero from *now* rather than inventing a balance from partial history
 * (which can come out negative and look like fact). The user then sets the real figure.
 */
object AccountBootstrap {
    data class Opening(val amount: BigDecimal, val date: Instant, val fromReportedBalance: Boolean)

    fun openingFor(transactions: List<Transaction>, canonicalIds: Set<Long>, isCard: Boolean, now: Instant): Opening {
        val canonical = transactions.filter { it.id in canonicalIds }.sortedBy { it.timestamp }
        val reported = if (isCard) null else canonical.lastOrNull { it.balanceAfter != null }
        if (reported == null) return Opening(BigDecimal.ZERO, now, false)
        fun signed(t: Transaction) = when (t.direction) {
            TransactionDirection.CREDIT -> t.amount
            TransactionDirection.DEBIT -> t.amount.negate()
            TransactionDirection.UNKNOWN -> BigDecimal.ZERO
        }
        val upToReported = canonical.filter { !it.timestamp.isAfter(reported.timestamp) }.fold(BigDecimal.ZERO) { a, t -> a + signed(t) }
        val earliest = transactions.minOf { it.timestamp }.minusMillis(1)
        return Opening(reported.balanceAfter!! - upToReported, earliest, true)
    }
}
