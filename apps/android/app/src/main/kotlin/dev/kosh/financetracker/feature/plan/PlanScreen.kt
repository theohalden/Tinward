package dev.kosh.financetracker.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.data.budget.FixedExpenseItem
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.MonthSelectorPill
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SecondaryButton
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.components.SegmentedTabs
import dev.kosh.financetracker.ui.components.TinwardTextField
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.math.BigDecimal

@Composable
fun PlanScreen(viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var editIncome by remember { mutableStateOf(false) }
    var addFixed by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<FixedExpenseItem?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Plan") {
            MonthSelectorPill(state.selectedMonth, state.availableMonths, state.monthsWithData, viewModel::selectMonth)
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SegmentedTabs(listOf("Budget", "Fixed", "Rules"), tab, { tab = it })
            when (tab) {
                0 -> BudgetTab(state, onEditIncome = { editIncome = true })
                1 -> FixedTab(state, onAdd = { addFixed = true }, onRemove = { removing = it })
                else -> RulesTab()
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (editIncome) {
        var text by remember { mutableStateOf(state.income?.toPlainString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { editIncome = false },
            containerColor = Tinward.colors.surface,
            tonalElevation = 0.dp,
            title = { Text("Monthly income") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("What you expect to receive each month. Used only for planning.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                    TinwardTextField(text, { t -> text = t.filter { it.isDigit() || it == '.' } }, "Amount", prefix = "₹", numeric = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setIncome(text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 })
                    editIncome = false
                }) { Text("Save", color = Tinward.colors.accentText) }
            },
            dismissButton = { TextButton(onClick = { editIncome = false }) { Text("Cancel", color = Tinward.colors.textSecondary) } },
        )
    }
    if (addFixed) {
        var label by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        val valid = label.isNotBlank() && (amount.toBigDecimalOrNull()?.signum() ?: 0) > 0
        AlertDialog(
            onDismissRequest = { addFixed = false },
            containerColor = Tinward.colors.surface,
            tonalElevation = 0.dp,
            title = { Text("Add fixed expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TinwardTextField(label, { label = it.take(40) }, "Name", placeholder = "e.g. Rent")
                    TinwardTextField(amount, { t -> amount = t.filter { it.isDigit() || it == '.' } }, "Monthly amount", prefix = "₹", numeric = true)
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    viewModel.addFixed(label, BigDecimal(amount))
                    addFixed = false
                }) { Text("Add", color = if (valid) Tinward.colors.accentText else Tinward.colors.textTertiary) }
            },
            dismissButton = { TextButton(onClick = { addFixed = false }) { Text("Cancel", color = Tinward.colors.textSecondary) } },
        )
    }
    removing?.let { item ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = Tinward.colors.surface,
            tonalElevation = 0.dp,
            title = { Text("Remove ${item.label}?") },
            text = { Text("This only removes it from your plan. No transactions change.", color = Tinward.colors.textSecondary) },
            confirmButton = { TextButton(onClick = { viewModel.removeFixed(item.id); removing = null }) { Text("Remove", color = Tinward.colors.negative) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep", color = Tinward.colors.textSecondary) } },
        )
    }
}

@Composable
private fun BudgetTab(state: PlanUiState, onEditIncome: () -> Unit) {
    val income = state.income
    KoshCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Monthly budget", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
            if (income == null) {
                Text("Not set", style = MaterialTheme.typography.displaySmall, color = Tinward.colors.textTertiary)
                Text("Set your monthly income to see how your spending compares with the 50/30/20 rule.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                Spacer(Modifier.height(Spacing.sm))
                PrimaryButton("Set monthly income", onEditIncome, Modifier.fillMaxWidth())
            } else {
                Text(formatMoney(income), style = MaterialTheme.typography.displaySmall, color = Tinward.colors.textPrimary)
                val frac = (state.spent.toFloat() / income.toFloat()).coerceIn(0f, 1f)
                Bar(frac, if (state.spent > income) Tinward.colors.negative else Tinward.colors.accent)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Spent ${(state.spent.toFloat() / income.toFloat() * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textSecondary)
                    val left = income - state.spent
                    Text(
                        if (left.signum() >= 0) "Left ${formatMoney(left)}" else "Over by ${formatMoney(left.abs())}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (left.signum() >= 0) Tinward.colors.textSecondary else Tinward.colors.negative,
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                SecondaryButton("Change income", onEditIncome)
            }
        }
    }
    if (income != null) {
        val r = state.rule
        KoshCard {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SectionLabel("50 / 30 / 20")
                RuleRow("Needs", 0.5f, r.needs, r.fractionOfIncome(r.needs), Tinward.colors.category[1])
                RuleRow("Wants", 0.3f, r.wants, r.fractionOfIncome(r.wants), Tinward.colors.category[3])
                RuleRow("Left to save", 0.2f, r.savings.max(BigDecimal.ZERO), r.fractionOfIncome(r.savings.max(BigDecimal.ZERO)), Tinward.colors.category[2])
                if (r.unclassified.signum() > 0) {
                    Text(
                        "${formatMoney(r.unclassified)} is uncategorised or in neither group, so it only reduces what is left to save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Tinward.colors.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleRow(label: String, target: Float, amount: BigDecimal, actual: Float?, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$label · target ${(target * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = Tinward.colors.textPrimary)
            Text(
                "${formatMoney(amount)}${actual?.let { " (${(it * 100).toInt()}%)" } ?: ""}",
                style = MaterialTheme.typography.labelMedium,
                color = Tinward.colors.textSecondary,
            )
        }
        Bar(((actual ?: 0f) / 1f).coerceIn(0f, 1f), color, marker = target)
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, marker: Float? = null) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Tinward.colors.track)) {
        Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(color, RoundedCornerShape(4.dp)))
        if (marker != null) {
            Box(Modifier.fillMaxWidth(marker).height(8.dp)) {
                Box(Modifier.align(Alignment.CenterEnd).size(2.dp, 8.dp).background(Tinward.colors.textPrimary))
            }
        }
    }
}

@Composable
private fun FixedTab(state: PlanUiState, onAdd: () -> Unit, onRemove: (FixedExpenseItem) -> Unit) {
    KoshCard {
        Column {
            Column(Modifier.padding(Spacing.md)) {
                Text("Fixed monthly expenses", style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary)
                Text("What goes out every month regardless of what Tinward detects.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
            }
            if (state.fixed.isEmpty()) {
                HorizontalDivider(color = Tinward.colors.divider)
                Text("Nothing added yet.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textTertiary, modifier = Modifier.padding(Spacing.md))
            }
            state.fixed.forEach { item ->
                HorizontalDivider(color = Tinward.colors.divider)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.label, style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary, modifier = Modifier.weight(1f))
                    Text(formatMoney(item.amount), style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
                    Box(
                        Modifier.size(48.dp).clickable { onRemove(item) }.semantics { contentDescription = "Remove ${item.label}" },
                        contentAlignment = Alignment.Center,
                    ) { Text("×", style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textTertiary) }
                }
            }
            HorizontalDivider(color = Tinward.colors.divider)
            Row(Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total fixed", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                Text(formatMoney(state.fixedTotal), style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
            }
            state.afterFixed?.let {
                Row(Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Left after fixed expenses", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                    Text(formatMoney(it), style = MaterialTheme.typography.bodyMedium, color = if (it.signum() >= 0) Tinward.colors.positive else Tinward.colors.negative)
                }
            }
        }
    }
    PrimaryButton("Add fixed expense", onAdd, Modifier.fillMaxWidth())
}

@Composable
private fun RulesTab() {
    KoshCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionLabel("The 50/30/20 rule")
            Text(
                "A simple split of income: half for needs, 30% for wants, and 20% kept as savings. Tinward sorts your recorded spending by category, so the result is a guide, not a verdict.",
                style = MaterialTheme.typography.bodyMedium,
                color = Tinward.colors.textSecondary,
            )
            Text("Needs", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
            Text("Groceries, bills, rent, fuel, health, education, transport.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
            Text("Wants", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
            Text("Food and dining, shopping, entertainment, subscriptions, personal care, technology, home, fees.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
            Text("Anything uncategorised, family or business spending counts toward neither group until you categorise it in Review.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
        }
    }
}
