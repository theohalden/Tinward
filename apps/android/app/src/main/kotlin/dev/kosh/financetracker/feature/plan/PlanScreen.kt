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
fun PlanScreen(onOpenTimeline: () -> Unit, onOpenPlus: () -> Unit, viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var editIncome by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Plan") {
            MonthSelectorPill(state.selectedMonth, state.availableMonths, state.monthsWithData, viewModel::selectMonth)
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SegmentedTabs(listOf("Budget", "Bills", "Goal", "50/30/20"), tab, { tab = it })
            message?.let {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.negative, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearMessage) { Text("Dismiss", color = Tinward.colors.textSecondary) }
                }
            }
            when (tab) {
                0 -> BudgetTab(state, viewModel, onEditIncome = { editIncome = true }, onOpenTimeline = onOpenTimeline, onOpenPlus = onOpenPlus)
                1 -> BillsTab(state, viewModel)
                2 -> GoalTab(state, viewModel, onOpenPlus)
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
                    TinwardTextField(text, { t -> text = t.filter { it.isDigit() || it == '.' } }, "Amount", prefix = state.currency, numeric = true)
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
}

@Composable
internal fun RuleRow(label: String, target: Float, amount: BigDecimal, actual: Float?, color: Color) {
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
internal fun Bar(fraction: Float, color: Color, marker: Float? = null) {
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
