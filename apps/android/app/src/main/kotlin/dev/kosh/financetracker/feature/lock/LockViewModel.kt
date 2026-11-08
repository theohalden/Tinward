package dev.kosh.financetracker.feature.lock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kosh.financetracker.data.budget.FixedBudgetStore
import dev.kosh.financetracker.feature.sync.SmsSyncManager
import dev.kosh.financetracker.security.LockState
import dev.kosh.financetracker.security.UnlockResult
import dev.kosh.financetracker.security.Vault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LockUi(
    val busy: Boolean = false,
    val error: String? = null,
    val retryAtMillis: Long = 0L,
)

@HiltViewModel
class LockViewModel @Inject constructor(
    val vault: Vault,
    @ApplicationContext private val context: Context,
    // Lazy: these reach the encrypted database, which must not be opened before unlock.
    private val sms: Lazy<SmsSyncManager>,
    private val budget: Lazy<FixedBudgetStore>,
    private val linker: Lazy<dev.kosh.financetracker.data.repository.AccountLinker>,
    private val planning: Lazy<dev.kosh.financetracker.data.repository.PlanningRepository>,
    private val reminders: Lazy<dev.kosh.financetracker.feature.reminders.ReminderSync>,
) : ViewModel() {
    private val _ui = MutableStateFlow(LockUi())
    val ui: StateFlow<LockUi> = _ui.asStateFlow()
    val state: StateFlow<LockState> = vault.state

    fun clearError() { _ui.value = _ui.value.copy(error = null) }

    fun setup(secret: String, confirm: String) {
        if (secret != confirm) { _ui.value = LockUi(error = "The two entries don't match."); return }
        _ui.value = LockUi(busy = true)
        val chars = secret.toCharArray()
        viewModelScope.launch {
            val err = vault.setup(chars)
            chars.fill('\u0000')
            _ui.value = LockUi(error = err)
            if (err == null) afterUnlock()
        }
    }

    fun unlock(secret: String) {
        _ui.value = LockUi(busy = true)
        val chars = secret.toCharArray()
        viewModelScope.launch {
            val r = vault.unlock(chars)
            chars.fill('\u0000')
            _ui.value = when (r) {
                is UnlockResult.Success -> LockUi().also { afterUnlock() }
                is UnlockResult.WrongSecret -> LockUi(error = "Incorrect PIN. ${attemptsHint()}")
                is UnlockResult.Throttled -> LockUi(error = "Too many attempts.", retryAtMillis = r.retryAtMillis)
                is UnlockResult.NotConfigured -> LockUi(error = "Could not open your data. Try again.")
                is UnlockResult.Failed -> LockUi(error = r.message)
            }
        }
    }

    fun unlockWithKey(key: ByteArray) {
        viewModelScope.launch {
            val err = vault.unlockWithKey(key)
            _ui.value = LockUi(error = err)
            if (err == null) afterUnlock()
        }
    }

    fun showError(message: String) { _ui.value = LockUi(error = message) }

    private fun attemptsHint(): String {
        val left = 5 - vault.failedAttempts
        return if (left in 1..3) "$left tries before a short wait." else ""
    }

    /** Work that touches the database or budget data, only possible once unlocked. */
    private fun afterUnlock() {
        viewModelScope.launch {
            runCatching { budget.get().encryptLegacyValues() }
            linker.get().start(viewModelScope)
            runCatching { planning.get().importFixedExpensesOnce() }
            reminders.get().start(viewModelScope)
            val smsOk = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
            if (vault.smsMissedWhileLocked && smsOk) {
                vault.smsMissedWhileLocked = false
                runCatching { sms.get().sync() }
            }
        }
    }
}
