package dev.kosh.financetracker.feature.addtransaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.CategorySplit
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.SplitCodec
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.data.repository.AccountRepository
import dev.kosh.financetracker.data.repository.TransactionRepository
import dev.kosh.financetracker.ui.components.labelFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class SplitRow(val category: TransactionCategory? = null, val amountText: String = "")

data class AddTransactionUiState(
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val amountText: String = "",
    val label: String = "",
    val date: LocalDate = LocalDate.now(),
    val category: TransactionCategory? = null,
    val note: String = "",
    val customLabel: String = "",
    val knownLabels: List<String> = emptyList(),
    val splitMode: Boolean = false,
    val splitRows: List<SplitRow> = listOf(SplitRow(), SplitRow()),
    val saved: Boolean = false,
    val error: String? = null,
    val accounts: List<Account> = emptyList(),
    val accountId: Long? = null,
    /** True when editing an existing entry. */
    val editing: Boolean = false,
    /** Amount, date and direction can be changed (entries the user typed in). Imported rows keep their original evidence. */
    val fullEdit: Boolean = true,
) {
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }
    val currency: String get() = account?.currency ?: "INR"
    val amount: BigDecimal? get() = amountText.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    private val splitSum: BigDecimal get() = splitRows.fold(BigDecimal.ZERO) { a, r -> a + (r.amountText.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
    val splitRemaining: BigDecimal? get() = amount?.let { it - splitSum }
    val canSave: Boolean get() = amount != null && (splitMode || category != null)
}

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repository: TransactionRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    private val editId: Long = handle.get<Long>("id") ?: 0L
    private var original: Transaction? = null
    private val _state = MutableStateFlow(AddTransactionUiState(editing = editId > 0))
    val state: StateFlow<AddTransactionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val allAccounts = accountRepository.observeAccounts().first()
            val labels = repository.observeTransactions().first().mapNotNull { it.customLabel }.distinct().sorted()
            val existing = if (editId > 0) repository.observeTransaction(editId).first() else null
            // An entry on an archived account keeps showing (and keeping) that account while it is edited.
            val accounts = allAccounts.filter { !it.archived || it.id == existing?.accountId }
            original = existing
            _state.update { s ->
                if (existing == null) s.copy(accounts = accounts, accountId = accounts.firstOrNull()?.id, knownLabels = labels)
                else s.copy(
                    accounts = accounts, accountId = existing.accountId, knownLabels = labels,
                    direction = existing.direction, amountText = existing.amount.toPlainString(),
                    label = existing.merchant.orEmpty(),
                    date = existing.timestamp.atZone(ZoneId.systemDefault()).toLocalDate(),
                    category = existing.category?.takeIf { it != TransactionCategory.UNCATEGORIZED } ?: existing.category,
                    note = existing.notes.orEmpty(), customLabel = existing.customLabel.orEmpty(),
                    splitMode = existing.splits.isNotEmpty(),
                    splitRows = existing.splits.map { SplitRow(it.category, it.amount.toPlainString()) }.ifEmpty { listOf(SplitRow(), SplitRow()) },
                    fullEdit = existing.source == TransactionSource.MANUAL &&
                        existing.type in setOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.CREDIT_CARD_PURCHASE),
                )
            }
        }
    }

    private fun edit(block: AddTransactionUiState.() -> AddTransactionUiState) = _state.update { it.block().copy(error = null) }

    fun setAccount(id: Long) = edit { copy(accountId = id) }
    fun setDirection(d: TransactionDirection) = edit { copy(direction = d) }
    fun setAmount(text: String) = _state.update { s ->
        // digits and a single decimal point only, max two decimals
        val clean = text.filter { it.isDigit() || it == '.' }
        val ok = clean.count { it == '.' } <= 1 && clean.substringAfter('.', "").length <= 2
        if (ok) s.copy(amountText = clean, error = null) else s
    }
    fun setLabel(text: String) = edit { copy(label = text.take(60)) }
    fun setDate(date: LocalDate) = edit { copy(date = date) }
    fun setCategory(c: TransactionCategory) = edit { copy(category = c) }
    fun setNote(t: String) = edit { copy(note = t.take(200)) }
    fun setCustomLabel(t: String) = edit { copy(customLabel = t.take(30)) }
    fun setSplitMode(on: Boolean) = edit { copy(splitMode = on) }
    fun setSplitCategory(i: Int, c: TransactionCategory) = edit { copy(splitRows = splitRows.mapIndexed { k, r -> if (k == i) r.copy(category = c) else r }) }
    fun setSplitAmount(i: Int, t: String) = edit {
        val clean = t.filter { it.isDigit() || it == '.' }
        copy(splitRows = splitRows.mapIndexed { k, r -> if (k == i) r.copy(amountText = clean) else r })
    }
    fun addSplitRow() = edit { copy(splitRows = splitRows + SplitRow()) }
    fun removeSplitRow(i: Int) = edit { copy(splitRows = splitRows.filterIndexed { k, _ -> k != i }.ifEmpty { listOf(SplitRow(), SplitRow()) }) }

    fun save() {
        val s = _state.value
        val amount = s.amount ?: return
        val splits: List<CategorySplit> = if (s.splitMode) {
            val parts = s.splitRows.mapNotNull { r ->
                val c = r.category ?: return@mapNotNull null
                val a = r.amountText.toBigDecimalOrNull() ?: return@mapNotNull null
                CategorySplit(c, a)
            }
            if (parts.size != s.splitRows.size) { _state.update { it.copy(error = "Pick a category and an amount for every part.") }; return }
            SplitCodec.problem(amount, parts)?.let { msg -> _state.update { it.copy(error = msg) }; return }
            parts
        } else emptyList()
        val category = if (s.splitMode) splits.maxByOrNull { it.amount }!!.category else s.category ?: return
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val timestamp = if (s.date == LocalDate.now(zone)) now else s.date.atTime(LocalTime.NOON).atZone(zone).toInstant()
        val merchant = s.label.trim().ifBlank { labelFor(category) }
        val note = s.note.trim().ifBlank { null }
        val custom = s.customLabel.trim().ifBlank { null }
        val base = original

        viewModelScope.launch {
            when {
                base == null -> repository.save(
                    Transaction(
                        timestamp = timestamp, amount = amount, direction = s.direction,
                        type = typeFor(s.account, s.direction),
                        category = category, merchant = merchant, rawMerchant = merchant,
                        accountId = s.account?.id, accountSuffix = s.account?.last4, bank = s.account?.institution,
                        paymentMethod = PaymentMethod.UNKNOWN, source = TransactionSource.MANUAL, sourceMessageId = null,
                        rawSourceText = null, confidence = 1.0, notes = note, createdAt = now, currency = s.currency,
                        splits = splits, customLabel = custom,
                    ),
                )
                s.fullEdit -> repository.updateManual(
                    base.copy(
                        timestamp = if (s.date == base.timestamp.atZone(zone).toLocalDate()) base.timestamp else timestamp,
                        amount = amount, direction = s.direction, type = typeFor(s.account, s.direction),
                        category = category, merchant = merchant, rawMerchant = merchant,
                        accountId = s.account?.id, accountSuffix = s.account?.last4, bank = s.account?.institution,
                        notes = note, currency = s.currency, splits = splits, customLabel = custom,
                    ),
                )
                else -> repository.updateLabelling(base.id, category, note, custom, splits, s.account.takeIf { it?.id != base.accountId })
            }
            _state.update { it.copy(saved = true) }
        }
    }

    private fun typeFor(account: Account?, direction: TransactionDirection) = when {
        account?.type == AccountType.CREDIT_CARD && direction == TransactionDirection.DEBIT -> TransactionType.CREDIT_CARD_PURCHASE
        account?.type == AccountType.CREDIT_CARD -> TransactionType.REFUND
        direction == TransactionDirection.DEBIT -> TransactionType.EXPENSE
        else -> TransactionType.INCOME
    }
}
