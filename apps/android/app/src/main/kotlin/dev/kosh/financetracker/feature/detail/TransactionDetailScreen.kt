package dev.kosh.financetracker.feature.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.finance.ConfirmedClassification
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.core.model.displayLabel
import dev.kosh.financetracker.core.parser.ParserFields
import dev.kosh.financetracker.ui.components.CategoryIconTile
import dev.kosh.financetracker.ui.components.FieldRow
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SecondaryButton
import dev.kosh.financetracker.ui.components.REVIEW_PICKER_CATEGORIES
import dev.kosh.financetracker.ui.components.labelFor
import dev.kosh.financetracker.ui.format.formatSignedMoney
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.core.model.countsAsSpending
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val transaction by viewModel.transaction.collectAsState()
    val extras by viewModel.extras.collectAsState()
    val message by viewModel.message.collectAsState()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var refunding by rememberSaveable { mutableStateOf(false) }
    var editingCategory by rememberSaveable { mutableStateOf(false) }
    var showStory by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Transaction", onBack = onBack)
        val current = transaction
        if (current != null) Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Hero(current)

            KoshCard {
                val account = extras.accountName ?: current.accountSuffix?.let { "${current.bank ?: "Account"} ••$it" } ?: "Unknown"
                FieldRow("Type", current.type.displayLabel())
                Divider()
                FieldRow("Category", labelFor(current.category ?: TransactionCategory.UNCATEGORIZED))
                Divider()
                FieldRow("Account", account)
                ParserFields.cardSuffix(current.rawSourceText.orEmpty())?.let { Divider(); FieldRow("Card", "••$it") }
                Divider()
                FieldRow("Source", when (current.source) { dev.kosh.financetracker.core.model.TransactionSource.SMS -> "Bank SMS"; dev.kosh.financetracker.core.model.TransactionSource.STATEMENT -> "Bank statement"; dev.kosh.financetracker.core.model.TransactionSource.MANUAL -> "Added by you"; else -> "Notification" })
                current.reconciliationNote?.let { Divider(); FieldRow("Reconciliation", it) }
                current.customLabel?.let { Divider(); FieldRow("Your label", it) }
                current.notes?.takeIf { current.source == dev.kosh.financetracker.core.model.TransactionSource.MANUAL || current.refundOfId != null || it.isNotBlank() }?.let { n -> Divider(); FieldRow("Note", n) }
                if (current.splits.isNotEmpty()) {
                    Divider()
                    FieldRow("Split", current.splits.joinToString(", ") { "${labelFor(it.category)} ${formatMoney(it.amount, currencyCode = current.currency)}" })
                }
                extras.refundOfName?.let { Divider(); FieldRow("Refund of", it) }
                if (extras.refunded.signum() > 0) { Divider(); FieldRow("Refunded", formatMoney(extras.refunded, currencyCode = current.currency)) }
            }
            message?.let {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.accentText, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = viewModel::clearMessage) { Text("Dismiss", color = Tinward.colors.textSecondary) }
                }
            }
            SecondaryButton("Edit details", onClick = { onEdit(current.id) }, modifier = Modifier.fillMaxWidth())
            if (current.direction == TransactionDirection.DEBIT && current.type.countsAsSpending()) {
                SecondaryButton("Record a refund", onClick = { refunding = true }, modifier = Modifier.fillMaxWidth())
            }

            SecondaryButton(if (editingCategory) "Close" else "Change category", onClick = { editingCategory = !editingCategory }, modifier = Modifier.fillMaxWidth())
            if (editingCategory) {
                KoshCard {
                    REVIEW_PICKER_CATEGORIES.forEachIndexed { i, category ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.setCategory(category); editingCategory = false }
                                .padding(horizontal = Spacing.md, vertical = Spacing.ms),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIconTile(category, labelFor(category), size = 32.dp)
                            Spacer(Modifier.width(Spacing.ms))
                            Text(labelFor(category), style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
                        }
                        if (i != REVIEW_PICKER_CATEGORIES.lastIndex) Divider()
                    }
                }
            }

            KoshCard(modifier = Modifier.clickable { showStory = !showStory }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Why it is here", style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary)
                    Text(if (showStory) "⌃" else "›", style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textTertiary)
                }
                if (showStory) {
                    Column(Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Story(current)
                    }
                }
            }
            SecondaryButton("Delete", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (confirmDelete) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmDelete = false }, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text("Delete this transaction?", color = Tinward.colors.textPrimary) },
        text = { Text("It leaves your totals now. You can undo right after, or restore it later from Settings \u2192 Recently deleted.", color = Tinward.colors.textSecondary) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) { Text("Delete", color = Tinward.colors.negative) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Tinward.colors.textSecondary) } },
    )
    val shown = transaction
    if (refunding && shown != null) RefundDialog(shown, extras.refunded, onDismiss = { refunding = false }) { amount, note -> refunding = false; viewModel.refund(amount, note) }
}

