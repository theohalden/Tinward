package dev.kosh.financetracker.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class AccountRowUi(
    val bank: String?,
    val suffix: String,
    /** Balance after the latest transaction that reported one; null if none ever did. */
    val balance: BigDecimal?,
    val balanceAsOf: Instant?,
    val monthIn: BigDecimal,
    val monthOut: BigDecimal,
    val transactionCount: Int,
)

data class AccountsUiState(val accounts: List<AccountRowUi> = emptyList(), val loaded: Boolean = false) {
    val knownBalances: List<BigDecimal> get() = accounts.mapNotNull { it.balance }
    val totalKnownBalance: BigDecimal? get() = knownBalances.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO) { a, b -> a + b }
}

@HiltViewModel
class AccountsViewModel @Inject constructor(repository: TransactionRepository) : ViewModel() {
    private val zone = ZoneId.systemDefault()

    val state: StateFlow<AccountsUiState> = repository.observeTransactions().map { all ->
        val month = YearMonth.now(zone)
        val rows = all.filter { !it.accountSuffix.isNullOrBlank() }
            .groupBy { (it.bank?.trim()?.lowercase()) to it.accountSuffix!! }
            .map { (key, txs) ->
                val withBalance = txs.filter { it.balanceAfter != null }.maxByOrNull { it.timestamp }
                val thisMonth = txs.filter { YearMonth.from(it.timestamp.atZone(zone)) == month }
                AccountRowUi(
                    bank = txs.firstNotNullOfOrNull { it.bank },
                    suffix = key.second,
                    balance = withBalance?.balanceAfter,
                    balanceAsOf = withBalance?.timestamp,
                    monthIn = thisMonth.filter { it.direction == dev.kosh.financetracker.core.model.TransactionDirection.CREDIT }.fold(BigDecimal.ZERO) { a, b -> a + b.amount },
                    monthOut = thisMonth.filter { it.direction == dev.kosh.financetracker.core.model.TransactionDirection.DEBIT }.fold(BigDecimal.ZERO) { a, b -> a + b.amount },
                    transactionCount = txs.size,
                )
            }
            .sortedByDescending { it.transactionCount }
        AccountsUiState(rows, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())
}
