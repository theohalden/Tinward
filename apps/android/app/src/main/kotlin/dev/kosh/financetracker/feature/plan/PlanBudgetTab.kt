package dev.kosh.financetracker.feature.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kosh.financetracker.core.finance.BudgetStatus
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.ui.components.ChoiceChips
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.SecondaryButton
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.components.TinwardTextField
import dev.kosh.financetracker.ui.components.labelFor
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Composable
internal fun BudgetTab(state: PlanUiState, vm: PlanViewModel, onEditIncome: () -> Unit, onOpenTimeline: () -> Unit, onOpenPlus: () -> Unit) {
    var editOverall by remember { mutableStateOf(false) }
    var addCategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryBudgetRow?>(null) }

    AvailableCard(state)

    KoshCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTimeline)) {
        Row(Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Money Timeline", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                Text("Where your balance is heading over 30, 60 or 90 days", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
            }
            Text("Plus", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.accentText)
        }
    }

    KoshCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionLabel("Budget for ${state.selectedMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }}")
            val overall = state.overall
            if (overall == null) {
                Text("Set a limit for the whole month and Tinward shows how you are tracking against it.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                PrimaryButton("Set monthly budget", { editOverall = true }, Modifier.fillMaxWidth())
            } else {
                BudgetLine("Overall", overall, state.currency, note = state.overallCarry.takeIf { it.signum() > 0 }?.let { "Includes ${formatMoney(it, currencyCode = state.currency)} carried over" })
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecondaryButton("Change", { editOverall = true }, Modifier.weight(1f))
                    SecondaryButton("Remove", { vm.setOverallBudget(null) }, Modifier.weight(1f))
                }
            }
            if (state.categoryBudgets.isNotEmpty()) HorizontalDivider(color = Tinward.colors.divider)
            state.categoryBudgets.forEach { row ->
                Column(Modifier.fillMaxWidth().clickable { if (row.paused) onOpenPlus() else editingCategory = row }.padding(vertical = 2.dp)) {
                    BudgetLine(
                        labelFor(row.limit.category!!), row.status, state.currency, paused = row.paused,
                        note = if (row.paused) "Paused \u00B7 needs Tinward Plus. Your budget is kept." else row.carriedIn.takeIf { it.signum() > 0 }?.let { "Includes ${formatMoney(it, currencyCode = state.currency)} carried over" },
                    )
                }
            }
            if (state.canAddCategoryBudget) {
                SecondaryButton("Add a category budget", { addCategory = true }, Modifier.fillMaxWidth())
            } else {
                SecondaryButton("More category budgets with Plus", onOpenPlus, Modifier.fillMaxWidth())
            }
            Text(
                (if (state.plus) "Tinward Plus: unlimited category budgets, with rollover. " else "Free plan: one monthly budget and up to 3 category budgets. ") +
                    "Transfers are never counted, and refunds reduce what you have spent.",
                style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
            )
        }
    }

    IncomeAndRule(state, onEditIncome)

    if (editOverall) BudgetDialog(
        title = "Monthly budget", help = "The most you want to spend this month, across everything.",
        initial = state.overallLimit?.amount?.toPlainString().orEmpty(), currency = state.currency, rolloverInitial = state.overallLimit?.rollover ?: false,
        plus = state.plus, onOpenPlus = { editOverall = false; onOpenPlus() }, onDismiss = { editOverall = false }, onRemove = null,
    ) { amount, roll -> vm.setOverallBudget(amount, roll); editOverall = false }

    if (addCategory) {
        var picked by remember { mutableStateOf<TransactionCategory?>(null) }
        var text by remember { mutableStateOf("") }
        var rollNew by remember { mutableStateOf(false) }
        val taken = state.categoryBudgets.mapNotNull { it.limit.category }.toSet()
        val options = TransactionCategory.values().filter { it != TransactionCategory.UNCATEGORIZED && it !in taken }
        val amount = text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        AlertDialog(
            onDismissRequest = { addCategory = false }, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
            title = { Text("Category budget", color = Tinward.colors.textPrimary) },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ChoiceChips(options, picked, { labelFor(it) }, { picked = it })
                    TinwardTextField(text, { t -> text = t.filter { it.isDigit() || it == '.' } }, "Monthly limit (${state.currency})", numeric = true)
                    RolloverSwitch(rollNew, state.plus, onOpenPlus = { addCategory = false; onOpenPlus() }) { rollNew = it }
                }
            },
            confirmButton = { TextButton(enabled = picked != null && amount != null, onClick = { vm.setCategoryBudget(picked!!, amount!!, rollNew); addCategory = false }) { Text("Save", color = Tinward.colors.accentText) } },
            dismissButton = { TextButton(onClick = { addCategory = false }) { Text("Cancel", color = Tinward.colors.textSecondary) } },
        )
    }

    editingCategory?.let { row ->
        BudgetDialog(
            title = "${labelFor(row.limit.category!!)} budget", help = "Change the monthly limit, or remove it.",
            initial = row.limit.amount.toPlainString(), currency = state.currency, rolloverInitial = row.limit.rollover,
            plus = state.plus, onOpenPlus = { editingCategory = null; onOpenPlus() }, onDismiss = { editingCategory = null },
            onRemove = { vm.removeBudget(row.limit.id); editingCategory = null },
        ) { amount, roll -> amount?.let { vm.setCategoryBudget(row.limit.category!!, it, roll) }; editingCategory = null }
    }
}

