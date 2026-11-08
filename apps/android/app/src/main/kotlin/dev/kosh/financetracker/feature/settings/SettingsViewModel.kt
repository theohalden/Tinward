package dev.kosh.financetracker.feature.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.data.repository.TransactionRepository
import dev.kosh.financetracker.data.settings.Appearance
import dev.kosh.financetracker.data.settings.SettingsStore
import dev.kosh.financetracker.feature.export.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val repository: TransactionRepository,
    val vault: dev.kosh.financetracker.security.Vault,
    val entitlements: dev.kosh.financetracker.feature.plus.Entitlements,
    private val syncManager: dev.kosh.financetracker.feature.sync.SmsSyncManager,
    val backup: dev.kosh.financetracker.security.BackupManager,
) : ViewModel() {
    val appearance: StateFlow<Appearance> = settings.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Appearance.SYSTEM)

    val baseCurrency: StateFlow<String> = settings.baseCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "INR")

    /** Counts only; no message text. */
    val scan: StateFlow<dev.kosh.financetracker.feature.sync.SyncState> = syncManager.state

    val dollarCurrency: StateFlow<String> = settings.dollarCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "USD")

    fun setDollarCurrency(code: String) { viewModelScope.launch { settings.setDollarCurrency(code) } }

    fun setBaseCurrency(code: String) { viewModelScope.launch { settings.setBaseCurrency(code) } }

    fun setAppearance(value: Appearance) { viewModelScope.launch { settings.setAppearance(value) } }

    suspend fun buildExport(context: Context): Intent? = withContext(Dispatchers.IO) {
        val all = repository.observeTransactions().first()
        if (all.isEmpty()) null else CsvExporter.shareIntent(context, all)
    }
}
