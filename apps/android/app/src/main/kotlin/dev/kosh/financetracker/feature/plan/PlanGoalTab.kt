package dev.kosh.financetracker.feature.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kosh.financetracker.core.finance.SavingsGoal
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.SecondaryButton
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.components.TinwardTextField
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
internal fun GoalTab(state: PlanUiState, vm: PlanViewModel, onOpenPlus: () -> Unit) {
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var creating by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Pair<SavingsGoal, Boolean>?>(null) }   // second = true to add, false to take out
    val cur = state.currency
    val active = state.goals.filter { !it.archived }
    val workable = state.workableGoals
    val paused = active.filter { g -> workable.none { it.id == g.id } }

    if (active.isEmpty()) {
        KoshCard {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("A savings goal", style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary)
                Text(
                    "Pick something to save for and Tinward tracks your progress. The money you set aside is taken out of available to spend, so you do not spend it by accident.",
                    style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
                )
                PrimaryButton("Start a goal", { creating = true }, Modifier.fillMaxWidth())
            }
        }
    }

    workable.forEach { goal ->
        KoshCard {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(goal.name, style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary)
                Text(formatMoney(goal.saved, currencyCode = goal.currency), style = MaterialTheme.typography.displaySmall, color = Tinward.colors.textPrimary)
                Bar(goal.fraction, Tinward.colors.positive)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(goal.fraction * 100).toInt()}% of ${formatMoney(goal.target, currencyCode = goal.currency)}", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textSecondary)
                    Text("${formatMoney(goal.remaining, currencyCode = goal.currency)} to go", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textSecondary)
                }
                goal.targetDate?.let { d ->
                    Text("Target ${d.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
                    goal.monthlyNeeded(LocalDate.now())?.let {
                        Text("About ${formatMoney(it, currencyCode = goal.currency)} a month gets you there.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.accentText)
                    }
                }
                if (goal.saved >= goal.target) Text("Goal reached.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.positive)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                    PrimaryButton("Add money", { moving = goal to true }, Modifier.weight(1f))
                    SecondaryButton("Take out", { moving = goal to false }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecondaryButton("Edit", { editing = goal }, Modifier.weight(1f))
                    SecondaryButton("Archive", { vm.archiveGoal(goal.id, true) }, Modifier.weight(1f))
                }
            }
        }
    }

    paused.forEach { goal ->
        KoshCard(containerColor = Tinward.colors.raised) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(goal.name, style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                Text("${formatMoney(goal.saved, currencyCode = goal.currency)} of ${formatMoney(goal.target, currencyCode = goal.currency)} set aside", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                Text("Paused: more than one goal needs Tinward Plus. Your goal and savings note are kept, and still held back from available to spend.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.accentText)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecondaryButton("See Plus", onOpenPlus, Modifier.weight(1f))
                    SecondaryButton("Archive", { vm.archiveGoal(goal.id, true) }, Modifier.weight(1f))
                }
            }
        }
    }

    if (active.isNotEmpty()) {
        if (state.plus) PrimaryButton("Add another goal", { creating = true }, Modifier.fillMaxWidth())
        else SecondaryButton("Save for more than one thing with Plus", onOpenPlus, Modifier.fillMaxWidth())
    }
    Text(
        "Adding money here is a note to yourself. It does not move money between accounts; it just stops Tinward counting it as spendable.",
        style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
    )

    val archived = state.goals.filter { it.archived }
    if (archived.isNotEmpty()) {
        SectionLabel("Archived")
        KoshCard {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                archived.forEach { g ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(g.name, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                        Text("${formatMoney(g.saved, currencyCode = g.currency)} of ${formatMoney(g.target, currencyCode = g.currency)}", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textTertiary)
                    }
                }
            }
        }
    }

    if (creating) GoalDialog(null, cur, onDismiss = { creating = false }) { vm.saveGoal(it); creating = false }
    editing?.let { g -> GoalDialog(g, cur, onDismiss = { editing = null }) { vm.saveGoal(it); editing = null } }
    moving?.let { (goal, add) ->
        AmountDialog(
            title = if (add) "Add to ${goal.name}" else "Take out of ${goal.name}",
            help = if (add) "How much you are setting aside now." else "How much you are releasing back to spendable money.",
            initial = "", currency = cur, onDismiss = { moving = null },
        ) { amount -> amount?.let { vm.contribute(goal.id, if (add) it else it.negate()) }; moving = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDialog(goal: SavingsGoal?, currency: String, onDismiss: () -> Unit, onSave: (SavingsGoal) -> Unit) {
    var name by remember { mutableStateOf(goal?.name.orEmpty()) }
    var target by remember { mutableStateOf(goal?.target?.toPlainString().orEmpty()) }
    var start by remember { mutableStateOf(goal?.saved?.toPlainString() ?: "") }
    var date by remember { mutableStateOf(goal?.targetDate) }
    var pickingDate by remember { mutableStateOf(false) }
    val t = target.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    AlertDialog(
        onDismissRequest = onDismiss, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text(if (goal == null) "Start a goal" else "Edit goal", color = Tinward.colors.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TinwardTextField(name, { name = it.take(40) }, "What are you saving for?", placeholder = "e.g. Emergency fund")
                TinwardTextField(target, { v -> target = v.filter { it.isDigit() || it == '.' } }, "Target ($currency)", numeric = true)
                if (goal == null) TinwardTextField(start, { v -> start = v.filter { it.isDigit() || it == '.' } }, "Already set aside (optional)", numeric = true)
                Text(
                    date?.let { "Target date: ${it.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}" } ?: "Add a target date (optional)",
                    style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.accentText,
                    modifier = Modifier.fillMaxWidth().clickable { pickingDate = true }.padding(vertical = Spacing.sm),
                )
                if (date != null) TextButton(onClick = { date = null }) { Text("Clear date", color = Tinward.colors.textSecondary) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && t != null, onClick = {
                onSave(
                    (goal ?: SavingsGoal(name = "", target = BigDecimal.ONE, currency = currency)).copy(
                        name = name.trim(), target = t!!, targetDate = date,
                        saved = goal?.saved ?: (start.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: BigDecimal.ZERO),
                    ),
                )
            }) { Text("Save", color = Tinward.colors.accentText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Tinward.colors.textSecondary) } },
    )
    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now().plusMonths(6)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { date = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; pickingDate = false }) { Text("OK", color = Tinward.colors.accentText) } },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel", color = Tinward.colors.textSecondary) } },
        ) { DatePicker(state = picker) }
    }
}