@Composable
private fun BudgetLine(label: String, status: BudgetStatus, currency: String, note: String? = null, paused: Boolean = false) {
    val color = when { paused -> Tinward.colors.textTertiary; status.over -> Tinward.colors.negative; status.warning -> Tinward.colors.accentText; else -> Tinward.colors.accent }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Tinward.colors.textPrimary, modifier = Modifier.weight(1f))
            // May wrap onto two lines at large text sizes instead of running into the label.
            Text(
                "${formatMoney(status.spent, currencyCode = currency)} of ${formatMoney(status.limit, currencyCode = currency)}",
                style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1.4f).padding(start = Spacing.sm),
            )
        }
        Bar(status.fraction.coerceIn(0f, 1f), color)
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (paused) Tinward.colors.accentText else Tinward.colors.textTertiary) }
        Text(
            when {
                paused -> "Not being tracked while Plus is off"
                status.over -> "Over by ${formatMoney(status.remaining.abs(), currencyCode = currency)}"
                status.warning -> "Nearly there: ${formatMoney(status.remaining, currencyCode = currency)} left"
                else -> "${formatMoney(status.remaining, currencyCode = currency)} left"
            },
            style = MaterialTheme.typography.bodySmall, color = if (status.over) Tinward.colors.negative else Tinward.colors.textSecondary,
        )
    }
}

@Composable
private fun AvailableCard(state: PlanUiState) {
    val a = state.available ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    val cur = a.currency
    KoshCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Available to spend", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
            Text(
                formatMoney(a.available, currencyCode = cur), style = MaterialTheme.typography.displaySmall,
                color = if (a.available.signum() < 0) Tinward.colors.negative else Tinward.colors.textPrimary,
            )
            Text("an estimate until ${a.windowEnd.format(DateTimeFormatter.ofPattern("d MMM"))}", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
            TextButton(onClick = { open = !open }) { Text(if (open) "Hide the working" else "How this is worked out", color = Tinward.colors.accentText) }
            if (open) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    WorkLine("Tracked account balances", a.accountBalances, cur)
                    WorkLine(if (a.cardDebt.signum() < 0) "Credit on your cards" else "Credit card debt", a.cardDebt.negate(), cur)
                    WorkLine("Bills due by ${a.windowEnd.format(DateTimeFormatter.ofPattern("d MMM"))} (${a.billCount})", a.billsDue.negate(), cur)
                    WorkLine("Set aside for your goal", a.reservedForGoals.negate(), cur)
                    HorizontalDivider(color = Tinward.colors.divider)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Available", style = MaterialTheme.typography.labelLarge, color = Tinward.colors.textPrimary)
                        Text(formatMoney(a.available, currencyCode = cur), style = MaterialTheme.typography.labelLarge, color = Tinward.colors.textPrimary)
                    }
                    Text(
                        "Balances are what Tinward tracks from your records, not live bank figures. Income that has not arrived yet is not counted, and only $cur accounts are used.",
                        style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
                    )
                    if (a.accountsWithoutBalance > 0) Text(
                        "${a.accountsWithoutBalance} tracked account${if (a.accountsWithoutBalance == 1) "" else "s"} show${if (a.accountsWithoutBalance == 1) "s" else ""} ${formatMoney(BigDecimal.ZERO, currencyCode = cur)}. Set the real balance in Accounts for a better estimate.",
                        style = MaterialTheme.typography.bodySmall, color = Tinward.colors.accentText,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkLine(label: String, effect: BigDecimal, currency: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary, modifier = Modifier.weight(1f))
        Text((if (effect.signum() >= 0) "+ " else "− ") + formatMoney(effect.abs(), currencyCode = currency), style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary)
    }
}

