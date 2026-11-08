package dev.kosh.financetracker.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.finance.CategorySlice
import dev.kosh.financetracker.core.finance.DaySpend
import dev.kosh.financetracker.core.finance.FinanceCalculator
import dev.kosh.financetracker.core.finance.AccountLedger
import dev.kosh.financetracker.core.finance.MonthComparison
import dev.kosh.financetracker.core.finance.MonthPoint
import dev.kosh.financetracker.core.finance.MonthSummary
import dev.kosh.financetracker.core.finance.Trends
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.data.repository.AccountRepository
import dev.kosh.financetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class InsightsUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val availableMonths: List<YearMonth> = emptyList(),
    val monthsWithData: Set<YearMonth> = emptySet(),
    val monthSummary: MonthSummary = MonthSummary(BigDecimal.ZERO, BigDecimal.ZERO),
    val percentChangeVsPreviousMonth: Float? = null,
    val dailySpend: List<DaySpend> = emptyList(),
    val categoryBreakdown: List<CategorySlice> = emptyList(),
    val hasAnyData: Boolean = false,
    val currency: String = "INR",
    /** Six months ending at the selected one, oldest first. */
    val trend: List<MonthPoint> = emptyList(),
    val averageSpent: BigDecimal? = null,
    val comparison: MonthComparison? = null,
    val stand: Standing? = null,
)

/** Where tracked money stands today, in one currency. An estimate from records, not a bank balance. */
data class Standing(val assets: BigDecimal, val cardDebt: BigDecimal, val accountsWithoutBalance: Int) {
    val net: BigDecimal get() = assets - cardDebt
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    repository: TransactionRepository,
    settings: dev.kosh.financetracker.data.settings.SettingsStore,
    accounts: AccountRepository,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val selectedMonth = MutableStateFlow<YearMonth?>(null)

    val uiState: StateFlow<InsightsUiState> = combine(
        repository.observeTransactions(),
        selectedMonth,
        settings.baseCurrency,
        accounts.observeAccounts(),
    ) { allTransactions, explicitMonth, base, accountList ->
        val transactions = allTransactions.filter { it.currency == base }
        val month = explicitMonth ?: YearMonth.now(zone)
        val monthsWithData = FinanceCalculator.monthsWithData(transactions, zone)
        val months = (FinanceCalculator.recentMonths(YearMonth.now(zone)) + monthsWithData)
            .distinct()
            .sortedDescending()

        val trend = Trends.lastMonths(transactions, month, 6, zone)
        InsightsUiState(
            selectedMonth = month,
            availableMonths = months,
            monthsWithData = monthsWithData.toSet(),
            monthSummary = FinanceCalculator.summaryForMonth(transactions, month, zone),
            percentChangeVsPreviousMonth = FinanceCalculator.percentChangeVsPreviousMonth(transactions, month, zone),
            dailySpend = FinanceCalculator.dailySpendForMonth(transactions, month, zone),
            categoryBreakdown = FinanceCalculator.categoryBreakdownForMonth(transactions, month, zone),
            hasAnyData = transactions.isNotEmpty(),
            currency = base,
            trend = trend,
            averageSpent = Trends.averageSpent(trend),
            comparison = Trends.comparison(transactions, month, zone),
            stand = standing(AccountLedger.positions(accountList, allTransactions), base),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun selectMonth(month: YearMonth) {
        selectedMonth.value = month
    }

    private fun standing(positions: List<dev.kosh.financetracker.core.finance.AccountPosition>, currency: String): Standing? {
        val mine = positions.filter { !it.account.archived && it.account.currency == currency }
        val assets = mine.filter { it.account.type != AccountType.CREDIT_CARD && it.account.type != AccountType.DEBIT_CARD }
        if (mine.isEmpty()) return null
        return Standing(
            assets = assets.fold(BigDecimal.ZERO) { a, p -> a + (p.amount ?: BigDecimal.ZERO) },
            cardDebt = mine.filter { it.account.type == AccountType.CREDIT_CARD }.fold(BigDecimal.ZERO) { a, p -> a + (p.amount ?: BigDecimal.ZERO) },
            accountsWithoutBalance = assets.count { it.amount == null || it.amount.signum() == 0 },
        )
    }
}
