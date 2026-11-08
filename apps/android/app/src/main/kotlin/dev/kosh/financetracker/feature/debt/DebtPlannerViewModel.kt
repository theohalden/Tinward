package dev.kosh.financetracker.feature.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.finance.AccountLedger
import dev.kosh.financetracker.core.finance.DebtCard
import dev.kosh.financetracker.core.finance.DebtPlanner
import dev.kosh.financetracker.core.finance.StrategyOutcome
import dev.kosh.financetracker.core.finance.Utilization
import dev.kosh.financetracker.core.finance.UtilizationPoint
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.data.repository.AccountRepository
import dev.kosh.financetracker.data.repository.TransactionRepository
import dev.kosh.financetracker.data.settings.SettingsStore
import dev.kosh.financetracker.feature.plus.Entitlements
import dev.kosh.financetracker.feature.plus.Tier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import javax.inject.Inject

data class CardDebtRow(val account: Account, val owed: BigDecimal, val history: List<UtilizationPoint>, val minimum: BigDecimal?)

data class DebtUi(
    val plus: Boolean = false,
    val currency: String = "INR",
    val cards: List<CardDebtRow> = emptyList(),
    val withoutApr: List<Account> = emptyList(),
    val budgetText: String = "",
    val suggestedBudget: BigDecimal? = null,
    val outcomes: List<StrategyOutcome> = emptyList(),
    val totalOwed: BigDecimal = BigDecimal.ZERO,
)

@HiltViewModel
class DebtPlannerViewModel @Inject constructor(
    transactions: TransactionRepository,
    accounts: AccountRepository,
    settings: SettingsStore,
    entitlements: Entitlements,
) : ViewModel() {
    private val budget = MutableStateFlow<String?>(null)

    val state: StateFlow<DebtUi> = combine(
        transactions.observeTransactions(), accounts.observeAccounts(), settings.baseCurrency, entitlements.tier, budget,
    ) { txs, accs, currency, tier, budgetOverride ->
        val positions = AccountLedger.positions(accs, txs)
        val owing = positions.filter { it.account.type == AccountType.CREDIT_CARD && !it.account.archived && it.account.currency == currency && (it.amount ?: BigDecimal.ZERO).signum() > 0 }
        val debtCards = owing.mapNotNull { p -> p.account.apr?.let { DebtCard(p.account.id, p.account.name, p.amount!!, it, p.account.creditLimit) } }
        val minSum = debtCards.fold(BigDecimal.ZERO) { a, c -> a + DebtPlanner.minimumPayment(c) }
        val suggested = minSum.multiply(BigDecimal(2)).setScale(0, RoundingMode.UP).takeIf { debtCards.isNotEmpty() }
        val text = budgetOverride ?: suggested?.toPlainString().orEmpty()
        val amount = text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        DebtUi(
            plus = tier == Tier.PLUS, currency = currency,
            cards = owing.map { p -> CardDebtRow(p.account, p.amount!!, Utilization.history(p.account, txs, YearMonth.now(), 6), debtCards.firstOrNull { it.id == p.account.id }?.let(DebtPlanner::minimumPayment)) },
            withoutApr = owing.filter { it.account.apr == null }.map { it.account },
            budgetText = text, suggestedBudget = suggested,
            outcomes = if (amount != null && debtCards.isNotEmpty()) DebtPlanner.compare(debtCards, amount) else emptyList(),
            totalOwed = owing.fold(BigDecimal.ZERO) { a, p -> a + p.amount!! },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtUi())

    fun setBudget(text: String) = budget.update { text.filter { it.isDigit() || it == '.' } }
}
