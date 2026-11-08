package dev.kosh.financetracker.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.data.repository.TransactionRepository
import dev.kosh.financetracker.feature.undo.UndoBus
import dev.kosh.financetracker.feature.undo.UndoEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import dev.kosh.financetracker.navigation.KoshDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TransactionRepository,
    accounts: dev.kosh.financetracker.data.repository.AccountRepository,
) : ViewModel() {

    private val transactionId: Long =
        checkNotNull(savedStateHandle.get<String>(KoshDestination.Detail.ARG_TRANSACTION_ID)).toLong()

    val transaction: StateFlow<Transaction?> = repository.observeTransaction(transactionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCategory(category: TransactionCategory) {
        viewModelScope.launch { repository.setCategory(transactionId, category) }
    }

    val extras: StateFlow<DetailExtras> = kotlinx.coroutines.flow.combine(repository.observeTransactions(), accounts.observeAccounts()) { all, accs ->
        val self = all.firstOrNull { it.id == transactionId }
        DetailExtras(
            refunded = all.filter { it.refundOfId == transactionId }.fold(BigDecimal.ZERO) { a, t -> a + t.amount },
            refundOfName = self?.refundOfId?.let { oid -> all.firstOrNull { it.id == oid }?.merchant ?: "a purchase" },
            accountName = self?.accountId?.let { id -> accs.firstOrNull { it.id == id }?.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailExtras())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    /** Deletes (recoverably) and offers Undo from the app shell. [onDone] runs after the row is hidden. */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val ids = repository.delete(transactionId)
            if (ids.isNotEmpty()) UndoBus.events.tryEmit(UndoEvent(if (ids.size > 1) "Transfer deleted" else "Transaction deleted", ids))
            onDone()
        }
    }

    fun refund(amount: BigDecimal, note: String?) {
        viewModelScope.launch {
            val r = repository.addRefund(transactionId, amount, Instant.now(), note)
            _message.value = r.exceptionOrNull()?.message ?: "Refund recorded. It reduces this month's spending."
        }
    }
}
