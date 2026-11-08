package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class Frequency(val label: String) { WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly") }
enum class RecurringKind { BILL, INCOME }

/**
 * A schedule for something that repeats. It is an expectation only: it never writes a transaction
 * by itself. [paidThrough] is the most recent due date the user (or a matching real transaction)
 * has settled.
 */
data class RecurringItem(
    val id: Long = 0,
    val name: String,
    val amount: BigDecimal,
    val currency: String = "INR",
    val kind: RecurringKind = RecurringKind.BILL,
    val frequency: Frequency = Frequency.MONTHLY,
    /** The first due date. Later dates follow its day of month / weekday. */
    val anchor: LocalDate,
    val accountId: Long? = null,
    val category: TransactionCategory? = null,
    /** Days before the due date the reminder starts. 0 turns reminders off for this item. */
    val remindDaysBefore: Int = 2,
    val active: Boolean = true,
    val paidThrough: LocalDate? = null,
)

enum class OccurrenceStatus { UPCOMING, DUE_TODAY, OVERDUE }

data class Occurrence(val item: RecurringItem, val date: LocalDate, val status: OccurrenceStatus, val matchedAutomatically: Boolean = false)

object Recurrence {
    /** The n-th due date (0 = anchor). Month-end days clamp (the 31st becomes the 30th, 28th or 29th). */
    fun dueDate(item: RecurringItem, n: Int): LocalDate = when (item.frequency) {
        Frequency.WEEKLY -> item.anchor.plusWeeks(n.toLong())
        Frequency.MONTHLY -> clamp(item.anchor, item.anchor.plusMonths(n.toLong()))
        Frequency.YEARLY -> clamp(item.anchor, item.anchor.plusYears(n.toLong()))
    }

    private fun clamp(anchor: LocalDate, shifted: LocalDate): LocalDate =
        shifted.withDayOfMonth(minOf(anchor.dayOfMonth, shifted.lengthOfMonth()))

    /** All due dates from [from] to [to] inclusive. */
    fun dueDates(item: RecurringItem, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        val out = ArrayList<LocalDate>()
        var n = 0
        while (n < 5000) {
            val d = dueDate(item, n)
            if (d.isAfter(to)) break
            if (!d.isBefore(from)) out += d
            n++
        }
        return out
    }

    /** First occurrence not yet settled. Anything on or before [RecurringItem.paidThrough] counts as settled. */
    fun nextUnsettled(item: RecurringItem): LocalDate {
        val after = item.paidThrough ?: return item.anchor
        var n = 0
        while (n < 5000) { val d = dueDate(item, n); if (d.isAfter(after)) return d; n++ }
        return dueDate(item, n)
    }

    /**
     * Occurrences still to deal with, from the first unsettled one up to [until]. A real transaction
     * near a due date that looks like the payment (same direction and currency, amount within 5%,
     * and the name appears in the description or the amount is exact) settles it automatically, so
     * a bill paid by SMS-detected payment never shows as due.
     */
    fun pending(
        item: RecurringItem, transactions: List<Transaction>, today: LocalDate, until: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Occurrence> {
        if (!item.active) return emptyList()
        val out = ArrayList<Occurrence>()
        var date = nextUnsettled(item)
        var guard = 0
        while (!date.isAfter(until) && guard++ < 2000) {
            val matched = findPayment(item, date, transactions, zone) != null
            if (!matched) {
                val status = when { date.isBefore(today) -> OccurrenceStatus.OVERDUE; date == today -> OccurrenceStatus.DUE_TODAY; else -> OccurrenceStatus.UPCOMING }
                out += Occurrence(item, date, status)
            }
            date = dueDate(item, indexAfter(item, date))
        }
        return out
    }

    private fun indexAfter(item: RecurringItem, date: LocalDate): Int {
        var n = 0
        while (n < 5000) { if (dueDate(item, n) == date) return n + 1; n++ }
        return n
    }

    fun findPayment(item: RecurringItem, due: LocalDate, transactions: List<Transaction>, zone: ZoneId = ZoneId.systemDefault(), windowDays: Long = 5): Transaction? {
        val wanted = if (item.kind == RecurringKind.BILL) TransactionDirection.DEBIT else TransactionDirection.CREDIT
        val low = item.amount.multiply(BigDecimal("0.95")); val high = item.amount.multiply(BigDecimal("1.05"))
        return transactions.firstOrNull { t ->
            if (t.direction != wanted || t.currency != item.currency || t.isUnmatchedOverlap) return@firstOrNull false
            val day = t.timestamp.atZone(zone).toLocalDate()
            if (ChronoUnit.DAYS.between(due, day) !in -windowDays..windowDays) return@firstOrNull false
            if (t.amount < low || t.amount > high) return@firstOrNull false
            val named = t.merchant?.lowercase()?.contains(item.name.trim().lowercase()) == true
            named || t.amount.compareTo(item.amount) == 0
        }
    }

    /** Dates on which a reminder should fire for [item]: from (due − remindDaysBefore) through the due date, while unsettled. */
    fun reminderActive(occurrence: Occurrence, today: LocalDate): Boolean {
        val n = occurrence.item.remindDaysBefore
        if (n <= 0) return false
        return !today.isBefore(occurrence.date.minusDays(n.toLong())) && occurrence.status != OccurrenceStatus.UPCOMING ||
            (occurrence.status == OccurrenceStatus.UPCOMING && !today.isBefore(occurrence.date.minusDays(n.toLong())))
    }

    @Suppress("unused") private val weekdays = DayOfWeek.values()
}
