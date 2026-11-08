package dev.kosh.financetracker.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.finance.AccountPosition
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.ui.components.EmptyState
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward

@Composable
fun AccountsScreen(
    onOpenAccount: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onOpenDebt: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Column(Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Accounts", actions = {
            TextButton(onClick = onAddAccount) { Text("Add", color = Tinward.colors.accentText, style = MaterialTheme.typography.labelLarge) }
        })
        if (state.loaded && state.positions.isEmpty()) {
            EmptyState(
                title = "No accounts yet",
                description = "Add the bank accounts, cash and cards you want to track. Tinward never connects to your bank; balances are calculated from what you and your messages record.",
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
            PrimaryButton("Add an account", onAddAccount, Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md).fillMaxWidth())
        } else LazyColumn(
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.ms),
        ) {
            if (state.net.isNotEmpty()) item {
                KoshCard(containerColor = Tinward.colors.raised) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text("Tracked net worth", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                        state.net.forEach { (cur, amount) ->
                            Text(formatMoney(amount, currencyCode = cur), style = MaterialTheme.typography.displaySmall, color = Tinward.colors.textPrimary)
                        }
                        Text(
                            "Accounts minus card debt, calculated from recorded history. Not a live bank balance, and different currencies are never added together.",
                            style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
                        )
                    }
                }
            }
            if (state.banks.isNotEmpty()) item { SectionLabel("Accounts") }
            items(state.banks.size) { i -> PositionCard(state.banks[i], onOpenAccount) }
            if (state.cards.any { it.account.type == dev.kosh.financetracker.core.model.AccountType.CREDIT_CARD }) item {
                KoshCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDebt)) {
                    Row(Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Debt planner", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                            Text("Payoff time, interest and limit use for your cards", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
                        }
                        Text("Plus", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.accentText)
                    }
                }
            }
            if (state.cards.isNotEmpty()) item { SectionLabel("Cards") }
            items(state.cards.size) { i -> PositionCard(state.cards[i], onOpenAccount) }
            if (state.archived.isNotEmpty()) {
                item { SectionLabel("Archived") }
                items(state.archived.size) { i -> PositionCard(state.archived[i], onOpenAccount) }
            }
        }
    }
}

@Composable
private fun PositionCard(p: AccountPosition, onOpen: (Long) -> Unit) {
    val a = p.account
    KoshCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(a.id) }) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary, modifier = Modifier.weight(1f))
                Text(a.type.label, style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
            }
            when {
                a.type == AccountType.CREDIT_CARD -> {
                    val owed = p.amount ?: java.math.BigDecimal.ZERO
                    Text(
                        if (owed.signum() < 0) "${formatMoney(owed.abs(), currencyCode = a.currency)} in credit" else "${formatMoney(owed, currencyCode = a.currency)} owed",
                        style = MaterialTheme.typography.headlineMedium, color = if (owed.signum() < 0) Tinward.colors.positive else Tinward.colors.negative,
                    )
                    p.available?.let { Text("${formatMoney(it, currencyCode = a.currency)} available of ${formatMoney(a.creditLimit!!, currencyCode = a.currency)} · ${p.utilizationPercent}% used", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary) }
                    a.dueDay?.let { Text("Payment due on the $it${suffixOf(it)}", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary) }
                }
                a.type == AccountType.DEBIT_CARD -> Text(
                    if (p.amount != null) "Spends from linked account · ${formatMoney(p.amount, currencyCode = a.currency)}" else "Not linked to an account",
                    style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
                )
                else -> Text(formatMoney(p.amount ?: java.math.BigDecimal.ZERO, currencyCode = a.currency), style = MaterialTheme.typography.headlineMedium, color = Tinward.colors.textPrimary)
            }
            if (!a.type.isCard && a.openingBalance.signum() == 0 && p.amount?.signum() == 0) {
                Text("Tracking starts today. Open this account and use Correct balance to enter what your bank shows.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.accentText)
            }
            Text(
                listOfNotNull(a.institution, a.last4?.let { "••$it" }, "${p.transactionCount} transactions", "tracked").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
            )
        }
    }
}

internal fun suffixOf(day: Int) = if (day in 11..13) "th" else when (day % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
