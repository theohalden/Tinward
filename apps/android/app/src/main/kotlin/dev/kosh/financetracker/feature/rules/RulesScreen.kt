package dev.kosh.financetracker.feature.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.finance.AutoRule
import dev.kosh.financetracker.core.finance.RuleDirection
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.ui.components.ChoiceChips
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SecondaryButton
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.components.TinwardTextField
import dev.kosh.financetracker.ui.components.labelFor
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val PICKABLE = TransactionCategory.values().filter { it != TransactionCategory.UNCATEGORIZED }

/** "If the merchant contains swiggy and the amount is over 500, set the category to Food." */
internal fun describe(rule: AutoRule, accounts: List<Account>): String {
    val ifs = buildList {
        rule.merchantContains?.takeIf { it.isNotBlank() }?.let { add("the merchant contains \"$it\"") }
        if (rule.amountMin != null && rule.amountMax != null) add("the amount is ${rule.amountMin.toPlainString()} to ${rule.amountMax.toPlainString()}")
        else if (rule.amountMin != null) add("the amount is at least ${rule.amountMin.toPlainString()}")
        else if (rule.amountMax != null) add("the amount is at most ${rule.amountMax.toPlainString()}")
        if (rule.direction != RuleDirection.ANY) add(if (rule.direction == RuleDirection.SPENT) "money went out" else "money came in")
        rule.accountId?.let { id -> add("it is on ${accounts.firstOrNull { it.id == id }?.name ?: "one account"}") }
    }
    val thens = buildList {
        rule.setCategory?.let { add("set the category to ${labelFor(it)}") }
        rule.setLabel?.takeIf { it.isNotBlank() }?.let { add("add the label \"$it\"") }
    }
    return "If ${ifs.joinToString(" and ")}, ${thens.joinToString(" and ")}." + if (rule.overwrite) " Replaces what is there." else " Only fills blanks (no category, or Other)."
}

@Composable
fun RulesScreen(onBack: () -> Unit, onOpenPlus: () -> Unit, viewModel: RulesViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<AutoRule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmApply by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Rules", onBack = onBack)
        if (!ui.plus) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Let Tinward tidy up for you", style = MaterialTheme.typography.headlineMedium, color = Tinward.colors.textPrimary)
                Text(
                    "Write your own rules, like \"merchant contains swiggy, set category to Food\". They label new and existing transactions, show exactly what they will do, and can be undone. Rules never move money. This is part of Tinward Plus.",
                    style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
                )
                if (ui.rules.isNotEmpty()) Text("Your ${ui.rules.size} saved rule${if (ui.rules.size == 1) " is" else "s are"} paused and kept. Nothing was deleted.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.accentText)
                PrimaryButton("See Tinward Plus", onOpenPlus, Modifier.fillMaxWidth())
            }
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                "Rules are checked from the top. The first rule that matches decides, so put the most specific one first. A rule only changes the category and your own label. It never changes amounts, dates or accounts, and never moves money.",
                style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
            )
            ui.message?.let { m ->
                Text(m, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.accentText, modifier = Modifier.clickable { viewModel.clearMessage() })
            }
            if (ui.rules.isEmpty()) {
                Text("No rules yet.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textTertiary)
            }
            ui.rules.forEachIndexed { i, rule ->
                KoshCard {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}. ${rule.name}", style = MaterialTheme.typography.titleSmall, color = if (rule.enabled) Tinward.colors.textPrimary else Tinward.colors.textTertiary, modifier = Modifier.weight(1f))
                            Switch(
                                checked = rule.enabled, onCheckedChange = { viewModel.setEnabled(rule.id, it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = Tinward.colors.accent, checkedThumbColor = Tinward.colors.onAccent),
                            )
                        }
                        Text(describe(rule, ui.accounts), style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
                        if (!rule.enabled) Text("Off: this rule does nothing.", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            SecondaryButton("Edit", { editing = rule }, Modifier.weight(1f))
                            SecondaryButton("Up", { viewModel.move(rule.id, -1) }, Modifier.weight(1f), enabled = i > 0)
                            SecondaryButton("Down", { viewModel.move(rule.id, 1) }, Modifier.weight(1f), enabled = i < ui.rules.lastIndex)
                        }
                    }
                }
            }
            PrimaryButton("New rule", { creating = true }, Modifier.fillMaxWidth())
            if (ui.rules.any { it.enabled }) {
                SecondaryButton("Apply to existing transactions", { scope.launch { confirmApply = viewModel.previewCount() } }, Modifier.fillMaxWidth())
                Text("New bank messages and imported rows are checked against these rules as they arrive. Entries you add by hand are never touched.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
            }
            if (ui.batches.isNotEmpty()) {
                SectionLabel("Recent changes")
                ui.batches.take(8).forEach { b ->
                    KoshCard {
                        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${b.changes.size} change${if (b.changes.size == 1) "" else "s"}${if (b.automatic) " on arrival" else " applied by you"}",
                                    style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary,
                                )
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(b.atMillis)) + " · " + b.changes.map { it.ruleName }.distinct().take(2).joinToString(", "),
                                    style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary,
                                )
                            }
                            TextButton(onClick = { viewModel.undo(b.batchId) }) { Text("Undo", color = Tinward.colors.accentText) }
                        }
                    }
                }
            }
        }
    }

    if (creating) RuleDialog(null, ui.accounts, viewModel, onDismiss = { creating = false }) { viewModel.save(it); creating = false }
    editing?.let { r -> RuleDialog(r, ui.accounts, viewModel, onDismiss = { editing = null }, onDelete = { viewModel.delete(r.id); editing = null }) { viewModel.save(it); editing = null } }
    confirmApply?.let { n ->
        AlertDialog(
            onDismissRequest = { confirmApply = null }, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
            title = { Text("Apply your rules?", color = Tinward.colors.textPrimary) },
            text = { Text(if (n == 0) "No existing transaction would change." else "$n transaction${if (n == 1) "" else "s"} would change. You can undo this afterwards.", color = Tinward.colors.textSecondary) },
            confirmButton = { TextButton(enabled = n > 0, onClick = { viewModel.applyToExisting(); confirmApply = null }) { Text("Apply", color = Tinward.colors.accentText) } },
            dismissButton = { TextButton(onClick = { confirmApply = null }) { Text("Cancel", color = Tinward.colors.textSecondary) } },
        )
    }
}

