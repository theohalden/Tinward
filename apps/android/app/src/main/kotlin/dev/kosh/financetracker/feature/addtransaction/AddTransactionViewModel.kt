package dev.kosh.financetracker.feature.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.data.repository.AccountRepository
import dev.kosh.financetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class AddTransactionUiState(
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val amountText: String = "",
    val label: String = "",
    val date: LocalDate = LocalDate.now(),
    val category: TransactionCategory? = null,
    val saved: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val accountId: Long? = null,
) {
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }
    val currency: String get() = account?.currency ?: "INR"
    val amount: BigDecimal? get() = amountText.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    val canSave: Boolean get() = amount != null && category != null
}

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AddTransactionUiState())
    val state: StateFlow<AddTransactionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val list = accountRepository.observeAccounts().first().filter { !it.archived }
            _state.update { it.copy(accounts = list, accountId = list.firstOrNull()?.id) }
        }
    }

    fun setAccount(id: Long) = _state.update { it.copy(accountId = id) }

    fun setDirection(d: TransactionDirection) = _state.update { it.copy(direction = d) }
    fun setAmount(text: String) = _state.update { s ->
        // digits and a single decimal point only, max two decimals
        val clean = text.filter { it.isDigit() || it == '.' }
        val ok = clean.count { it == '.' } <= 1 && clean.substringAfter('.', "").length <= 2
        if (ok) s.copy(amountText = clean) else s
    }
    fun setLabel(text: String) = _state.update { it.copy(label = text.take(60)) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun setCategory(c: TransactionCategory) = _state.update { it.copy(category = c) }

    fun save() {
        val s = _state.value
        val amount = s.amount ?: return
        val category = s.category ?: return
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val timestamp = if (s.date == LocalDate.now(zone)) now else s.date.atTime(LocalTime.NOON).atZone(zone).toInstant()
        val merchant = s.label.trim().ifBlank { null }
        viewModelScope.launch {
            repository.save(
                Transaction(
                    timestamp = timestamp,
                    amount = amount,
                    direction = s.direction,
                    type = when {
                        s.account?.type == AccountType.CREDIT_CARD && s.direction == TransactionDirection.DEBIT -> TransactionType.CREDIT_CARD_PURCHASE
                        s.account?.type == AccountType.CREDIT_CARD -> TransactionType.REFUND
                        s.direction == TransactionDirection.DEBIT -> TransactionType.EXPENSE
                        else -> TransactionType.INCOME
                    },
                    category = category,
                    merchant = merchant,
                    rawMerchant = merchant,
                    accountId = s.account?.id,
                    accountSuffix = s.account?.last4,
                    bank = s.account?.institution,
                    paymentMethod = PaymentMethod.UNKNOWN,
                    source = TransactionSource.MANUAL,
                    sourceMessageId = null,
                    rawSourceText = null,
                    confidence = 1.0,
                    notes = null,
                    createdAt = now,
                    currency = s.currency,
                ),
            )
            _state.update { it.copy(saved = true) }
        }
    }
}
