package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PlanningLogicTest {
    private val zone = ZoneId.of("UTC")
    private fun d(v: String) = BigDecimal(v)
    private fun day(s: String) = LocalDate.parse(s)
    private fun bill(anchor: String, freq: Frequency = Frequency.MONTHLY, paid: String? = null, name: String = "Rent", amt: String = "15000", kind: RecurringKind = RecurringKind.BILL) =
        RecurringItem(1, name, d(amt), "INR", kind, freq, day(anchor), paidThrough = paid?.let(::day))

    private fun tx(date: String, amt: String, merchant: String, dir: TransactionDirection = TransactionDirection.DEBIT, cur: String = "INR") = Transaction(
        id = 1, timestamp = Instant.parse("${date}T10:00:00Z"), amount = d(amt), direction = dir, type = TransactionType.EXPENSE, category = null,
        merchant = merchant, rawMerchant = merchant, accountId = null, accountSuffix = null, bank = null, paymentMethod = PaymentMethod.UNKNOWN,
        source = TransactionSource.SMS, sourceMessageId = "s", rawSourceText = null, confidence = 1.0, notes = null, createdAt = Instant.EPOCH, currency = cur,
    )

    // ---- due dates ----
    @Test fun `monthly clamps to the end of short months and recovers`() {
        val b = bill("2026-01-31")
        assertEquals(listOf(day("2026-01-31"), day("2026-02-28"), day("2026-03-31"), day("2026-04-30")), (0..3).map { Recurrence.dueDate(b, it) })
        assertEquals(day("2028-02-29"), Recurrence.dueDate(b, 25))   // leap year
    }

    @Test fun `weekly and yearly`() {
        assertEquals(day("2026-11-22"), Recurrence.dueDate(bill("2026-11-08", Frequency.WEEKLY), 2))
        assertEquals(day("2027-02-28"), Recurrence.dueDate(bill("2024-02-29", Frequency.YEARLY), 3))
        assertEquals(day("2028-02-29"), Recurrence.dueDate(bill("2024-02-29", Frequency.YEARLY), 4))
    }

    @Test fun `dueDates in a range`() {
        assertEquals(listOf(day("2026-11-15"), day("2026-12-15")), Recurrence.dueDates(bill("2026-09-15"), day("2026-11-01"), day("2026-12-31")))
        assertTrue(Recurrence.dueDates(bill("2026-09-15"), day("2026-12-31"), day("2026-11-01")).isEmpty())
    }

    @Test fun `paid through moves the next unsettled date`() {
        assertEquals(day("2026-10-15"), Recurrence.nextUnsettled(bill("2026-09-15", paid = "2026-09-15")))
        assertEquals(day("2026-09-15"), Recurrence.nextUnsettled(bill("2026-09-15")))
    }

    // ---- pending, status, matching ----
    @Test fun `statuses are upcoming due today and overdue`() {
        val today = day("2026-11-15")
        val p = Recurrence.pending(bill("2026-10-15"), emptyList(), today, day("2026-12-31"), zone)
        assertEquals(listOf(OccurrenceStatus.OVERDUE, OccurrenceStatus.DUE_TODAY, OccurrenceStatus.UPCOMING), p.map { it.status })
    }

    @Test fun `a matching payment settles that occurrence automatically`() {
        val today = day("2026-11-10")
        val paid = tx("2026-11-14", "15000", "Rent to landlord")
        val p = Recurrence.pending(bill("2026-11-15"), listOf(paid), today, day("2026-12-31"), zone)
        assertEquals(listOf(day("2026-12-15")), p.map { it.date })
    }

    @Test fun `a payment that is too far in time or amount does not match`() {
        val today = day("2026-11-10")
        assertEquals(day("2026-11-15"), Recurrence.pending(bill("2026-11-15"), listOf(tx("2026-11-25", "15000", "Rent")), today, day("2026-11-30"), zone).single().date)
        assertEquals(day("2026-11-15"), Recurrence.pending(bill("2026-11-15"), listOf(tx("2026-11-14", "9000", "Rent")), today, day("2026-11-30"), zone).single().date)
    }

    @Test fun `an exact amount matches without the name, a different amount needs the name`() {
        val today = day("2026-11-10")
        assertTrue(Recurrence.pending(bill("2026-11-15"), listOf(tx("2026-11-15", "15000", "UPI-9988")), today, day("2026-11-30"), zone).isEmpty())
        assertTrue(Recurrence.pending(bill("2026-11-15"), listOf(tx("2026-11-15", "15200", "Monthly RENT")), today, day("2026-11-30"), zone).isEmpty())
        assertFalse(Recurrence.pending(bill("2026-11-15"), listOf(tx("2026-11-15", "15200", "Grocer")), today, day("2026-11-30"), zone).isEmpty())
    }

    @Test fun `wrong direction or currency never matches`() {
        val today = day("2026-11-10")
        assertFalse(Recurrence.pending(bill("2026-11-15"), listOf(tx("2026-11-15", "15000", "Rent", TransactionDirection.CREDIT)), today, day("2026-11-30"), zone).isEmpty())
        assertFalse(Recurrence.pending(bill("2026-11-15"), listOf(tx("2026-11-15", "15000", "Rent", cur = "USD")), today, day("2026-11-30"), zone).isEmpty())
    }

    @Test fun `inactive items have nothing pending`() = assertTrue(Recurrence.pending(bill("2026-11-15").copy(active = false), emptyList(), day("2026-11-10"), day("2026-12-31"), zone).isEmpty())

    @Test fun `income schedules match credits`() {
        val salary = bill("2026-11-01", name = "Salary", amt = "80000", kind = RecurringKind.INCOME)
        val credit = tx("2026-11-01", "80000", "ACME PAYROLL", TransactionDirection.CREDIT)
        assertEquals(listOf(day("2026-12-01")), Recurrence.pending(salary, listOf(credit), day("2026-11-10"), day("2026-12-31"), zone).map { it.date })
    }

    // ---- available to spend ----
    private val bank = Account(1, "HDFC", AccountType.BANK, openingBalance = d("30000"), openingDate = Instant.EPOCH)
    private val empty = Account(2, "New", AccountType.BANK, openingBalance = d("0"), openingDate = Instant.EPOCH)
    private val card = Account(3, "Visa", AccountType.CREDIT_CARD, openingBalance = d("4000"), openingDate = Instant.EPOCH, creditLimit = d("50000"))
    private val usd = Account(4, "Wise", AccountType.BANK, currency = "USD", openingBalance = d("999"), openingDate = Instant.EPOCH)

    @Test fun `available is balances minus card debt minus bills due minus goals`() {
        val positions = AccountLedger.positions(listOf(bank, card), emptyList())
        val rent = Recurrence.pending(bill("2026-11-20"), emptyList(), day("2026-11-08"), day("2026-11-30"), zone)
        val goal = SavingsGoal(1, "Trip", d("20000"), d("5000"))
        val a = AvailableToSpendCalculator.compute(positions, rent, listOf(goal), "INR", day("2026-11-30"))
        assertEquals(d("30000"), a.accountBalances); assertEquals(d("4000"), a.cardDebt); assertEquals(d("15000"), a.billsDue); assertEquals(d("5000"), a.reservedForGoals)
        assertEquals(d("6000"), a.available)
        assertEquals(1, a.billCount)
    }

    @Test fun `bills after the window and other currencies are left out`() {
        val positions = AccountLedger.positions(listOf(bank, usd), emptyList())
        val later = Recurrence.pending(bill("2026-12-20"), emptyList(), day("2026-11-08"), day("2026-12-31"), zone)
        val a = AvailableToSpendCalculator.compute(positions, later, emptyList(), "INR", day("2026-11-30"))
        assertEquals(d("30000"), a.available)   // USD 999 is not counted, December rent is outside the window
    }

    @Test fun `income schedules are not counted`() {
        val positions = AccountLedger.positions(listOf(bank), emptyList())
        val salary = Recurrence.pending(bill("2026-11-25", name = "Salary", amt = "80000", kind = RecurringKind.INCOME), emptyList(), day("2026-11-08"), day("2026-11-30"), zone)
        assertEquals(d("30000"), AvailableToSpendCalculator.compute(positions, salary, emptyList(), "INR", day("2026-11-30")).available)
    }

    @Test fun `accounts with no balance are flagged`() {
        val a = AvailableToSpendCalculator.compute(AccountLedger.positions(listOf(bank, empty), emptyList()), emptyList(), emptyList(), "INR", day("2026-11-30"))
        assertEquals(1, a.accountsWithoutBalance)
    }

    @Test fun `archived goals do not reserve money`() {
        val a = AvailableToSpendCalculator.compute(AccountLedger.positions(listOf(bank), emptyList()), emptyList(), listOf(SavingsGoal(1, "x", d("10"), d("9"), archived = true)), "INR", day("2026-11-30"))
        assertEquals(d("30000"), a.available)
    }

    // ---- budgets and goals ----
    @Test fun `budget status`() {
        assertEquals(0.5f, BudgetStatus(d("1000"), d("500")).fraction, 0.001f)
        assertTrue(BudgetStatus(d("1000"), d("850")).warning)
        assertFalse(BudgetStatus(d("1000"), d("850")).over)
        assertTrue(BudgetStatus(d("1000"), d("1001")).over)
        assertEquals(d("-1"), BudgetStatus(d("1000"), d("1001")).remaining)
        assertEquals(d("200"), Budgets.category(d("500"), TransactionCategory.FOOD, listOf(CategorySlice(TransactionCategory.FOOD, d("200"), 1f))).spent)
        assertEquals(BigDecimal.ZERO, Budgets.category(d("500"), TransactionCategory.FUEL, emptyList()).spent)
    }

    @Test fun `goal progress and monthly need`() {
        val g = SavingsGoal(1, "Trip", d("12000"), d("3000"), targetDate = day("2027-03-15"))
        assertEquals(0.25f, g.fraction, 0.001f)
        assertEquals(d("9000"), g.remaining)
        assertEquals(d("2250.00"), g.monthlyNeeded(day("2026-11-08")))   // 4 whole months left (cautious), rounded up
        assertNull(g.copy(targetDate = day("2026-01-01")).monthlyNeeded(day("2026-11-08")))
        assertNull(g.copy(saved = d("12000")).monthlyNeeded(day("2026-11-08")))
        assertEquals(1f, g.copy(saved = d("20000")).fraction, 0f)
    }
}
