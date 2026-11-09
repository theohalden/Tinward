package dev.kosh.financetracker.feature.plan

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.kosh.financetracker.core.finance.Frequency
import dev.kosh.financetracker.core.finance.Occurrence
import dev.kosh.financetracker.core.finance.OccurrenceStatus
import dev.kosh.financetracker.core.finance.Recurrence
import dev.kosh.financetracker.core.finance.RecurringItem
import dev.kosh.financetracker.core.finance.RecurringKind
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.feature.reminders.ReminderScheduler
import dev.kosh.financetracker.ui.components.ChoiceChips
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.SecondaryButton
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.components.TinwardTextField
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM")

private fun whenText(o: Occurrence, today: LocalDate): String = when (o.status) {
    OccurrenceStatus.OVERDUE -> "Overdue by ${ChronoUnit.DAYS.between(o.date, today)} day${if (ChronoUnit.DAYS.between(o.date, today) == 1L) "" else "s"} \u00B7 ${o.date.format(DAY_FORMAT)}"
    OccurrenceStatus.DUE_TODAY -> "Due today"
    OccurrenceStatus.UPCOMING -> {
        val n = ChronoUnit.DAYS.between(today, o.date)
        (if (n == 1L) "Tomorrow" else "In $n days") + " \u00B7 ${o.date.format(DAY_FORMAT)}"
    }
}

@Composable
internal fun BillsTab(state: PlanUiState, vm: PlanViewModel) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var editing by remember { mutableStateOf<RecurringItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Occurrence?>(null) }
    var notifOk by remember { mutableStateOf(ReminderScheduler.canNotify(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = ReminderScheduler.canNotify(context) }
    val wantsReminders = state.recurring.any { it.active && it.kind == RecurringKind.BILL && it.remindDaysBefore > 0 }

    if (wantsReminders && !notifOk) KoshCard(containerColor = Tinward.colors.raised) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Turn on reminders", style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary)
            Text(
                "Tinward can nudge you a few days before a bill is due. The notification just says a bill is coming up. It never shows names or amounts on your lock screen.",
                style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
            )
            PrimaryButton("Allow notifications", {
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else notifOk = ReminderScheduler.canNotify(context)
            }, Modifier.fillMaxWidth())
        }
    }

    SectionLabel("Coming up")
    if (state.pending.isEmpty()) {
        Text("Nothing due in the next 45 days. Add a bill or income schedule below.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
    } else KoshCard {
        Column {
            state.pending.forEachIndexed { i, o ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(o.item.name, style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary, maxLines = 1)
                        Text(
                            whenText(o, today) + if (o.item.kind == RecurringKind.INCOME) " \u00B7 income" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (o.status == OccurrenceStatus.OVERDUE) Tinward.colors.negative else Tinward.colors.textSecondary,
                        )
                    }
                    Text(formatMoney(o.item.amount, currencyCode = o.item.currency), style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
                    // Only the next one per schedule can be confirmed; later ones follow automatically.
                    if (state.pending.first { it.item.id == o.item.id } == o) {
                        TextButton(onClick = { settling = o }) { Text(if (o.item.kind == RecurringKind.BILL) "Mark paid" else "Mark received", color = Tinward.colors.accentText) }
                    } else androidx.compose.foundation.layout.Spacer(Modifier.padding(end = Spacing.md))
                }
                if (i != state.pending.lastIndex) HorizontalDivider(color = Tinward.colors.divider)
            }
        }
    }

    SectionLabel("All bills and income (tap to edit)")
    if (state.recurring.isEmpty()) Text("Nothing added yet.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textTertiary)
    else KoshCard {
        Column {
            state.recurring.forEachIndexed { i, item ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { editing = item }.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name + if (!item.active) " (paused)" else "", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary, maxLines = 1)
                        Text(
                            "${item.frequency.label} \u00B7 ${if (item.kind == RecurringKind.BILL) "bill" else "income"} \u00B7 next ${Recurrence.nextUnsettled(item).format(DAY_FORMAT)}",
                            style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary,
                        )
                    }
                    Text(formatMoney(item.amount, currencyCode = item.currency), style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textSecondary)
                }
                if (i != state.recurring.lastIndex) HorizontalDivider(color = Tinward.colors.divider)
            }
        }
    }
    PrimaryButton("Add a bill or income", { adding = true }, Modifier.fillMaxWidth())
    if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
        TextButton(onClick = { ReminderScheduler.sendTest(context) }) { Text("Send a test reminder (debug builds only)", color = Tinward.colors.textSecondary) }
    }
    Text(
        "A schedule is only a reminder of what to expect. It never records a payment by itself: you confirm it, or a matching payment from your bank is spotted and settles it.",
        style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
    )

    if (adding) BillDialog(null, state, onDismiss = { adding = false }, onSave = { vm.saveRecurring(it); adding = false }, onDelete = null)
    editing?.let { item ->
        BillDialog(item, state, onDismiss = { editing = null }, onSave = { vm.saveRecurring(it); editing = null }, onDelete = { vm.deleteRecurring(item.id); editing = null })
    }
    settling?.let { o ->
        val bill = o.item.kind == RecurringKind.BILL
        AlertDialog(
            onDismissRequest = { settling = null }, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
            title = { Text(if (bill) "Mark ${o.item.name} paid" else "Mark ${o.item.name} received", color = Tinward.colors.textPrimary) },
            text = {
                Text(
                    "Record it now to ${if (bill) "subtract" else "add"} ${formatMoney(o.item.amount, currencyCode = o.item.currency)} ${if (bill) "from" else "to"} your history, or just move the schedule on if it is already recorded or happened outside Tinward.",
                    style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
                )
            },
            confirmButton = { TextButton(onClick = { vm.markPaid(o, true); settling = null }) { Text("Record it", color = Tinward.colors.accentText) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.markPaid(o, false); settling = null }) { Text("Just move on", color = Tinward.colors.textSecondary) }
                    TextButton(onClick = { settling = null }) { Text("Cancel", color = Tinward.colors.textSecondary) }
                }
            },
        )
    }
}

