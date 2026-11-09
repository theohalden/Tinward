package dev.kosh.financetracker.core.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SpendPaceTest {
    private fun d(v: String) = BigDecimal(v)
    private val nov = YearMonth.of(2026, 11)
    private val days = listOf(DaySpend(LocalDate.of(2026, 11, 2), d("100")), DaySpend(LocalDate.of(2026, 11, 5), d("50")), DaySpend(LocalDate.of(2026, 11, 5), d("25")))

    @Test fun `quiet days are filled and totals run up to today`() {
        val c = SpendPace.cumulative(days, nov, LocalDate.of(2026, 11, 6))
        assertEquals(listOf("0", "100", "100", "100", "175", "175"), c.map { it.toPlainString() })
    }

    @Test fun `a past month covers every day and a future month nothing`() {
        assertEquals(30, SpendPace.cumulative(days, YearMonth.of(2026, 9), LocalDate.of(2026, 11, 6)).size)
        assertEquals(0, SpendPace.cumulative(days, YearMonth.of(2026, 12), LocalDate.of(2026, 11, 6)).size)
    }

    @Test fun `days from other months are ignored`() =
        assertEquals("0", SpendPace.cumulative(days, YearMonth.of(2026, 10), LocalDate.of(2026, 11, 6)).last().toPlainString())

    @Test fun `at holds the last value past the end and is null when empty`() {
        val c = SpendPace.cumulative(days, nov, LocalDate.of(2026, 11, 6))
        assertEquals(d("175"), SpendPace.at(c, 30)); assertEquals(d("100"), SpendPace.at(c, 3)); assertNull(SpendPace.at(emptyList(), 3))
    }

    @Test fun `percent versus earlier rounds and refuses a zero base`() {
        assertEquals(50, SpendPace.percentVs(d("150"), d("100"))); assertEquals(-25, SpendPace.percentVs(d("75"), d("100")))
        assertNull(SpendPace.percentVs(d("10"), d("0"))); assertNull(SpendPace.percentVs(d("10"), null))
    }
}
