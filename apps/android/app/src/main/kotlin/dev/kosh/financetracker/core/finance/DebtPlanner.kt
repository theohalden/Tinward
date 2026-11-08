package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.ZoneId

/** A card with something owed, as the planner sees it. [apr] is the yearly percentage the user entered. */
data class DebtCard(val id: Long, val name: String, val balance: BigDecimal, val aprPercent: BigDecimal, val limit: BigDecimal?)

data class PayoffResult(
    /** Months until everything is paid, or null if the payments never get there. */
    val months: Int?,
    val totalInterest: BigDecimal,
    val totalPaid: BigDecimal,
)

enum class Strategy(val label: String) { AVALANCHE("Highest interest first"), SNOWBALL("Smallest balance first"), MINIMUMS("Minimums only") }

data class StrategyOutcome(val strategy: Strategy, val result: PayoffResult)

/**
 * Credit card repayment maths. Everything is a simple monthly model: interest is charged once a
 * month on the balance at APR/12, then the payment is applied; no new spending is assumed. It
 * is a planning aid, not a statement: real issuers differ in how and when they charge interest.
 */
object DebtPlanner {
    const val MAX_MONTHS = 600
    private val HUNDRED = BigDecimal(100)
    private val TWELVE = BigDecimal(12)

    private fun monthlyRate(aprPercent: BigDecimal) = aprPercent.divide(HUNDRED, 10, RoundingMode.HALF_UP).divide(TWELVE, 10, RoundingMode.HALF_UP)

    /** One card paid with a fixed monthly [payment]. */
    fun payoff(balance: BigDecimal, aprPercent: BigDecimal, payment: BigDecimal): PayoffResult {
        if (balance.signum() <= 0) return PayoffResult(0, BigDecimal.ZERO, BigDecimal.ZERO)
        val rate = monthlyRate(aprPercent)
        var left = balance; var interest = BigDecimal.ZERO; var paid = BigDecimal.ZERO; var month = 0
        while (left.signum() > 0 && month < MAX_MONTHS) {
            month++
            val charge = left.multiply(rate).setScale(2, RoundingMode.HALF_UP)
            left += charge; interest += charge
            if (payment <= charge && left > payment) return PayoffResult(null, interest, paid)   // the payment does not even cover the interest
            val pay = payment.min(left)
            left -= pay; paid += pay
        }
        return if (left.signum() > 0) PayoffResult(null, interest, paid) else PayoffResult(month, interest, paid)
    }

    /** The fixed monthly payment that clears [balance] in [months] months. */
    fun paymentFor(balance: BigDecimal, aprPercent: BigDecimal, months: Int): BigDecimal {
        if (months <= 0 || balance.signum() <= 0) return BigDecimal.ZERO
        val r = monthlyRate(aprPercent)
        if (r.signum() == 0) return balance.divide(BigDecimal(months), 2, RoundingMode.UP)
        val factor = BigDecimal.ONE.subtract(BigDecimal.ONE.add(r).pow(-months, java.math.MathContext(12)).let { it })
        return balance.multiply(r).divide(factor, 2, RoundingMode.UP)
    }

    /** A common minimum: this month's interest plus 1% of the balance. */
    fun minimumPayment(card: DebtCard): BigDecimal =
        card.balance.multiply(monthlyRate(card.aprPercent)).add(card.balance.multiply(BigDecimal("0.01"))).setScale(2, RoundingMode.UP).min(card.balance + card.balance.multiply(monthlyRate(card.aprPercent)))

    /**
     * Several cards, one total monthly budget. Every card gets its minimum; whatever is left goes to
     * the card chosen by [strategy]. With [Strategy.MINIMUMS] nothing extra is paid.
     */
    fun simulate(cards: List<DebtCard>, monthlyBudget: BigDecimal, strategy: Strategy): PayoffResult {
        if (cards.all { it.balance.signum() <= 0 }) return PayoffResult(0, BigDecimal.ZERO, BigDecimal.ZERO)
        val balances = cards.associate { it.id to it.balance }.toMutableMap()
        var interest = BigDecimal.ZERO; var paid = BigDecimal.ZERO; var month = 0
        while (balances.values.any { it.signum() > 0 } && month < MAX_MONTHS) {
            month++
            for (c in cards) {
                val b = balances[c.id]!!; if (b.signum() <= 0) continue
                val charge = b.multiply(monthlyRate(c.aprPercent)).setScale(2, RoundingMode.HALF_UP)
                balances[c.id] = b + charge; interest += charge
            }
            var pool = if (strategy == Strategy.MINIMUMS) BigDecimal.ZERO else monthlyBudget
            // minimums first
            for (c in cards) {
                val b = balances[c.id]!!; if (b.signum() <= 0) continue
                val min = minimumPayment(c.copy(balance = b)).min(b)
                val pay = if (strategy == Strategy.MINIMUMS) min else min.min(pool).also { pool -= it }
                balances[c.id] = b - pay; paid += pay
            }
            val order = when (strategy) {
                Strategy.AVALANCHE -> cards.sortedByDescending { it.aprPercent }
                Strategy.SNOWBALL -> cards.sortedBy { balances[it.id]!! }
                Strategy.MINIMUMS -> emptyList()
            }
            for (c in order) {
                if (pool.signum() <= 0) break
                val b = balances[c.id]!!; if (b.signum() <= 0) continue
                val pay = pool.min(b); balances[c.id] = b - pay; pool -= pay; paid += pay
            }
            // The minimum model can stall; give up rather than loop on a debt that never falls.
            if (month > 24 && balances.values.fold(BigDecimal.ZERO, BigDecimal::add) >= cards.fold(BigDecimal.ZERO) { a, c -> a + c.balance } && strategy == Strategy.MINIMUMS) break
        }
        val done = balances.values.none { it.signum() > 0 }
        return PayoffResult(if (done) month else null, interest, paid)
    }

    fun compare(cards: List<DebtCard>, monthlyBudget: BigDecimal): List<StrategyOutcome> =
        listOf(Strategy.AVALANCHE, Strategy.SNOWBALL, Strategy.MINIMUMS).map { StrategyOutcome(it, simulate(cards, monthlyBudget, it)) }
}

/** How much of a card's limit was in use at the end of each month. */
/** [tracked] is false for months before Tinward started tracking the card, when its balance is simply not known. */
data class UtilizationPoint(val month: YearMonth, val owed: BigDecimal, val percent: Int?, val tracked: Boolean = true)

object Utilization {
    fun history(account: Account, transactions: List<Transaction>, end: YearMonth, count: Int = 6, zone: ZoneId = ZoneId.systemDefault()): List<UtilizationPoint> {
        require(account.type == AccountType.CREDIT_CARD)
        return (count - 1 downTo 0).map { back ->
            val m = end.minusMonths(back.toLong())
            val cutoff = m.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant()
            if (!cutoff.isAfter(account.openingDate)) return@map UtilizationPoint(m, BigDecimal.ZERO, null, tracked = false)
            val owed = AccountLedger.balanceOf(account, transactions.filter { it.accountId == account.id && it.timestamp.isBefore(cutoff) })
            val pct = account.creditLimit?.takeIf { it.signum() > 0 }?.let { owed.max(BigDecimal.ZERO).multiply(BigDecimal(100)).divide(it, 0, RoundingMode.HALF_UP).toInt() }
            UtilizationPoint(m, owed, pct)
        }
    }
}
