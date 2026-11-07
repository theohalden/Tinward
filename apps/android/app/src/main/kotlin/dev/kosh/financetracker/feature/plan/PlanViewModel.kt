package dev.kosh.financetracker.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.finance.BudgetRule
import dev.kosh.financetracker.core.finance.BudgetRuleBreakdown
import dev.kosh.financetracker.core.finance.FinanceCalculator
import dev.kosh.financetracker.data.budget.FixedBudgetStore
import dev.kosh.financetracker.data.budget.FixedExpenseItem
import dev.kosh.financetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class PlanUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val availableMonths: List<YearMonth> = emptyList(),
    val monthsWithData: Set<YearMonth> = emptySet(),
    val income: BigDecimal? = null,
    val fixed: List<FixedExpenseItem> = emptyList(),
    val spent: BigDecimal = BigDecimal.ZERO,
    val rule: BudgetRuleBreakdown = BudgetRuleBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
) {
    val fixedTotal: BigDecimal get() = fixed.fold(BigDecimal.ZERO) { a, b -> a + b.amount }
    val afterFixed: BigDecimal? get() = income?.let { it - fixedTotal }
}

@HiltViewModel
class PlanViewModel @Inject constructor(
    repository: TransactionRepository,
    private val store: FixedBudgetStore,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val month = MutableStateFlow<YearMonth?>(null)

    val state: StateFlow<PlanUiState> = combine(
        repository.observeTransactions(), store.monthlyIncome, store.expenses, month,
    ) { tx, income, fixed, explicit ->
        val m = explicit ?: YearMonth.now(zone)
        val withData = FinanceCalculator.monthsWithData(tx, zone)
        PlanUiState(
            selectedMonth = m,
            availableMonths = (FinanceCalculator.recentMonths(YearMonth.now(zone)) + withData).distinct().sortedDescending(),
            monthsWithData = withData.toSet(),
            income = income,
            fixed = fixed,
            spent = FinanceCalculator.summaryForMonth(tx, m, zone).expense,
            rule = BudgetRule.breakdown(FinanceCalculator.categoryBreakdownForMonth(tx, m, zone), income ?: BigDecimal.ZERO),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    fun selectMonth(m: YearMonth) { month.value = m }
    fun setIncome(amount: BigDecimal?) { viewModelScope.launch { store.setMonthlyIncome(amount) } }
    fun addFixed(label: String, amount: BigDecimal) { viewModelScope.launch { store.addExpense(label, amount) } }
    fun removeFixed(id: String) { viewModelScope.launch { store.removeExpense(id) } }
}
