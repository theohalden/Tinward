package dev.kosh.financetracker.feature.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.finance.EventKind
import dev.kosh.financetracker.core.finance.MoneyTimeline
import dev.kosh.financetracker.ui.components.ChoiceChips
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.ScreenHeader
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

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
fun TimelineScreen(onBack: () -> Unit, onOpenPlus: () -> Unit, viewModel: TimelineViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsState()
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Money Timeline", onBack = onBack)
        val t = ui.timeline
        if (!ui.plus) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("See where your balance is heading", style = MaterialTheme.typography.headlineMedium, color = Tinward.colors.textPrimary)
                Text(
                    "Money Timeline projects your tracked balances over 30, 60 or 90 days from the bills and income you have added, and shows the lowest point and every assumption behind it. It is part of Tinward Plus.",
                    style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
                )
                PrimaryButton("See Tinward Plus", onOpenPlus, Modifier.fillMaxWidth())
            }
        } else if (t != null) Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ChoiceChips(listOf(30, 60, 90), ui.days, { "$it days" }, viewModel::setDays)
            Summary(t, ui.baseline)
            KoshCard { Box2(t) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = Spacing.sm)) {
                    Text("Include everyday spending", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
                    Text(
                        if (ui.everydayAvailable) "Adds the average of your recent spending that was not a bill." else "Needs a few weeks of recorded spending first.",
                        style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary,
                    )
                }
                Switch(
                    checked = ui.everyday, onCheckedChange = viewModel::setEveryday, enabled = ui.everydayAvailable,
                    colors = SwitchDefaults.colors(checkedTrackColor = Tinward.colors.accent, checkedThumbColor = Tinward.colors.onAccent),
                )
            }

            SectionLabel("What's coming")
            if (t.events.isEmpty()) Text("Nothing scheduled in this window. Add bills and income under Plan \u2192 Bills.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
            else KoshCard {
                Column {
                    t.events.forEachIndexed { i, e ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.label + if (e.kind == EventKind.WHAT_IF) " (what if)" else "", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary, maxLines = 1)
                                Text(e.date.format(DAY), style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text((if (e.amount.signum() >= 0) "+" else "\u2212") + formatMoney(e.amount.abs(), currencyCode = t.currency), style = MaterialTheme.typography.bodyLarge, color = if (e.amount.signum() >= 0) Tinward.colors.positive else Tinward.colors.textPrimary)
                                Text("balance ${formatMoney(t.balanceAfter(e), currencyCode = t.currency)}", style = MaterialTheme.typography.labelSmall, color = if (t.balanceAfter(e).signum() < 0) Tinward.colors.negative else Tinward.colors.textTertiary)
                            }
                        }
                        if (i != t.events.lastIndex) HorizontalDivider(color = Tinward.colors.divider)
                    }
                }
            }

            SectionLabel("Try a what-if")
            ui.whatIfs.forEachIndexed { i, w ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${w.label} \u00B7 ${w.date.format(DAY)} \u00B7 ${if (w.amount.signum() >= 0) "+" else "\u2212"}${formatMoney(w.amount.abs(), currencyCode = t.currency)}", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.removeWhatIf(i) }) { Text("Remove", color = Tinward.colors.textSecondary) }
                }
            }
            SecondaryButton("Add a one-off purchase or income", { adding = true }, Modifier.fillMaxWidth())
            Text("What-ifs only change this forecast. They are never saved as transactions.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)

            SectionLabel("How this was made")
            KoshCard {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("As of ${t.asOf.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} \u00B7 ${t.currency}", style = MaterialTheme.typography.labelLarge, color = Tinward.colors.textPrimary)
                    Text("Accounts: ${t.includedAccounts.joinToString(", ").ifBlank { "none yet" }}", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
                    t.assumptions.forEach { Text("\u2022 $it", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary) }
                    Text("A forecast, not a promise: real spending and income will differ.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
    if (adding) WhatIfDialog(ui.timeline?.currency ?: "INR", onDismiss = { adding = false }) { d, l, a -> viewModel.addWhatIf(d, l, a); adding = false }
}

@Composable
private fun Summary(t: MoneyTimeline, baseline: MoneyTimeline?) {
    val low = t.lowest; val short = t.firstShortfall
    KoshCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("In ${t.days} days", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
            Text(formatMoney(t.end.balance, currencyCode = t.currency), style = MaterialTheme.typography.displaySmall, color = if (t.end.balance.signum() < 0) Tinward.colors.negative else Tinward.colors.textPrimary)
            Text("Lowest point ${formatMoney(low.balance, currencyCode = t.currency)} on ${low.date.format(DAY)}", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
            if (short != null) Text("Your tracked balance would go below zero on ${short.date.format(DAY)}.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.negative)
            if (baseline != null) {
                val delta = t.end.balance - baseline.end.balance
                Text("The what-ifs change the end balance by ${if (delta.signum() >= 0) "+" else "\u2212"}${formatMoney(delta.abs(), currencyCode = t.currency)}.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.accentText)
            }
        }
    }
}

@Composable
private fun Box2(t: MoneyTimeline) {
    val line = Tinward.colors.accent
    val zero = Tinward.colors.negative
    val track = Tinward.colors.divider
    val min = t.points.minOf { it.balance }.min(BigDecimal.ZERO).toFloat()
    val max = t.points.maxOf { it.balance }.max(BigDecimal.ZERO).toFloat()
    val span = (max - min).takeIf { it > 0f } ?: 1f
    Canvas(Modifier.fillMaxWidth().height(160.dp).padding(Spacing.md).semantics { contentDescription = "Line chart of expected balance over ${t.days} days. Lowest ${t.lowest.balance} on ${t.lowest.date}." }) {
        fun y(v: Float) = size.height - (v - min) / span * size.height
        drawLine(track, Offset(0f, y(0f)), Offset(size.width, y(0f)), strokeWidth = 1.dp.toPx())
        val path = Path()
        t.points.forEachIndexed { i, p ->
            val x = i.toFloat() / (t.points.size - 1).coerceAtLeast(1) * size.width
            if (i == 0) path.moveTo(x, y(p.balance.toFloat())) else path.lineTo(x, y(p.balance.toFloat()))
        }
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx()))
        if (min < 0f) drawLine(zero.copy(alpha = 0.35f), Offset(0f, y(0f)), Offset(size.width, y(0f)), strokeWidth = 1.5.dp.toPx())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatIfDialog(currency: String, onDismiss: () -> Unit, onSave: (LocalDate, String, BigDecimal) -> Unit) {
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var spend by remember { mutableStateOf(true) }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(14)) }
    var picking by remember { mutableStateOf(false) }
    val value = amount.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    AlertDialog(
        onDismissRequest = onDismiss, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text("Add a what-if", color = Tinward.colors.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ChoiceChips(listOf(true, false), spend, { if (it) "Spend" else "Receive" }, { spend = it })
                TinwardTextField(label, { label = it.take(30) }, "What is it?", placeholder = "e.g. New laptop")
                TinwardTextField(amount, { t -> amount = t.filter { it.isDigit() || it == '.' } }, "Amount ($currency)", numeric = true)
                Text("On ${date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy"))}  (tap to change)", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.accentText, modifier = Modifier.fillMaxWidth().clickableNoRipple { picking = true }.padding(vertical = Spacing.sm))
            }
        },
        confirmButton = { TextButton(enabled = value != null, onClick = { onSave(date, label, if (spend) value!!.negate() else value!!) }) { Text("Add", color = Tinward.colors.accentText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Tinward.colors.textSecondary) } },
    )
    if (picking) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { date = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; picking = false }) { Text("OK", color = Tinward.colors.accentText) } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel", color = Tinward.colors.textSecondary) } },
        ) { DatePicker(state = st) }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable { onClick() }
