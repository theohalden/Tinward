package dev.kosh.financetracker.feature.importcsv

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.csv.PlannedRow
import dev.kosh.financetracker.core.csv.RowStatus
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.parser.MoneyNumbers
import dev.kosh.financetracker.ui.components.ChoiceChips
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SecondaryButton
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.time.format.DateTimeFormatter

@Composable
fun ImportCsvScreen(onBack: () -> Unit, viewModel: ImportCsvViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            viewModel.load(uri, name)
        }
    }
    val header = ui.header
    val m = ui.mapping
    val columnOptions: List<Int?> = listOf<Int?>(null) + header.indices.map { it }
    fun colLabel(i: Int?) = if (i == null) "None" else header.getOrNull(i)?.ifBlank { null }?.take(24) ?: "Column ${i + 1}"

    Column(Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Import a CSV file", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                "Works with the statement a bank lets you download as CSV. The file stays on this phone: nothing is uploaded. Tinward shows what it understood before saving anything.",
                style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
            )
            SecondaryButton(if (ui.fileName == null) "Choose a file" else "Choose a different file", { picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
            ui.fileName?.let { Text("${it} \u00B7 ${ui.table?.rows?.size?.minus(ui.headerRow + 1) ?: 0} rows", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary) }
            ui.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.negative) }

            if (ui.table != null) {
                SectionLabel("Which account is this?")
                if (ui.accounts.isEmpty()) Text("Add an account first, then come back.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.negative)
                else ChoiceChips(ui.accounts, ui.account, { it.name }, { viewModel.selectAccount(it.id) })

                SectionLabel("Which column is which?")
                Text("Date", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                ChoiceChips(columnOptions, m.date, ::colLabel, { c -> viewModel.setMapping { copy(date = c) } })
                Text("Description", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                ChoiceChips(columnOptions, m.description, ::colLabel, { c -> viewModel.setMapping { copy(description = c) } })
                Text("Amounts are in", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                ChoiceChips(listOf(false, true), m.split, { if (it) "Separate debit and credit columns" else "One amount column" }, { split ->
                    viewModel.setMapping { if (split) copy(amount = null) else copy(debit = null, credit = null) }
                })
                if (m.split) {
                    Text("Money out (debit)", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                    ChoiceChips(columnOptions, m.debit, ::colLabel, { c -> viewModel.setMapping { copy(debit = c) } })
                    Text("Money in (credit)", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                    ChoiceChips(columnOptions, m.credit, ::colLabel, { c -> viewModel.setMapping { copy(credit = c) } })
                } else {
                    Text("Amount", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                    ChoiceChips(columnOptions, m.amount, ::colLabel, { c -> viewModel.setMapping { copy(amount = c) } })
                    Text("Which sign means money going out?", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                    ChoiceChips(listOf(false, true), ui.invertSign, { if (it) "Positive (card statements)" else "Negative (most banks)" }, viewModel::setInvert)
                }
                Text("Balance (optional)", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                ChoiceChips(columnOptions, m.balance, ::colLabel, { c -> viewModel.setMapping { copy(balance = c) } })

                SectionLabel("How are dates and numbers written?")
                ui.dateDetection?.let { det ->
                    ChoiceChips(det.alternatives.ifEmpty { listOfNotNull(ui.dateFormat) }, ui.dateFormat, { it.example }, viewModel::setDateFormat)
                    if (det.ambiguous) Text(
                        "These dates could be read two ways (day first or month first). Check the preview below and pick the right one.",
                        style = MaterialTheme.typography.bodySmall, color = Tinward.colors.accentText,
                    )
                }
                ChoiceChips(MoneyNumbers.Decimal.values().toList(), ui.decimal, { it.label }, viewModel::setDecimal)

                val plan = ui.plan
                if (plan == null) {
                    Text("Pick at least the date and amount columns to see a preview.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                } else {
                    SectionLabel("Preview")
                    val cur = ui.account?.currency ?: "INR"
                    KoshCard {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text("${plan.newRows.size} new \u00B7 ${plan.duplicates} already in your history \u00B7 ${plan.errors} can't be read", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                            if (plan.newRows.isNotEmpty()) {
                                Text("Money out ${formatMoney(plan.moneyOut, currencyCode = cur)} \u00B7 money in ${formatMoney(plan.moneyIn, currencyCode = cur)}", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                                Text("${plan.firstDate?.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} to ${plan.lastDate?.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
                            }
                        }
                    }
                    KoshCard {
                        Column {
                            val shown = plan.rows.sortedBy { if (it.status == RowStatus.ERROR) 0 else 1 }.take(12)
                            shown.forEachIndexed { i, r -> PreviewRow(r, cur); if (i != shown.lastIndex) HorizontalDivider(color = Tinward.colors.divider) }
                            if (plan.rows.size > shown.size) Text("and ${plan.rows.size - shown.size} more", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary, modifier = Modifier.padding(Spacing.md))
                        }
                    }
                    Text(
                        "Imported rows are marked as coming from a file you mapped, not as bank-verified. A row already in your history (same amount and direction, within a day) is skipped. Credits are not counted as income until you say so.",
                        style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
                    )
                }
            }
            ui.result?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = Tinward.colors.positive) }
            Spacer(Modifier.height(Spacing.md))
        }
        if (ui.result != null) PrimaryButton("Done", onBack, Modifier.fillMaxWidth().padding(Spacing.md).windowInsetsPadding(WindowInsets.navigationBars))
        else PrimaryButton(
            if (ui.working) "Working\u2026" else "Import ${ui.plan?.newRows?.size ?: 0} transactions", viewModel::import, enabled = ui.canImport,
            modifier = Modifier.fillMaxWidth().padding(Spacing.md).windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}

@Composable
private fun PreviewRow(r: PlannedRow, currency: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.description.ifBlank { "(no description)" }, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary, maxLines = 1)
            Text(
                listOfNotNull(r.date?.format(DateTimeFormatter.ofPattern("d MMM yyyy")), when (r.status) {
                    RowStatus.NEW -> "New"; RowStatus.DUPLICATE -> "Already there \u00B7 skipped"; RowStatus.ERROR -> "Line ${r.line}: ${r.problem}"
                }).joinToString(" \u00B7 "),
                style = MaterialTheme.typography.bodySmall,
                color = when (r.status) { RowStatus.ERROR -> Tinward.colors.negative; RowStatus.DUPLICATE -> Tinward.colors.textTertiary; else -> Tinward.colors.textSecondary },
            )
        }
        r.amount?.let {
            Text(
                (if (r.direction == TransactionDirection.DEBIT) "\u2212" else "+") + formatMoney(it, currencyCode = currency),
                style = MaterialTheme.typography.bodyMedium, color = if (r.direction == TransactionDirection.DEBIT) Tinward.colors.textPrimary else Tinward.colors.positive,
            )
        }
    }
}
