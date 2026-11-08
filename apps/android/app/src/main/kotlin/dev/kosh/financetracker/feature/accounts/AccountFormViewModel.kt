package dev.kosh.financetracker.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.data.repository.AccountRepository
import dev.kosh.financetracker.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

val COMMON_CURRENCIES = listOf("INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD", "JPY")

data class AccountFormState(
    val type: AccountType = AccountType.BANK,
    val name: String = "",
    val institution: String = "",
    val last4: String = "",
    val currency: String = "INR",
    val opening: String = "",
    val limit: String = "",
    val statementDay: String = "",
    val dueDay: String = "",
    val linkedId: Long? = null,
    val banks: List<Account> = emptyList(),
    val error: String? = null,
    val saved: Boolean = false,
    val editing: Boolean = false,
)

@HiltViewModel
class AccountFormViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val accounts: AccountRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val id: Long = handle.get<Long>("id") ?: 0L
    private var original: Account? = null
    private val _state = MutableStateFlow(AccountFormState())
    val state: StateFlow<AccountFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val all = accounts.observeAccounts().first()
            val banks = all.filter { it.type == AccountType.BANK && !it.archived }
            val existing = if (id > 0) all.firstOrNull { it.id == id } else null
            original = existing
            _state.value = if (existing != null) AccountFormState(
                type = existing.type, name = existing.name, institution = existing.institution.orEmpty(), last4 = existing.last4.orEmpty(),
                currency = existing.currency, opening = existing.openingBalance.toPlainString(),
                limit = existing.creditLimit?.toPlainString().orEmpty(), statementDay = existing.statementDay?.toString().orEmpty(),
                dueDay = existing.dueDay?.toString().orEmpty(), linkedId = existing.linkedAccountId, banks = banks, editing = true,
            ) else AccountFormState(currency = settings.baseCurrency.first(), banks = banks)
        }
    }

    fun update(block: AccountFormState.() -> AccountFormState) = _state.update { it.block().copy(error = null) }

    private fun money(t: String) = t.trim().replace(",", "").toBigDecimalOrNull()

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) return fail("Give it a name.")
        if (s.last4.isNotEmpty() && (s.last4.length != 4 || !s.last4.all(Char::isDigit))) return fail("Last four digits only, or leave blank.")
        val opening = if (s.opening.isBlank()) BigDecimal.ZERO else money(s.opening) ?: return fail("Opening amount isn't a number.")
        if (s.type == AccountType.CREDIT_CARD && opening.signum() < 0) return fail("Enter what you owe as a positive amount.")
        val limit = if (s.type == AccountType.CREDIT_CARD && s.limit.isNotBlank()) money(s.limit)?.takeIf { it.signum() > 0 } ?: return fail("Credit limit isn't a valid amount.") else null
        fun day(t: String): Int? = if (t.isBlank()) null else t.toIntOrNull()?.takeIf { it in 1..31 }
        if (s.statementDay.isNotBlank() && day(s.statementDay) == null) return fail("Statement day must be 1 to 31.")
        if (s.dueDay.isNotBlank() && day(s.dueDay) == null) return fail("Due day must be 1 to 31.")
        if (s.type == AccountType.DEBIT_CARD && s.linkedId == null) return fail("Pick the bank account this card spends from.")
        val base = original
        val account = Account(
            id = base?.id ?: 0, name = s.name.trim(), type = s.type,
            institution = s.institution.trim().ifBlank { null }, last4 = s.last4.ifBlank { null }, currency = s.currency,
            openingBalance = if (s.type == AccountType.DEBIT_CARD) BigDecimal.ZERO else opening,
            openingDate = base?.openingDate ?: Instant.now(), archived = base?.archived ?: false, sortOrder = base?.sortOrder ?: 0,
            linkedAccountId = if (s.type == AccountType.DEBIT_CARD) s.linkedId else null,
            creditLimit = limit, statementDay = if (s.type == AccountType.CREDIT_CARD) day(s.statementDay) else null,
            dueDay = if (s.type == AccountType.CREDIT_CARD) day(s.dueDay) else null,
        )
        viewModelScope.launch {
            if (base == null) accounts.create(account) else accounts.update(account)
            _state.update { it.copy(saved = true) }
        }
    }

    private fun fail(msg: String) { _state.update { it.copy(error = msg) } }
}
