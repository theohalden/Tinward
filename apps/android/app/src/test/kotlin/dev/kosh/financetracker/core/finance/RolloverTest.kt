package dev.kosh.financetracker.core.finance

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

class RolloverTest {
    private fun d(v: String) = BigDecimal(v)
    private fun m(y: Int, mo: Int) = YearMonth.of(y, mo)
    private fun spent(map: Map<YearMonth, String>) = { ym: YearMonth -> map[ym]?.let(::d) ?: BigDecimal.ZERO }

    @Test fun `nothing carries into the month the budget starts`() = assertEquals(BigDecimal.ZERO, Rollover.carryIn(d("1000"), m(2026, 11), m(2026, 11), spent(emptyMap())))

    @Test fun `unspent money carries to the next month`() =
        assertEquals(d("400"), Rollover.carryIn(d("1000"), m(2026, 10), m(2026, 11), spent(mapOf(m(2026, 10) to "600"))))

    @Test fun `carry accumulates across months`() {
        val s = spent(mapOf(m(2026, 9) to "800", m(2026, 10) to "500"))      // +200, then +500 on top
        assertEquals(d("700"), Rollover.carryIn(d("1000"), m(2026, 9), m(2026, 11), s))
    }

    @Test fun `overspending does not reduce next month`() {
        assertEquals(BigDecimal.ZERO, Rollover.carryIn(d("1000"), m(2026, 10), m(2026, 11), spent(mapOf(m(2026, 10) to "1500"))))
    }

    @Test fun `overspending uses up earlier carry but never goes negative`() {
        val s = spent(mapOf(m(2026, 9) to "800", m(2026, 10) to "1500"))     // carry 200, then 1000+200-1500 -> 0
        assertEquals(BigDecimal.ZERO, Rollover.carryIn(d("1000"), m(2026, 9), m(2026, 11), s))
        assertEquals(d("100"), Rollover.carryIn(d("1000"), m(2026, 9), m(2026, 11), spent(mapOf(m(2026, 9) to "800", m(2026, 10) to "1100"))))
    }

    @Test fun `a budget that starts in the future carries nothing`() = assertEquals(BigDecimal.ZERO, Rollover.carryIn(d("1000"), m(2027, 1), m(2026, 11), spent(emptyMap())))

    @Test fun `very old budgets are capped`() {
        val c = Rollover.carryIn(d("100"), m(2000, 1), m(2026, 11), spent(emptyMap()))
        assertEquals(d("2400"), c)    // 24 months of unspent 100, not 300+
    }
}
