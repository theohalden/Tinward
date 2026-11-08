package dev.kosh.financetracker.feature.trail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.core.model.countsAsSpending
import dev.kosh.financetracker.data.repository.AccountRepository
import dev.kosh.financetracker.data.repository.TransactionRepository
import dev.kosh.financetracker.ui.components.labelFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class TrailFilter { ALL, EXPENSES, INCOME, TRANSFERS }

enum class Period(val label: String) { ALL("All time"), THIS_MONTH("This month"), LAST_30("Last 30 days"), THIS_YEAR("This year") }

/** Everything the Activity list can be narrowed by. Pure data so the filtering is unit-tested. */
data class TrailFilters(
    val query: String = "",
    val type: TrailFilter = TrailFilter.ALL,
    val period: Period = Period.ALL,
    val accountId: Long? = null,
    val category: TransactionCategory? = null,
) {
    /** Filters beyond search and type, shown as a count on the Filters button. */
    val activeExtras: Int get() = listOf(period != Period.ALL, accountId != null, category != null).count { it }

    fun apply(all: List<Transaction>, accounts: List<Account>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<Transaction> {
        val byId = accounts.associateBy { it.id }
        return all.filter { matchesType(it) && matchesPeriod(it, today, zone) && matchesAccount(it, byId) && matchesCategory(it) && matchesQuery(it) }
    }

    private fun matchesType(t: Transaction) = when (type) {
        TrailFilter.ALL -> true
        TrailFilter.EXPENSES -> t.type.countsAsSpending()
        TrailFilter.INCOME -> t.type == TransactionType.INCOME
        TrailFilter.TRANSFERS -> t.type == TransactionType.TRANSFER || t.type == TransactionType.CREDIT_CARD_PAYMENT || t.type == TransactionType.CASH_WITHDRAWAL
    }

    private fun matchesPeriod(t: Transaction, today: LocalDate, zone: ZoneId): Boolean {
        val day = t.timestamp.atZone(zone).toLocalDate()
        return when (period) {
            Period.ALL -> true
            Period.THIS_MONTH -> day.year == today.year && day.month == today.month
            Period.LAST_30 -> !day.isBefore(today.minusDays(29)) && !day.isAfter(today)
            Period.THIS_YEAR -> day.year == today.year
        }
    }

    private fun matchesAccount(t: Transaction, byId: Map<Long, Account>): Boolean {
        val id = accountId ?: return true
        if (t.accountId == id) return true
        // A debit card's spending shows under the bank account it draws from.
        return t.accountId?.let { byId[it] }?.linkedAccountId == id
    }

    private fun matchesCategory(t: Transaction): Boolean {
        val c = category ?: return true
        return t.category == c || t.splits.any { it.category == c }
    }

    private fun matchesQuery(t: Transaction): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return t.merchant?.lowercase()?.contains(q) == true ||
            t.accountSuffix?.contains(q) == true ||
            t.amount.toPlainString().contains(q) ||
            t.notes?.lowercase()?.contains(q) == true ||
            t.customLabel?.lowercase()?.contains(q) == true ||
            t.category?.let { labelFor(it).lowercase().contains(q) } == true
    }
}

data class TrailUiState(
    val filters: TrailFilters = TrailFilters(),
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
) {
    val query: String get() = filters.query
    val filter: TrailFilter get() = filters.type
    val isFiltered: Boolean get() = filters.query.isNotBlank() || filters.type != TrailFilter.ALL || filters.activeExtras > 0
}

@HiltViewModel
class TrailViewModel @Inject constructor(
    repository: TransactionRepository,
    accounts: AccountRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(TrailFilters())

    val uiState: StateFlow<TrailUiState> = combine(repository.observeTransactions(), filters, accounts.observeAccounts()) { transactions, f, accs ->
        TrailUiState(f, accs, f.apply(transactions, accs))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrailUiState())

    fun setQuery(value: String) = filters.update { it.copy(query = value) }
    fun setFilter(value: TrailFilter) = filters.update { it.copy(type = value) }
    fun setPeriod(value: Period) = filters.update { it.copy(period = value) }
    fun setAccount(id: Long?) = filters.update { it.copy(accountId = id) }
    fun setCategory(c: TransactionCategory?) = filters.update { it.copy(category = c) }
    fun clearExtras() = filters.update { it.copy(period = Period.ALL, accountId = null, category = null) }
}
