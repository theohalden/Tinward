package dev.kosh.financetracker.feature.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderCacheTest {
    private val today = LocalDate.of(2026, 11, 8)
    private fun e(dueOffset: Long, remind: Int) = ReminderCache.Entry(today.toEpochDay() + dueOffset, remind)

    @Test fun `reminder starts the chosen number of days before`() {
        assertTrue(ReminderCache.dueNow(listOf(e(3, 2)), today).isEmpty())          // due in 3, remind from 2 days before
        assertEquals(1, ReminderCache.dueNow(listOf(e(2, 2)), today).size)           // exactly on the first reminder day
        assertEquals(1, ReminderCache.dueNow(listOf(e(0, 2)), today).size)           // due today
    }

    @Test fun `still reminds for a few days after but not for long overdue bills`() {
        assertEquals(1, ReminderCache.dueNow(listOf(e(-3, 1)), today).size)
        assertTrue(ReminderCache.dueNow(listOf(e(-4, 1)), today).isEmpty())
    }

    @Test fun `off means never`() = assertTrue(ReminderCache.dueNow(listOf(e(0, 0)), today).isEmpty())

    @Test fun `counts several bills`() = assertEquals(2, ReminderCache.dueNow(listOf(e(1, 2), e(0, 1), e(20, 2)), today).size)
}
