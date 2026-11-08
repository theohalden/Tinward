package dev.kosh.financetracker.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.finance.AccountLedger
import dev.kosh.financetracker.core.finance.CardDues
import dev.kosh.financetracker.core.finance.EverydaySpend
import dev.kosh.financetracker.core.finance.MoneyTimeline
import dev.kosh.financetracker.core.finance.Recurrence
import dev.kosh.financetracker.core.finance.TimelineBuilder
import dev.kosh.financetracker.core.finance.WhatIf
import dev.kosh.financetracker.data.repository.AccountRepository
import dev.kosh.financetracker.data.repository.PlanningRepository
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
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TimelineUi(
    val plus: Boolean = false,
    val days: Int = 30,
    val everyday: Boolean = false,
    val whatIfs: List<WhatIf> = emptyList(),
    val timeline: MoneyTimeline? = null,
    /** The same forecast without the what-ifs, to show their effect. */
    val baseline: MoneyTimeline? = null,
    val everydayAvailable: Boolean = false,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    transactions: TransactionRepository,
    accounts: AccountRepository,
    planning: PlanningRepository,
    settings: SettingsStore,
    entitlements: Entitlements,
) : ViewModel() {
    private data class Controls(val days: Int, val everyday: Boolean, val whatIfs: List<WhatIf>)
    private val controls = MutableStateFlow(Controls(30, false, emptyList()))
    private val zone = ZoneId.systemDefault()

    val state: StateFlow<TimelineUi> = combine(
        transactions.observeTransactions(), accounts.observeAccounts(), planning.observeRecurring(), settings.baseCurrency,
        combine(controls, entitlements.tier) { c, t -> c to t },
    ) { txs, accs, recurring, currency, (c, tier) ->
        val today = LocalDate.now()
        val positions = AccountLedger.positions(accs, txs)
        val pending = recurring.flatMap { Recurrence.pending(it, txs, today, today.plusDays(c.days.toLong()), zone) }
        val dues = CardDues.upcoming(positions, today, c.days.toLong())
        val everyday = EverydaySpend.perDay(txs, recurring, currency, today, 90, zone)
        val perDay = everyday.takeIf { c.everyday }
        TimelineUi(
            plus = tier == Tier.PLUS, days = c.days, everyday = c.everyday && everyday != null, whatIfs = c.whatIfs, everydayAvailable = everyday != null,
            timeline = TimelineBuilder.build(positions, pending, dues, currency, today, c.days, perDay, c.whatIfs),
            baseline = if (c.whatIfs.isEmpty()) null else TimelineBuilder.build(positions, pending, dues, currency, today, c.days, perDay, emptyList()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUi())

    fun setDays(d: Int) = controls.update { it.copy(days = d) }
    fun setEveryday(on: Boolean) = controls.update { it.copy(everyday = on) }
    fun addWhatIf(date: LocalDate, label: String, amount: BigDecimal) = controls.update { it.copy(whatIfs = it.whatIfs + WhatIf(date, label.trim().ifBlank { "What if" }, amount)) }
    fun removeWhatIf(index: Int) = controls.update { it.copy(whatIfs = it.whatIfs.filterIndexed { i, _ -> i != index }) }
}
