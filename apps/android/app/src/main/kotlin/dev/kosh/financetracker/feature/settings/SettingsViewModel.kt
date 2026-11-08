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
    val backup: dev.kosh.financetracker.security.BackupManager,
) : ViewModel() {
    val appearance: StateFlow<Appearance> = settings.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Appearance.SYSTEM)

    val baseCurrency: StateFlow<String> = settings.baseCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "INR")

    fun setBaseCurrency(code: String) { viewModelScope.launch { settings.setBaseCurrency(code) } }

    fun setAppearance(value: Appearance) { viewModelScope.launch { settings.setAppearance(value) } }

    suspend fun buildExport(context: Context): Intent? = withContext(Dispatchers.IO) {
        val all = repository.observeTransactions().first()
        if (all.isEmpty()) null else CsvExporter.shareIntent(context, all)
    }
}
