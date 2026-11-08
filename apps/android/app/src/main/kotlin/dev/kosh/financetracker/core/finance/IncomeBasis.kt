package dev.kosh.financetracker.core.finance

import java.math.BigDecimal

enum class IncomeSource { RECORDED, PLANNED, NONE }

data class IncomeBasis(val amount: BigDecimal, val source: IncomeSource, val receivedSoFar: BigDecimal)

object IncomeBasisRule {
    /**
     * What "left to spend" measures spending against. The income the user declared is the baseline,
     * because income that has only partly arrived early in the month would make a healthy month
     * look overspent. Actual income wins only when it exceeds the plan (a bonus). With no plan, what
     * has been recorded is all there is.
     */
    fun choose(recorded: BigDecimal, planned: BigDecimal?): IncomeBasis = when {
        planned != null && planned.signum() > 0 ->
            if (recorded > planned) IncomeBasis(recorded, IncomeSource.RECORDED, recorded) else IncomeBasis(planned, IncomeSource.PLANNED, recorded)
        recorded.signum() > 0 -> IncomeBasis(recorded, IncomeSource.RECORDED, recorded)
        else -> IncomeBasis(BigDecimal.ZERO, IncomeSource.NONE, recorded)
    }
}
