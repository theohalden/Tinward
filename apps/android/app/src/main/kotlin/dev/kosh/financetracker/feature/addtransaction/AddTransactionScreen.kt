package dev.kosh.financetracker.feature.addtransaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.components.SegmentedTabs
import dev.kosh.financetracker.ui.components.TinwardTextField
import dev.kosh.financetracker.ui.components.labelFor
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(onDone: () -> Unit, viewModel: AddTransactionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var pickingDate by remember { mutableStateOf(false) }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Column(modifier = Modifier.fillMaxSize().background(Tinward.colors.background).imePadding()) {
        ScreenHeader("Add transaction", onBack = onDone)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                "For money the bank never texted, like cash or a missed message.",
                style = MaterialTheme.typography.bodyMedium,
                color = Tinward.colors.textSecondary,
            )
            SegmentedTabs(
                options = listOf("Spent", "Received"),
                selectedIndex = if (state.direction == TransactionDirection.DEBIT) 0 else 1,
                onSelect = { viewModel.setDirection(if (it == 0) TransactionDirection.DEBIT else TransactionDirection.CREDIT) },
            )
            if (state.accounts.isNotEmpty()) {
                SectionLabel("Account")
                dev.kosh.financetracker.ui.components.ChoiceChips(state.accounts, state.account, { it.name }, { viewModel.setAccount(it.id) })
            }
            TinwardTextField(
                state.amountText, viewModel::setAmount, label = "Amount",
                prefix = runCatching { java.util.Currency.getInstance(state.currency).getSymbol(Locale.getDefault()) }.getOrDefault(state.currency),
                numeric = true, placeholder = "0.00",
            )
            TinwardTextField(state.label, viewModel::setLabel, label = "What was this for? (optional)", placeholder = "e.g. Groceries")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Tinward.colors.divider, RoundedCornerShape(14.dp))
                    .background(Tinward.colors.surface)
                    .clickable { pickingDate = true }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Date", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                Text(
                    if (state.date == LocalDate.now()) "Today" else state.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Tinward.colors.textPrimary,
                )
            }

            SectionLabel("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TransactionCategory.values().filter { it != TransactionCategory.UNCATEGORIZED }.forEach { c ->
                    val selected = state.category == c
                    Text(
                        labelFor(c),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Tinward.colors.onAccent else Tinward.colors.textPrimary,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (selected) Tinward.colors.accent else Tinward.colors.raised)
                            .clickable { viewModel.setCategory(c) }
                            .padding(horizontal = Spacing.md, vertical = Spacing.ms),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
        PrimaryButton(
            "Save",
            onClick = viewModel::save,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md).windowInsetsPadding(WindowInsets.navigationBars),
        )
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.setDate(if (picked.isAfter(LocalDate.now())) LocalDate.now() else picked)
                    }
                    pickingDate = false
                }) { Text("OK", color = Tinward.colors.accentText) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel", color = Tinward.colors.textSecondary) } },
        ) { DatePicker(state = pickerState) }
    }
}