@Composable
private fun RuleDialog(rule: AutoRule?, accounts: List<Account>, vm: RulesViewModel, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (AutoRule) -> Unit) {
    var name by remember { mutableStateOf(rule?.name.orEmpty()) }
    var merchant by remember { mutableStateOf(rule?.merchantContains.orEmpty()) }
    var min by remember { mutableStateOf(rule?.amountMin?.toPlainString().orEmpty()) }
    var max by remember { mutableStateOf(rule?.amountMax?.toPlainString().orEmpty()) }
    var direction by remember { mutableStateOf(rule?.direction ?: RuleDirection.ANY) }
    var accountId by remember { mutableStateOf(rule?.accountId) }
    var category by remember { mutableStateOf(rule?.setCategory) }
    var label by remember { mutableStateOf(rule?.setLabel.orEmpty()) }
    var overwrite by remember { mutableStateOf(rule?.overwrite ?: false) }
    fun build() = AutoRule(
        id = rule?.id ?: 0, name = name.trim(), enabled = rule?.enabled ?: true, merchantContains = merchant.trim().ifBlank { null },
        amountMin = min.toBigDecimalOrNull(), amountMax = max.toBigDecimalOrNull(), direction = direction, accountId = accountId,
        setCategory = category, setLabel = label.trim().ifBlank { null }, overwrite = overwrite,
    )
    val draft = build()
    val valid = name.isNotBlank() && draft.hasCondition && draft.hasAction
    var matches by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(merchant, min, max, direction, accountId) { matches = if (draft.hasCondition) vm.matchCount(draft) else null }

    AlertDialog(
        onDismissRequest = onDismiss, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text(if (rule == null) "New rule" else "Edit rule", color = Tinward.colors.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TinwardTextField(name, { name = it.take(40) }, "Rule name", placeholder = "e.g. Food delivery")
                SectionLabel("If")
                TinwardTextField(merchant, { merchant = it.take(40) }, "Merchant contains", placeholder = "e.g. swiggy")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TinwardTextField(min, { v -> min = v.filter { it.isDigit() || it == '.' } }, "Amount from", numeric = true, modifier = Modifier.weight(1f))
                    TinwardTextField(max, { v -> max = v.filter { it.isDigit() || it == '.' } }, "Amount to", numeric = true, modifier = Modifier.weight(1f))
                }
                ChoiceChips(RuleDirection.values().toList(), direction, { it.label }, { direction = it })
                if (accounts.isNotEmpty()) {
                    Text("Account (optional)", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                    ChoiceChips(accounts, accounts.firstOrNull { it.id == accountId }, { it.name }, { a -> accountId = if (a.id == accountId) null else a.id })
                }
                Text(
                    when {
                        !draft.hasCondition -> "Add at least one condition."
                        matches == null -> ""
                        else -> "Matches ${matches} transaction${if (matches == 1) "" else "s"} today."
                    },
                    style = MaterialTheme.typography.labelMedium, color = Tinward.colors.accentText,
                )
                SectionLabel("Then")
                Text("Category", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                ChoiceChips(PICKABLE, category, { labelFor(it) }, { c -> category = if (c == category) null else c })
                TinwardTextField(label, { label = it.take(30) }, "Your own label (optional)", placeholder = "e.g. Eating out")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = Spacing.sm)) {
                        Text("Replace what is already set", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary)
                        Text(if (overwrite) "Overrides a category or label that is already there." else "Off: only fills blanks (no category, or Other), so your own choices are kept.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
                    }
                    Switch(checked = overwrite, onCheckedChange = { overwrite = it }, colors = SwitchDefaults.colors(checkedTrackColor = Tinward.colors.accent, checkedThumbColor = Tinward.colors.onAccent))
                }
                if (valid) Text(describe(draft, accounts), style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete rule", color = Tinward.colors.negative) }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(build()) }) { Text("Save", color = Tinward.colors.accentText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Tinward.colors.textSecondary) } },
    )
}
