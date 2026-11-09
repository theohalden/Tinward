package dev.kosh.financetracker.feature.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.finance.AutoRule
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.data.database.RuleChangeEntity
import dev.kosh.financetracker.data.repository.AccountRepository
import dev.kosh.financetracker.data.repository.RulesRepository
import dev.kosh.financetracker.feature.plus.Entitlements
import dev.kosh.financetracker.feature.plus.Tier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleBatch(val batchId: Long, val automatic: Boolean, val atMillis: Long, val changes: List<RuleChangeEntity>)

data class RulesUiState(
    val plus: Boolean = false,
    val rules: List<AutoRule> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val batches: List<RuleBatch> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repo: RulesRepository,
    accountRepository: AccountRepository,
    entitlements: Entitlements,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<RulesUiState> = combine(entitlements.tier, repo.rulesFlow, accountRepository.observeAccounts(), repo.recentChanges, message) { tier, rules, accounts, changes, msg ->
        RulesUiState(
            plus = tier == Tier.PLUS, rules = rules, accounts = accounts.filter { !it.archived },
            batches = changes.groupBy { it.batchId }.map { (id, list) -> RuleBatch(id, list.all { it.automatic }, list.maxOf { it.appliedAtEpochMillis }, list) }.sortedByDescending { it.batchId },
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUiState())

    fun save(rule: AutoRule) = viewModelScope.launch { repo.save(rule) }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun setEnabled(id: Long, on: Boolean) = viewModelScope.launch { repo.setEnabled(id, on) }
    fun move(id: Long, by: Int) = viewModelScope.launch { repo.move(id, by) }
    fun clearMessage() = message.update { null }

    suspend fun matchCount(rule: AutoRule): Int = repo.matchCount(rule)
    suspend fun previewCount(): Int = repo.previewChanges().size

    fun applyToExisting() = viewModelScope.launch {
        val run = repo.applyToExisting()
        message.value = if (run.changed == 0) "Nothing needed changing." else "Updated ${run.changed} change${if (run.changed == 1) "" else "s"}. You can undo this below."
    }

    fun undo(batchId: Long) = viewModelScope.launch {
        val n = repo.undo(batchId)
        message.value = if (n == 0) "Nothing to put back: those entries were changed since." else "Put back $n change${if (n == 1) "" else "s"}."
    }
}