@Composable
private fun RefundDialog(t: Transaction, alreadyRefunded: java.math.BigDecimal, onDismiss: () -> Unit, onConfirm: (java.math.BigDecimal, String?) -> Unit) {
    val left = t.amount - alreadyRefunded
    var amountText by rememberSaveable { mutableStateOf(left.toPlainString()) }
    var note by rememberSaveable { mutableStateOf("") }
    val amount = amountText.toBigDecimalOrNull()?.takeIf { it.signum() > 0 && it <= left }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text("Record a refund", color = Tinward.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Up to ${formatMoney(left, currencyCode = t.currency)} can still be refunded. It reverses the spending in its category and is not counted as income.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
                dev.kosh.financetracker.ui.components.TinwardTextField(amountText, { v -> amountText = v.filter { it.isDigit() || it == '.' } }, "Refund amount", numeric = true)
                dev.kosh.financetracker.ui.components.TinwardTextField(note, { note = it.take(100) }, "Note (optional)")
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(enabled = amount != null, onClick = { onConfirm(amount!!, note.trim().ifBlank { null }) }) { Text("Record", color = Tinward.colors.accentText) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = Tinward.colors.textSecondary) } },
    )
}

@Composable
private fun Divider() = HorizontalDivider(color = Tinward.colors.divider, thickness = 1.dp)

@Composable
private fun Hero(t: Transaction) {
    val isDebit = t.direction == TransactionDirection.DEBIT
    val neutral = t.type == TransactionType.CREDIT_CARD_PAYMENT || t.type == TransactionType.TRANSFER ||
        t.type == TransactionType.CASH_WITHDRAWAL || t.type == TransactionType.ADJUSTMENT
    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
        CategoryIconTile(t.category, t.merchant ?: "?", size = 56.dp)
        Spacer(Modifier.height(Spacing.sm))
        Text(t.merchant ?: "Unknown", style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary, textAlign = TextAlign.Center)
        Text(
            formatSignedMoney(t.amount, if (neutral) null else isDebit, currencyCode = t.currency),
            style = MaterialTheme.typography.displaySmall,
            color = when {
                neutral -> Tinward.colors.textSecondary
                isDebit -> Tinward.colors.textPrimary
                else -> Tinward.colors.positive
            },
        )
        Text(
            t.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a")),
            style = MaterialTheme.typography.labelMedium,
            color = Tinward.colors.textTertiary,
        )
    }
}

@Composable
private fun Story(t: Transaction) {
    StoryStep("Message captured", t.rawSourceText ?: "No source message stored for this entry.")
    StoryStep(
        "Identified as ${t.type.displayLabel().lowercase()}",
        if (ConfirmedClassification.type(t) != null) {
            t.notes.orEmpty()
        } else when (t.type) {
            TransactionType.CREDIT_CARD_PURCHASE -> "The issuer confirms a completed card purchase. It counts as spending on the purchase date."
            TransactionType.CREDIT_CARD_PAYMENT -> "This settles a credit card balance. It is neither new spending nor income; the purchase is counted separately."
            TransactionType.TRANSFER -> "Classified as a transfer and excluded from spending and income."
            TransactionType.CASH_WITHDRAWAL -> "Money moved from a bank account to cash. It is not spending until you spend the cash."
            TransactionType.ADJUSTMENT -> "You corrected the tracked balance to match your real one. It is neither income nor spending."
            TransactionType.UNKNOWN -> "The purpose of this movement is unresolved. A credit alone does not establish income."
            else -> "Classified from the stored transaction and available source evidence."
        },
    )
    StoryStep(
        "Categorised as ${labelFor(t.category ?: TransactionCategory.UNCATEGORIZED)}",
        if (t.merchant != null) "Matched from the merchant name (\"${t.merchant}\"). You can change this anytime."
        else "No merchant name was found in the message, so this could not be auto-categorised.",
    )
}

@Composable
private fun StoryStep(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
        Spacer(Modifier.height(Spacing.xs))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
    }
}
