package dev.kosh.financetracker.data.repository

import androidx.room.withTransaction
import dev.kosh.financetracker.core.finance.Budgets
import dev.kosh.financetracker.core.finance.Frequency
import dev.kosh.financetracker.core.finance.Recurrence
import dev.kosh.financetracker.core.finance.RecurringItem
import dev.kosh.financetracker.core.finance.RecurringKind
import dev.kosh.financetracker.core.finance.SavingsGoal
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.data.budget.FixedBudgetStore
import dev.kosh.financetracker.data.database.BudgetEntity
import dev.kosh.financetracker.data.database.FinanceDatabase
import dev.kosh.financetracker.data.database.PlanningDao
import dev.kosh.financetracker.data.database.TransactionDao
import dev.kosh.financetracker.data.database.toDomain
import dev.kosh.financetracker.data.database.toEntity
import dev.kosh.financetracker.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A budget the user has set. [category] null means the overall monthly budget. */
data class BudgetLimit(val id: Long, val category: TransactionCategory?, val amount: BigDecimal, val currency: String)

@Singleton
class PlanningRepository @Inject constructor(
    private val database: FinanceDatabase,
    private val dao: PlanningDao,
    private val transactions: TransactionDao,
    private val fixedStore: FixedBudgetStore,
    private val settings: SettingsStore,
) {
    // ---- bills and income ----
    fun observeRecurring(): Flow<List<RecurringItem>> = dao.observeRecurring().map { list -> list.map { it.toDomain() } }

    suspend fun saveRecurring(item: RecurringItem): Long =
        if (item.id == 0L) dao.insert(item.toEntity()) else { dao.update(item.toEntity()); item.id }

    suspend fun deleteRecurring(id: Long) = dao.deleteRecurring(id)

    /**
     * Confirms the occurrence due on [due] is settled (and any before it). With [recordTransaction] it also records the
     * payment (so balances and spending reflect it); without, it only moves the schedule on, for a
     * payment that is already in the ledger or happened outside Tinward.
     */
    suspend fun markPaid(item: RecurringItem, due: LocalDate, recordTransaction: Boolean, at: Instant = Instant.now()) = database.withTransaction {
        if (recordTransaction) {
            val bill = item.kind == RecurringKind.BILL
            transactions.insert(
                Transaction(
                    timestamp = at, amount = item.amount, direction = if (bill) TransactionDirection.DEBIT else TransactionDirection.CREDIT,
                    type = if (bill) TransactionType.EXPENSE else TransactionType.INCOME,
                    category = item.category ?: TransactionCategory.OTHER, merchant = item.name, rawMerchant = item.name,
                    accountId = item.accountId, accountSuffix = null, bank = null, paymentMethod = PaymentMethod.UNKNOWN,
                    source = TransactionSource.MANUAL, sourceMessageId = null, rawSourceText = null, confidence = 1.0,
                    notes = "Recorded from ${item.name} schedule", createdAt = at, currency = item.currency,
                ).toEntity(),
            )
        }
        dao.setPaidThrough(item.id, due.toEpochDay())
    }

    // ---- budgets ----
    fun observeBudgets(): Flow<List<BudgetLimit>> = dao.observeBudgets().map { l -> l.map { BudgetLimit(it.id, it.category, it.amount, it.currency) } }

    suspend fun setOverallBudget(amount: BigDecimal?, currency: String) = database.withTransaction {
        val existing = dao.budgets().firstOrNull { it.category == null }
        when {
            amount == null && existing != null -> dao.deleteBudget(existing.id)
            amount == null -> Unit
            existing == null -> dao.insertBudget(BudgetEntity(category = null, amount = amount, currency = currency))
            else -> dao.updateBudget(existing.copy(amount = amount, currency = currency))
        }
    }

    /** Sets or changes a category budget. A fourth distinct category is refused (free tier limit). */
    suspend fun setCategoryBudget(category: TransactionCategory, amount: BigDecimal, currency: String): Result<Unit> = database.withTransaction {
        val all = dao.budgets()
        val existing = all.firstOrNull { it.category == category }
        if (existing == null && all.count { it.category != null } >= Budgets.FREE_CATEGORY_LIMIT) {
            return@withTransaction Result.failure(IllegalStateException("The free plan covers ${Budgets.FREE_CATEGORY_LIMIT} category budgets. Remove one to add another."))
        }
        if (existing == null) dao.insertBudget(BudgetEntity(category = category, amount = amount, currency = currency))
        else dao.updateBudget(existing.copy(amount = amount, currency = currency))
        Result.success(Unit)
    }

    suspend fun removeBudget(id: Long) = dao.deleteBudget(id)

    // ---- goals ----
    fun observeGoals(): Flow<List<SavingsGoal>> = dao.observeGoals().map { l -> l.map { it.toDomain() } }

    /** Free tier keeps one active goal. */
    suspend fun saveGoal(goal: SavingsGoal): Result<Long> = database.withTransaction {
        if (goal.id == 0L) {
            if (dao.activeGoals().size >= FREE_GOAL_LIMIT) return@withTransaction Result.failure(IllegalStateException("The free plan has one savings goal. Finish or archive it to start another."))
            Result.success(dao.insertGoal(goal.toEntity()))
        } else { dao.updateGoal(goal.toEntity()); Result.success(goal.id) }
    }

    /** Adds to (or with a negative amount, takes back from) the amount set aside. Never below zero. */
    suspend fun contribute(goalId: Long, delta: BigDecimal) = database.withTransaction {
        val g = dao.goal(goalId) ?: return@withTransaction
        dao.updateGoal(g.copy(saved = (g.saved + delta).max(BigDecimal.ZERO)))
    }

    suspend fun setGoalArchived(id: Long, archived: Boolean) = database.withTransaction { dao.goal(id)?.let { dao.updateGoal(it.copy(archived = archived)) } }

    // ---- one-time carry-over of the old "fixed expenses" list ----
    /**
     * Turns each old fixed expense into a monthly bill so nothing the user entered is lost. The old
     * list had no due date, so each is placed on the last day of this month, which keeps it inside
     * "available to spend" until the user sets the real date.
     */
    suspend fun importFixedExpensesOnce() {
        if (settings.fixedExpensesImported.first()) return
        val items = fixedStore.expenses.first()
        val today = LocalDate.now()
        val dueDay = today.withDayOfMonth(today.lengthOfMonth())
        val currency = settings.baseCurrency.first()
        database.withTransaction {
            items.forEach { dao.insert(RecurringItem(name = it.label, amount = it.amount, currency = currency, frequency = Frequency.MONTHLY, anchor = dueDay).toEntity()) }
        }
        settings.markFixedExpensesImported()
    }

    companion object { const val FREE_GOAL_LIMIT = 1 }
}
