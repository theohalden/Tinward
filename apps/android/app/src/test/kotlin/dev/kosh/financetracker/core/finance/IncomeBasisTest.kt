package dev.kosh.financetracker.core.finance

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class IncomeBasisTest {
    private fun d(v: String) = BigDecimal(v)

    @Test fun `plan wins while recorded income is still arriving`() {
        val b = IncomeBasisRule.choose(d("3000"), d("50000"))
        assertEquals(IncomeSource.PLANNED, b.source); assertEquals(d("50000"), b.amount); assertEquals(d("3000"), b.receivedSoFar)
    }

    @Test fun `actual income wins when it exceeds the plan`() {
        val b = IncomeBasisRule.choose(d("60000"), d("50000"))
        assertEquals(IncomeSource.RECORDED, b.source); assertEquals(d("60000"), b.amount)
    }

    @Test fun `no plan uses what was recorded`() = assertEquals(IncomeSource.RECORDED, IncomeBasisRule.choose(d("3000"), null).source)

    @Test fun `neither means none`() {
        assertEquals(IncomeSource.NONE, IncomeBasisRule.choose(BigDecimal.ZERO, null).source)
        assertEquals(IncomeSource.NONE, IncomeBasisRule.choose(BigDecimal.ZERO, BigDecimal.ZERO).source)
    }

    @Test fun `a plan with nothing recorded yet is the plan`() = assertEquals(d("50000"), IncomeBasisRule.choose(BigDecimal.ZERO, d("50000")).amount)
}
