package dev.kosh.financetracker.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.finance.AccountLedger
import dev.kosh.financetracker.core.finance.AccountPosition
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.data.repository.AccountRepository
import dev.kosh.financetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

data class AccountsUiState(
    val positions: List<AccountPosition> = emptyList(),
    val net: Map<String, BigDecimal> = emptyMap(),
    val loaded: Boolean = false,
) {
    val active get() = positions.filter { !it.account.archived }
    val banks get() = active.filter { !it.account.type.isCard }
    val cards get() = active.filter { it.account.type.isCard }
    val archived get() = positions.filter { it.account.archived }
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    transactions: TransactionRepository,
) : ViewModel() {
    val state: StateFlow<AccountsUiState> = combine(accounts.observeAccounts(), transactions.observeTransactions()) { accs, txs ->
        val positions = AccountLedger.positions(accs, txs)
        AccountsUiState(positions, AccountLedger.netByCurrency(positions), loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun move(id: Long, up: Boolean) { viewModelScope.launch { accounts.move(id, up) } }
}

data class AccountDetailUi(
    val position: AccountPosition? = null,
    val others: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val message: String? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val accounts: AccountRepository,
    transactions: TransactionRepository,
) : ViewModel() {
    private val id: Long = handle.get<Long>("id") ?: 0L
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<AccountDetailUi> = combine(accounts.observeAccounts(), transactions.observeTransactions(), message) { accs, txs, msg ->
        val byId = accs.associateBy { it.id }
        val position = AccountLedger.positions(accs, txs).firstOrNull { it.account.id == id }
        val own = txs.filter { it.accountId == id || AccountLedger.effectiveAccountId(it, byId) == id }
        AccountDetailUi(position, accs.filter { it.id != id && !it.archived && it.type != AccountType.DEBIT_CARD }, own, msg, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailUi())

    fun clearMessage() { message.value = null }

    /** [outgoing]: money leaves this account for [otherId]; otherwise it arrives from [otherId]. */
    fun transfer(otherId: Long, outgoing: Boolean, amount: BigDecimal, note: String?) {
        viewModelScope.launch {
            val (from, to) = if (outgoing) id to otherId else otherId to id
            val r = accounts.transfer(from, to, amount, Instant.now(), note)
            message.value = r.exceptionOrNull()?.message ?: "Transfer recorded."
        }
    }

    fun reconcile(actual: BigDecimal) {
        val p = state.value.position ?: return
        val tracked = p.amount ?: return
        viewModelScope.launch {
            val changed = accounts.reconcile(p.account, tracked, actual)
            message.value = if (changed) "Balance adjusted. The adjustment shows in the history below." else "Already matches what Tinward tracked."
        }
    }

    fun setArchived(archived: Boolean) { viewModelScope.launch { accounts.setArchived(id, archived) } }
}
