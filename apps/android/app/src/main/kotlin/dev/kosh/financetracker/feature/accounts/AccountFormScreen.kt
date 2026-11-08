package dev.kosh.financetracker.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.ui.components.ChoiceChips
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.components.TinwardTextField
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward

@Composable
fun AccountFormScreen(onDone: () -> Unit, viewModel: AccountFormViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsState()
    LaunchedEffect(s.saved) { if (s.saved) onDone() }
    val card = s.type == AccountType.CREDIT_CARD

    Column(Modifier.fillMaxSize().background(Tinward.colors.background).imePadding()) {
        ScreenHeader(if (s.editing) "Edit account" else "Add account", onBack = onDone)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                "You create accounts yourself. Tinward doesn't log in to banks, and never stores full card numbers, CVV or PINs.",
                style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
            )
            if (!s.editing) {
                SectionLabel("Type")
                ChoiceChips(AccountType.values().toList(), s.type, { it.label }, { t -> viewModel.update { copy(type = t) } })
            }
            TinwardTextField(s.name, { v -> viewModel.update { copy(name = v.take(40)) } }, "Name", placeholder = if (card) "e.g. HDFC Regalia" else "e.g. Salary account")
            TinwardTextField(s.institution, { v -> viewModel.update { copy(institution = v.take(40)) } }, "Bank or issuer (optional)", placeholder = "e.g. HDFC")
            TinwardTextField(s.last4, { v -> viewModel.update { copy(last4 = v.filter(Char::isDigit).take(4)) } }, "Last 4 digits (optional)", numeric = true,
                placeholder = "Helps match bank messages")

            if (s.type == AccountType.DEBIT_CARD) {
                SectionLabel("Spends from")
                if (s.banks.isEmpty()) Text("Add a bank account first, then link the card to it.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.negative)
                else ChoiceChips(s.banks, s.banks.firstOrNull { it.id == s.linkedId }, { it.name }, { b -> viewModel.update { copy(linkedId = b.id, currency = b.currency) } })
            } else {
                SectionLabel("Currency")
                ChoiceChips((COMMON_CURRENCIES + s.currency).distinct(), s.currency, { it }, { c -> viewModel.update { copy(currency = c) } })
                TinwardTextField(
                    s.opening, { v -> viewModel.update { copy(opening = v.filter { it.isDigit() || it == '.' || it == '-' }) } },
                    if (card) "Amount you owe today" else "Balance today", numeric = true, placeholder = "0.00",
                )
                Text(
                    if (card) "Tinward tracks purchases and payments from here on." else "Tinward adds and subtracts recorded transactions from here. You can correct it later with a balance adjustment.",
                    style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
                )
            }
            if (card) {
                TinwardTextField(s.limit, { v -> viewModel.update { copy(limit = v.filter { it.isDigit() || it == '.' }) } }, "Credit limit (as you entered it)", numeric = true)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TinwardTextField(s.statementDay, { v -> viewModel.update { copy(statementDay = v.filter(Char::isDigit).take(2)) } }, "Statement day", numeric = true, modifier = Modifier.weight(1f))
                    TinwardTextField(s.dueDay, { v -> viewModel.update { copy(dueDay = v.filter(Char::isDigit).take(2)) } }, "Payment due day", numeric = true, modifier = Modifier.weight(1f))
                }
                TinwardTextField(s.apr, { v -> viewModel.update { copy(apr = v.filter { it.isDigit() || it == '.' }.take(6)) } }, "Interest rate, % a year (optional, for the debt planner)", numeric = true)
            }
            s.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.negative) }
        }
        PrimaryButton(
            "Save", viewModel::save,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md).windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}
