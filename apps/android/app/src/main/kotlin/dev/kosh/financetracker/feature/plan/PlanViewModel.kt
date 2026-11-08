package dev.kosh.financetracker.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.finance.AccountLedger
import dev.kosh.financetracker.core.finance.AvailableToSpend
import dev.kosh.financetracker.core.finance.AvailableToSpendCalculator
import dev.kosh.financetracker.core.finance.BudgetRule
import dev.kosh.financetracker.core.finance.BudgetRuleBreakdown
import dev.kosh.financetracker.core.finance.BudgetStatus
import dev.kosh.financetracker.core.finance.Budgets
import dev.kosh.financetracker.core.finance.FinanceCalculator
import dev.kosh.financetracker.core.finance.Occurrence
import dev.kosh.financetracker.core.finance.Recurrence
import dev.kosh.financetracker.core.finance.RecurringItem
import dev.kosh.financetracker.core.finance.SavingsGoal
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.data.budget.FixedBudgetStore
import dev.kosh.financetracker.data.repository.AccountRepository
import dev.kosh.financetracker.data.repository.BudgetLimit
import dev.kosh.financetracker.data.repository.PlanningRepository
import dev.kosh.financetracker.data.repository.TransactionRepository
import dev.kosh.financetracker.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class CategoryBudgetRow(val limit: BudgetLimit, val status: BudgetStatus)

data class PlanUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val availableMonths: List<YearMonth> = emptyList(),
    val monthsWithData: Set<YearMonth> = emptySet(),
    val currency: String = "INR",
    val income: BigDecimal? = null,
    val spent: BigDecimal = BigDecimal.ZERO,
    val rule: BudgetRuleBreakdown = BudgetRuleBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
    val overall: BudgetStatus? = null,
    val overallLimit: BudgetLimit? = null,
    val categoryBudgets: List<CategoryBudgetRow> = emptyList(),
    val recurring: List<RecurringItem> = emptyList(),
    val pending: List<Occurrence> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val available: AvailableToSpend? = null,
) {
    val canAddCategoryBudget: Boolean get() = categoryBudgets.size < Budgets.FREE_CATEGORY_LIMIT
    val activeGoal: SavingsGoal? get() = goals.firstOrNull { !it.archived }
}

private data class Base(val month: YearMonth, val months: List<YearMonth>, val withData: Set<YearMonth>, val currency: String, val income: BigDecimal?, val spent: BigDecimal,
                        val rule: BudgetRuleBreakdown, val breakdown: List<dev.kosh.financetracker.core.finance.CategorySlice>, val tx: List<dev.kosh.financetracker.core.model.Transaction>)

@HiltViewModel
class PlanViewModel @Inject constructor(
    repository: TransactionRepository,
    private val store: FixedBudgetStore,
    settings: SettingsStore,
    private val planning: PlanningRepository,
    accountRepository: AccountRepository,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val month = MutableStateFlow<YearMonth?>(null)
    private var currency = "INR"

    private val base = combine(repository.observeTransactions(), store.monthlyIncome, month, settings.baseCurrency) { allTx, income, explicit, base ->
        currency = base
        val tx = allTx.filter { it.currency == base }
        val m = explicit ?: YearMonth.now(zone)
        val withData = FinanceCalculator.monthsWithData(tx, zone)
        val breakdown = FinanceCalculator.categoryBreakdownForMonth(tx, m, zone)
        Base(
            m, (FinanceCalculator.recentMonths(YearMonth.now(zone)) + withData).distinct().sortedDescending(), withData.toSet(), base, income,
            FinanceCalculator.summaryForMonth(tx, m, zone).expense, BudgetRule.breakdown(breakdown, income ?: BigDecimal.ZERO), breakdown, allTx,
        )
    }

    private val planned = combine(planning.observeRecurring(), planning.observeBudgets(), planning.observeGoals(), accountRepository.observeAccounts()) { r, b, g, a -> listOf(r, b, g, a) }

    val state: StateFlow<PlanUiState> = combine(base, planned) { b, p ->
        @Suppress("UNCHECKED_CAST") val recurring = p[0] as List<RecurringItem>
        @Suppress("UNCHECKED_CAST") val budgets = p[1] as List<BudgetLimit>
        @Suppress("UNCHECKED_CAST") val goals = p[2] as List<SavingsGoal>
        @Suppress("UNCHECKED_CAST") val accounts = p[3] as List<Account>
        val today = LocalDate.now()
        val windowEnd = today.withDayOfMonth(today.lengthOfMonth())
        val pending = recurring.flatMap { Recurrence.pending(it, b.tx, today, today.plusDays(45), zone) }.sortedBy { it.date }
        val overallLimit = budgets.firstOrNull { it.category == null && it.currency == b.currency }
        PlanUiState(
            selectedMonth = b.month, availableMonths = b.months, monthsWithData = b.withData, currency = b.currency, income = b.income, spent = b.spent, rule = b.rule,
            overall = overallLimit?.let { Budgets.overall(it.amount, b.spent) }, overallLimit = overallLimit,
            categoryBudgets = budgets.filter { it.category != null && it.currency == b.currency }.map { CategoryBudgetRow(it, Budgets.category(it.amount, it.category!!, b.breakdown)) },
            recurring = recurring, pending = pending, goals = goals, accounts = accounts,
            available = AvailableToSpendCalculator.compute(AccountLedger.positions(accounts, b.tx), pending, goals, b.currency, windowEnd),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    val message = MutableStateFlow<String?>(null)

    fun selectMonth(m: YearMonth) { month.value = m }
    fun setIncome(amount: BigDecimal?) { viewModelScope.launch { store.setMonthlyIncome(amount) } }

    fun setOverallBudget(amount: BigDecimal?) { viewModelScope.launch { planning.setOverallBudget(amount, currency) } }
    fun setCategoryBudget(category: TransactionCategory, amount: BigDecimal) {
        viewModelScope.launch { message.value = planning.setCategoryBudget(category, amount, currency).exceptionOrNull()?.message }
    }
    fun removeBudget(id: Long) { viewModelScope.launch { planning.removeBudget(id) } }

    fun saveRecurring(item: RecurringItem) { viewModelScope.launch { planning.saveRecurring(item) } }
    fun deleteRecurring(id: Long) { viewModelScope.launch { planning.deleteRecurring(id) } }
    fun markPaid(o: Occurrence, record: Boolean) { viewModelScope.launch { planning.markPaid(o.item, o.date, record) } }

    fun saveGoal(goal: SavingsGoal) { viewModelScope.launch { message.value = planning.saveGoal(goal).exceptionOrNull()?.message } }
    fun contribute(id: Long, delta: BigDecimal) { viewModelScope.launch { planning.contribute(id, delta) } }
    fun archiveGoal(id: Long, archived: Boolean) { viewModelScope.launch { planning.setGoalArchived(id, archived) } }
    fun clearMessage() { message.value = null }
}
