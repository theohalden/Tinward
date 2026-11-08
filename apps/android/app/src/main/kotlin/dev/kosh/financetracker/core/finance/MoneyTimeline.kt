package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class EventKind { BILL, INCOME, CARD_PAYMENT, WHAT_IF }

/** Something expected to move money on a date. Negative amounts leave the accounts. */
data class TimelineEvent(val date: LocalDate, val label: String, val amount: BigDecimal, val kind: EventKind)

data class TimelinePoint(val date: LocalDate, val balance: BigDecimal)

/** A one-off the user is trying out ("what if I buy a laptop on 20 December?"). Never saved as a transaction. */
data class WhatIf(val date: LocalDate, val label: String, val amount: BigDecimal)

/**
 * A forecast of tracked account balances, built only from things the user has told Tinward about
 * (bills, income schedules, card due dates). It is not a prediction of everyday spending unless
 * [everydayPerDay] was switched on, and it says exactly what it did and did not include.
 */
data class MoneyTimeline(
    val asOf: LocalDate,
    val days: Int,
    val currency: String,
    val startBalance: BigDecimal,
    val includedAccounts: List<String>,
    val events: List<TimelineEvent>,
    val points: List<TimelinePoint>,
    val everydayPerDay: BigDecimal?,
    val assumptions: List<String>,
) {
    val lowest: TimelinePoint get() = points.minByOrNull { it.balance }!!
    val end: TimelinePoint get() = points.last()
    /** First day the expected balance is below zero, if any. */
    val firstShortfall: TimelinePoint? get() = points.firstOrNull { it.balance.signum() < 0 }
    /** Balance after each event, for the running list. */
    fun balanceAfter(event: TimelineEvent): BigDecimal = points.first { it.date == event.date }.balance
}

object TimelineBuilder {
    fun build(
        positions: List<AccountPosition>, pending: List<Occurrence>, cardDues: List<CardDue>, currency: String, today: LocalDate, days: Int,
        everydayPerDay: BigDecimal? = null, whatIfs: List<WhatIf> = emptyList(),
    ): MoneyTimeline {
        val end = today.plusDays(days.toLong())
        val assets = positions.filter { !it.account.archived && it.account.currency == currency && it.account.type != AccountType.CREDIT_CARD && it.account.type != AccountType.DEBIT_CARD }
        val start = assets.fold(BigDecimal.ZERO) { a, p -> a + (p.amount ?: BigDecimal.ZERO) }

        fun clampDate(d: LocalDate) = if (d.isBefore(today)) today else d   // overdue things are assumed to happen today
        val scheduled = pending.filter { it.item.currency == currency && !clampDate(it.date).isAfter(end) }.map {
            val bill = it.item.kind == RecurringKind.BILL
            TimelineEvent(clampDate(it.date), it.item.name, if (bill) it.item.amount.negate() else it.item.amount, if (bill) EventKind.BILL else EventKind.INCOME)
        }
        val billNames = pending.filter { it.item.kind == RecurringKind.BILL }.map { it.item.name.lowercase() }
        val cards = cardDues.filter { it.account.currency == currency && !clampDate(it.date).isAfter(end) }
            // A card already added as a bill (e.g. "Visa payment") must not be paid twice in the forecast.
            .filter { d -> billNames.none { it.contains(d.account.name.lowercase()) } }
            .map { TimelineEvent(clampDate(it.date), "${it.account.name} payment", it.owed.negate(), EventKind.CARD_PAYMENT) }
        val hypo = whatIfs.filter { !it.date.isAfter(end) && !it.date.isBefore(today) }.map { TimelineEvent(it.date, it.label, it.amount, EventKind.WHAT_IF) }
        val events = (scheduled + cards + hypo).sortedBy { it.date }

        val byDay = events.groupBy { it.date }
        var running = start
        val points = ArrayList<TimelinePoint>()
        for (i in 0..days) {
            val d = today.plusDays(i.toLong())
            if (i > 0 && everydayPerDay != null) running -= everydayPerDay
            byDay[d]?.forEach { running += it.amount }
            points += TimelinePoint(d, running.setScale(2, RoundingMode.HALF_UP))
        }

        val notes = ArrayList<String>()
        notes += "Starts from the balances Tinward tracks today (${assets.size} account${if (assets.size == 1) "" else "s"}), which are estimates from your records, not live bank figures."
        notes += "Includes only bills and income you added, and credit card payments assumed paid in full on the due day."
        notes += if (everydayPerDay != null) "Also subtracts ${everydayPerDay.setScale(2, RoundingMode.HALF_UP).toPlainString()} a day for everyday spending, the average of your recent spending that was not a bill."
        else "Does not include everyday spending (food, fuel, shopping) or any income you have not added as a schedule."
        notes += "Overdue items are assumed to happen today. Only $currency accounts and items are used; other currencies are never converted."
        if (whatIfs.isNotEmpty()) notes += "Includes ${whatIfs.size} what-if item${if (whatIfs.size == 1) "" else "s"} you added to try out. They are not recorded anywhere."
        return MoneyTimeline(today, days, currency, start.setScale(2, RoundingMode.HALF_UP), assets.map { it.account.name }, events, points, everydayPerDay, notes)
    }
}

object EverydaySpend {
    /**
     * Average daily spending over the last [windowDays] days, leaving out anything that matches a
     * bill (those are already forecast as bills). Uses the days actually covered by data (at least
     * 14) so a new user is not averaged down by months of nothing. Null when there is nothing to average.
     */
    fun perDay(
        transactions: List<Transaction>, bills: List<RecurringItem>, currency: String, today: LocalDate, windowDays: Int = 90,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BigDecimal? {
        val from = today.minusDays(windowDays.toLong())
        val own = transactions.filter { it.currency == currency && it.reconciliationNote == null }
        val inWindow = own.filter { val d = it.timestamp.atZone(zone).toLocalDate(); !d.isBefore(from) && d.isBefore(today) }
        val loose = inWindow.filter { t ->
            bills.none { b -> b.kind == RecurringKind.BILL && b.currency == currency && Recurrence.dueDates(b, from.minusDays(5), today.plusDays(5)).any { due -> Recurrence.findPayment(b, due, listOf(t), zone) != null } }
        }
        val total = loose.flatMap { FinanceCalculator.spendParts(it) }.fold(BigDecimal.ZERO) { a, p -> a + p.second }
        if (total.signum() <= 0) return null
        val firstDay = own.minOfOrNull { it.timestamp.atZone(zone).toLocalDate() } ?: return null
        val covered = ChronoUnit.DAYS.between(maxOf(firstDay, from), today).coerceAtLeast(14).coerceAtMost(windowDays.toLong())
        return total.divide(BigDecimal(covered), 2, RoundingMode.HALF_UP)
    }
}