@Composable
private fun IncomeAndRule(state: PlanUiState, onEditIncome: () -> Unit) {
    val income = state.income
    KoshCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Monthly income", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
            if (income == null) {
                Text("Not set", style = MaterialTheme.typography.displaySmall, color = Tinward.colors.textTertiary)
                Text("Set your monthly income to see how your spending compares with the 50/30/20 rule.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                Spacer(Modifier.height(Spacing.sm))
                PrimaryButton("Set monthly income", onEditIncome, Modifier.fillMaxWidth())
            } else {
                Text(formatMoney(income, currencyCode = state.currency), style = MaterialTheme.typography.displaySmall, color = Tinward.colors.textPrimary)
                val frac = (state.spent.toFloat() / income.toFloat()).coerceIn(0f, 1f)
                Bar(frac, if (state.spent > income) Tinward.colors.negative else Tinward.colors.accent)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Spent ${(state.spent.toFloat() / income.toFloat() * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textSecondary)
                    val left = income - state.spent
                    Text(
                        if (left.signum() >= 0) "Left ${formatMoney(left, currencyCode = state.currency)}" else "Over by ${formatMoney(left.abs(), currencyCode = state.currency)}",
                        style = MaterialTheme.typography.labelMedium, color = if (left.signum() >= 0) Tinward.colors.textSecondary else Tinward.colors.negative,
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
                        "${formatMoney(r.unclassified, currencyCode = state.currency)} is uncategorised or in neither group, so it only reduces what is left to save.",
                        style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
                    )
                }
            }
        }
    }
}

/** Amount entry with optional Remove. [onSave] gets null when the field is empty or invalid. */
@Composable
internal fun AmountDialog(
    title: String, help: String, initial: String, currency: String, onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null, onSave: (BigDecimal?) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val amount = text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    AlertDialog(
        onDismissRequest = onDismiss, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text(title, color = Tinward.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(help, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                TinwardTextField(text, { t -> text = t.filter { it.isDigit() || it == '.' } }, "Amount ($currency)", numeric = true)
                if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove this budget", color = Tinward.colors.negative) }
            }
        },
        confirmButton = { TextButton(enabled = amount != null, onClick = { onSave(amount) }) { Text("Save", color = Tinward.colors.accentText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Tinward.colors.textSecondary) } },
    )
}

@Composable
private fun RolloverSwitch(checked: Boolean, plus: Boolean, onOpenPlus: () -> Unit, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Spacing.sm)) {
            Text(if (plus) "Roll unspent money forward" else "Roll unspent money forward (Plus)", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary)
            Text("What you do not spend this month is added to next month's limit. Overspending never reduces it.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
        }
        androidx.compose.material3.Switch(
            checked = checked && plus, onCheckedChange = { if (plus) onChange(it) else onOpenPlus() },
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Tinward.colors.accent, checkedThumbColor = Tinward.colors.onAccent),
        )
    }
}

/** Budget amount entry with the Plus rollover option and an optional Remove. */
@Composable
private fun BudgetDialog(
    title: String, help: String, initial: String, currency: String, rolloverInitial: Boolean, plus: Boolean, onOpenPlus: () -> Unit,
    onDismiss: () -> Unit, onRemove: (() -> Unit)?, onSave: (BigDecimal?, Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var roll by remember { mutableStateOf(rolloverInitial) }
    val amount = text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    AlertDialog(
        onDismissRequest = onDismiss, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text(title, color = Tinward.colors.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(help, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                TinwardTextField(text, { t -> text = t.filter { it.isDigit() || it == '.' } }, "Amount ($currency)", numeric = true)
                RolloverSwitch(roll, plus, onOpenPlus) { roll = it }
                if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove this budget", color = Tinward.colors.negative) }
            }
        },
        confirmButton = { TextButton(enabled = amount != null, onClick = { onSave(amount, roll) }) { Text("Save", color = Tinward.colors.accentText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Tinward.colors.textSecondary) } },
    )
}
