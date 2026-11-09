package dev.kosh.financetracker.feature.addtransaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import dev.kosh.financetracker.ui.components.ChoiceChips
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.components.SegmentedTabs
import dev.kosh.financetracker.ui.components.TinwardTextField
import dev.kosh.financetracker.ui.components.labelFor
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PICKABLE = TransactionCategory.values().filter { it != TransactionCategory.UNCATEGORIZED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(onDone: () -> Unit, viewModel: AddTransactionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var pickingDate by remember { mutableStateOf(false) }
    var pickingSplitFor by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    val dateLabel = if (state.date == LocalDate.now()) "Today" else state.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))

    Column(modifier = Modifier.fillMaxSize().background(Tinward.colors.background).imePadding()) {
        ScreenHeader(if (state.editing) "Edit transaction" else "Add transaction", onBack = onDone)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                when {
                    !state.editing -> "For money the bank never texted, like cash or a missed message."
                    state.fullEdit -> "You added this entry, so everything can be changed."
                    else -> "This came from your bank, so its amount and date stay as recorded. You can change how Tinward labels it."
                },
                style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
            )
            if (state.fullEdit) {
                SegmentedTabs(
                    options = listOf("Spent", "Received"),
                    selectedIndex = if (state.direction == TransactionDirection.DEBIT) 0 else 1,
                    onSelect = { viewModel.setDirection(if (it == 0) TransactionDirection.DEBIT else TransactionDirection.CREDIT) },
                )
            }
            if (state.fullEdit) {
                TinwardTextField(
                    state.amountText, viewModel::setAmount, label = "Amount",
                    prefix = runCatching { java.util.Currency.getInstance(state.currency).getSymbol(Locale.getDefault()) }.getOrDefault(state.currency),
                    numeric = true, placeholder = "0.00", large = true, autoFocus = !state.editing,
                )
                TinwardTextField(state.label, viewModel::setLabel, label = "What was this for? (optional)", placeholder = "e.g. Groceries")
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Tinward.colors.divider, RoundedCornerShape(14.dp)).background(Tinward.colors.surface)
                        .clickable { pickingDate = true }.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Date", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                    Text(dateLabel, style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
                }
            } else {
                Text(
                    "${state.label.ifBlank { "Transaction" }} · ${formatMoney(state.amount ?: java.math.BigDecimal.ZERO, currencyCode = state.currency)} · $dateLabel",
                    style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary,
                )
            }

            if (state.accounts.isNotEmpty()) {
                SectionLabel("Account")
                ChoiceChips(state.accounts, state.account, { it.name }, { viewModel.setAccount(it.id) })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = Spacing.sm)) {
                    Text("Split across categories", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
                    Text("For one bill that covers several things.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
                }
                Switch(
                    checked = state.splitMode, onCheckedChange = viewModel::setSplitMode,
                    colors = SwitchDefaults.colors(checkedTrackColor = Tinward.colors.accent, checkedThumbColor = Tinward.colors.onAccent),
                )
            }

            if (!state.splitMode) {
                SectionLabel("Category")
                ChoiceChips(PICKABLE, state.category, { labelFor(it) }, viewModel::setCategory)
            } else {
                SectionLabel("Parts")
                state.splitRows.forEachIndexed { i, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.category?.let { labelFor(it) } ?: "Pick category",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (row.category != null) Tinward.colors.onAccent else Tinward.colors.textPrimary,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (row.category != null) Tinward.colors.accent else Tinward.colors.raised)
                                .clickable { pickingSplitFor = i }.padding(horizontal = Spacing.md, vertical = Spacing.ms),
                        )
                        TinwardTextField(row.amountText, { viewModel.setSplitAmount(i, it) }, label = "Amount", numeric = true, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.removeSplitRow(i) }) { Text("✕", color = Tinward.colors.textSecondary) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = viewModel::addSplitRow) { Text("Add a part", color = Tinward.colors.accentText) }
                    state.splitRemaining?.let {
                        Text(
                            if (it.signum() == 0) "Adds up" else if (it.signum() > 0) "${formatMoney(it, currencyCode = state.currency)} left to place" else "${formatMoney(it.abs(), currencyCode = state.currency)} over",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (it.signum() == 0) Tinward.colors.positive else Tinward.colors.textSecondary,
                        )
                    }
                }
            }

            TinwardTextField(state.customLabel, viewModel::setCustomLabel, label = "Your own label (optional)", placeholder = "e.g. Pets, Trip to Goa")
            if (state.knownLabels.isNotEmpty()) ChoiceChips(state.knownLabels, state.customLabel.takeIf { it in state.knownLabels }, { it }, viewModel::setCustomLabel)
            TinwardTextField(state.note, viewModel::setNote, label = "Note (optional)", singleLine = false)
            state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.negative) }
            Spacer(Modifier.height(Spacing.sm))
        }
        PrimaryButton(
            if (state.editing) "Save changes" else "Save",
            onClick = viewModel::save,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md).windowInsetsPadding(WindowInsets.navigationBars),
        )
    }

    pickingSplitFor?.let { index ->
        AlertDialog(
            onDismissRequest = { pickingSplitFor = null }, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
            title = { Text("Category for this part", color = Tinward.colors.textPrimary) },
            text = { ChoiceChips(PICKABLE, state.splitRows.getOrNull(index)?.category, { labelFor(it) }, { viewModel.setSplitCategory(index, it); pickingSplitFor = null }) },
            confirmButton = { TextButton(onClick = { pickingSplitFor = null }) { Text("Close", color = Tinward.colors.accentText) } },
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