private val REMIND_OPTIONS = listOf(0, 1, 2, 3, 7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillDialog(item: RecurringItem?, state: PlanUiState, onDismiss: () -> Unit, onSave: (RecurringItem) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var amount by remember { mutableStateOf(item?.amount?.toPlainString().orEmpty()) }
    var kind by remember { mutableStateOf(item?.kind ?: RecurringKind.BILL) }
    var frequency by remember { mutableStateOf(item?.frequency ?: Frequency.MONTHLY) }
    var due by remember { mutableStateOf(item?.let { Recurrence.nextUnsettled(it) } ?: LocalDate.now().plusDays(7)) }
    var remind by remember { mutableStateOf(item?.remindDaysBefore ?: 2) }
    var active by remember { mutableStateOf(item?.active ?: true) }
    var accountId by remember { mutableStateOf(item?.accountId) }
    var pickingDate by remember { mutableStateOf(false) }
    val value = amount.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    val accounts: List<Account> = state.accounts.filter { !it.archived && it.type != dev.kosh.financetracker.core.model.AccountType.DEBIT_CARD }

    AlertDialog(
        onDismissRequest = onDismiss, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text(if (item == null) "Add a schedule" else "Edit schedule", color = Tinward.colors.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ChoiceChips(RecurringKind.values().toList(), kind, { if (it == RecurringKind.BILL) "Bill" else "Income" }, { kind = it })
                TinwardTextField(name, { name = it.take(40) }, "Name", placeholder = if (kind == RecurringKind.BILL) "e.g. Rent, Netflix" else "e.g. Salary")
                TinwardTextField(amount, { t -> amount = t.filter { it.isDigit() || it == '.' } }, "Amount (${state.currency})", numeric = true)
                SectionLabel("Repeats")
                ChoiceChips(Frequency.values().toList(), frequency, { it.label }, { frequency = it })
                Text(
                    "Next due: ${due.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy"))}  (tap to change)",
                    style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.accentText,
                    modifier = Modifier.fillMaxWidth().clickable { pickingDate = true }.padding(vertical = Spacing.sm),
                )
                if (kind == RecurringKind.BILL) {
                    SectionLabel("Remind me")
                    ChoiceChips(REMIND_OPTIONS, remind, { if (it == 0) "Off" else if (it == 1) "1 day before" else "$it days before" }, { remind = it })
                }
                if (accounts.isNotEmpty()) {
                    SectionLabel("Paid from / into (optional)")
                    ChoiceChips(accounts, accounts.firstOrNull { it.id == accountId }, { it.name }, { accountId = if (accountId == it.id) null else it.id })
                }
                if (item != null) ChoiceChips(listOf(true, false), active, { if (it) "Active" else "Paused" }, { active = it })
                onDelete?.let { TextButton(onClick = it) { Text("Delete this schedule", color = Tinward.colors.negative) } }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && value != null, onClick = {
                onSave(
                    RecurringItem(
                        id = item?.id ?: 0, name = name.trim(), amount = value!!, currency = state.currency, kind = kind, frequency = frequency,
                        anchor = due, accountId = accountId, category = item?.category, remindDaysBefore = if (kind == RecurringKind.BILL) remind else 0,
                        active = active,
                        // Moving the due date resets what counts as already settled.
                        paidThrough = if (item != null && due == Recurrence.nextUnsettled(item)) item.paidThrough else null,
                    ),
                )
            }) { Text("Save", color = Tinward.colors.accentText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Tinward.colors.textSecondary) } },
    )

    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = due.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { due = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; pickingDate = false }) { Text("OK", color = Tinward.colors.accentText) } },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel", color = Tinward.colors.textSecondary) } },
        ) { DatePicker(state = picker) }
    }
}
