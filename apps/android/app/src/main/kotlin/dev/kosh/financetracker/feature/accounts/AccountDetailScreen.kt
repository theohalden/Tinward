package dev.kosh.financetracker.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.ui.components.ChoiceChips
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SecondaryButton
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.components.SegmentedTabs
import dev.kosh.financetracker.ui.components.TinwardTextField
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private sealed interface Sheet {
    object None : Sheet
    object Move : Sheet
    object Pay : Sheet
    object Adjust : Sheet
}

@Composable
fun AccountDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsState()
    var sheet by remember { mutableStateOf<Sheet>(Sheet.None) }
    val p = ui.position
    Column(Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader(p?.account?.name ?: "Account", onBack = onBack, actions = {
            if (p != null) TextButton(onClick = { onEdit(p.account.id) }) { Text("Edit", color = Tinward.colors.accentText, style = MaterialTheme.typography.labelLarge) }
        })
        if (p == null) {
            if (ui.loaded) Text("This account no longer exists.", Modifier.padding(Spacing.md), color = Tinward.colors.textSecondary)
        } else {
        val a = p.account
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                KoshCard {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            when (a.type) { AccountType.CREDIT_CARD -> "Amount owed"; AccountType.DEBIT_CARD -> "Linked account balance"; else -> "Tracked balance" },
                            style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary,
                        )
                        Text(
                            p.amount?.let { formatMoney(it.abs(), currencyCode = a.currency) + if (a.isLiability && it.signum() < 0) " credit" else "" } ?: "Not linked",
                            style = MaterialTheme.typography.displaySmall,
                            color = if (a.type == AccountType.CREDIT_CARD) Tinward.colors.negative else Tinward.colors.textPrimary,
                        )
                        p.available?.let {
                            Text("${formatMoney(it, currencyCode = a.currency)} available \u00B7 ${p.utilizationPercent}% of limit used", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                        }
                        if (a.type == AccountType.CREDIT_CARD) {
                            val dates = listOfNotNull(a.statementDay?.let { "Statement on the $it${suffixOf(it)}" }, a.dueDay?.let { "payment due on the $it${suffixOf(it)}" })
                            if (dates.isNotEmpty()) Text(dates.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
                        }
                        Text(
                            "Calculated from the opening amount and recorded transactions, so it is an estimate, not a bank-verified figure.",
                            style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
                        )
                    }
                }
            }
            ui.message?.let { msg ->
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.accentText, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearMessage) { Text("Dismiss", color = Tinward.colors.textSecondary) }
                    }
                }
            }
            if (a.type != AccountType.DEBIT_CARD && !a.archived) item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SecondaryButton(if (a.type == AccountType.CREDIT_CARD) "Pay this card" else "Move money", { sheet = if (a.type == AccountType.CREDIT_CARD) Sheet.Pay else Sheet.Move }, Modifier.weight(1f))
                        SecondaryButton("Correct balance", { sheet = Sheet.Adjust }, Modifier.weight(1f))
                    }
                }
            }
            item { SectionLabel("History") }
            if (ui.transactions.isEmpty()) item { Text("Nothing recorded for this account yet.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary) }
            else item {
                KoshCard {
                    Column {
                        ui.transactions.take(50).forEachIndexed { i, t ->
                            TxRow(t, onClick = { onOpenTransaction(t.id) })
                            if (i < minOf(ui.transactions.size, 50) - 1) HorizontalDivider(color = Tinward.colors.divider)
                        }
                    }
                }
            }
            item {
                SecondaryButton(if (a.archived) "Restore this account" else "Archive this account", { viewModel.setArchived(!a.archived) }, Modifier.fillMaxWidth())
                if (!a.archived) Text("Archiving hides it from totals but keeps its history.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary, modifier = Modifier.padding(top = Spacing.xs))
            }
        }
        }
    }

    when (sheet) {
        Sheet.None -> Unit
        Sheet.Move, Sheet.Pay -> if (p != null) MoveDialog(p.account, ui.others, pay = sheet == Sheet.Pay, onDismiss = { sheet = Sheet.None }) { otherId, outgoing, amount ->
            viewModel.transfer(otherId, outgoing, amount, null); sheet = Sheet.None
        }
        Sheet.Adjust -> if (p?.amount != null) AdjustDialog(p.account, p.amount, onDismiss = { sheet = Sheet.None }) { actual -> viewModel.reconcile(actual); sheet = Sheet.None }
    }
}

@Composable
private fun TxRow(t: Transaction, onClick: () -> Unit) {
    val debit = t.direction == TransactionDirection.DEBIT
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.merchant ?: "Unknown", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary, maxLines = 1)
            Text(t.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy")), style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
        }
        Text(
            (if (debit) "\u2212" else "+") + formatMoney(t.amount, currencyCode = t.currency),
            style = MaterialTheme.typography.bodyLarge,
            color = if (debit) Tinward.colors.textPrimary else Tinward.colors.positive,
        )
    }
}

@Composable
private fun MoveDialog(account: Account, others: List<Account>, pay: Boolean, onDismiss: () -> Unit, onConfirm: (Long, Boolean, BigDecimal) -> Unit) {
    val candidates = if (pay) others.filter { it.type != AccountType.CREDIT_CARD } else others
    var outgoing by remember { mutableStateOf(!pay) }
    var other by remember { mutableStateOf<Account?>(null) }
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    val mismatch = other != null && other!!.currency != account.currency
    AlertDialog(
        onDismissRequest = onDismiss, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text(if (pay) "Pay ${account.name}" else "Move money", color = Tinward.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (!pay) SegmentedTabs(listOf("Out to", "In from"), if (outgoing) 0 else 1, { outgoing = it == 0 })
                if (candidates.isEmpty()) Text("Add another account first.", color = Tinward.colors.negative)
                else ChoiceChips(candidates, other, { it.name }, { other = it })
                TinwardTextField(amountText, { v -> amountText = v.filter { it.isDigit() || it == '.' } }, "Amount (${account.currency})", numeric = true)
                if (mismatch) Text("These accounts use different currencies. Tinward does not convert between them.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.negative)
                Text(
                    if (pay) "Lowers the card's balance owed and the paying account. It is not counted as spending." else "Recorded as a transfer, so it is not counted as income or spending.",
                    style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = other != null && amount != null && !mismatch, onClick = { onConfirm(other!!.id, outgoing, amount!!) }) { Text("Record", color = Tinward.colors.accentText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Tinward.colors.textSecondary) } },
    )
}

@Composable
private fun AdjustDialog(account: Account, tracked: BigDecimal, onDismiss: () -> Unit, onConfirm: (BigDecimal) -> Unit) {
    var text by remember { mutableStateOf("") }
    val actual = text.toBigDecimalOrNull()
    AlertDialog(
        onDismissRequest = onDismiss, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text("Correct balance", color = Tinward.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Tinward tracks ${formatMoney(tracked, currencyCode = account.currency)}. Enter ${if (account.isLiability) "what you really owe" else "what your bank really shows"} and Tinward records the difference as an adjustment, not as income or spending.",
                    style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
                )
                TinwardTextField(text, { v -> text = v.filter { it.isDigit() || it == '.' || it == '-' } }, "Actual amount (${account.currency})", numeric = true)
            }
        },
        confirmButton = { TextButton(enabled = actual != null, onClick = { onConfirm(actual!!) }) { Text("Adjust", color = Tinward.colors.accentText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Tinward.colors.textSecondary) } },
    )
}
