package dev.kosh.financetracker.feature.trail

import dev.kosh.financetracker.core.model.displayMerchant
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.ui.components.CategoryIconTile
import dev.kosh.financetracker.ui.components.EmptyState
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SegmentedTabs
import dev.kosh.financetracker.ui.components.labelFor
import dev.kosh.financetracker.ui.format.formatSignedMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FILTERS = listOf(TrailFilter.ALL to "All", TrailFilter.EXPENSES to "Spent", TrailFilter.INCOME to "Income", TrailFilter.TRANSFERS to "Transfers")

@Composable
fun TrailScreen(
    onOpenTransaction: (Long) -> Unit,
    viewModel: TrailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val zone = ZoneId.systemDefault()
    var showFilters by remember { androidx.compose.runtime.mutableStateOf(false) }
    val grouped = remember(state.transactions) {
        state.transactions.groupBy { it.timestamp.atZone(zone).toLocalDate() }.toSortedMap(compareByDescending { it })
    }

    Column(modifier = Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Activity")
        Column(Modifier.padding(horizontal = Spacing.md)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search merchant, amount or account", color = Tinward.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Tinward.colors.textTertiary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Tinward.colors.surface,
                    focusedContainerColor = Tinward.colors.surface,
                    unfocusedBorderColor = Tinward.colors.divider,
                    focusedBorderColor = Tinward.colors.accent,
                    focusedTextColor = Tinward.colors.textPrimary,
                    unfocusedTextColor = Tinward.colors.textPrimary,
                    cursorColor = Tinward.colors.accent,
                ),
            )
            Spacer(Modifier.height(Spacing.sm))
            SegmentedTabs(
                options = FILTERS.map { it.second },
                selectedIndex = FILTERS.indexOfFirst { it.first == state.filter },
                onSelect = { viewModel.setFilter(FILTERS[it].first) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showFilters = true }) {
                    Text(
                        if (state.filters.activeExtras > 0) "Filters (${state.filters.activeExtras})" else "Filters",
                        color = Tinward.colors.accentText, style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    if (state.isFiltered) "${state.transactions.size} found" else "",
                    style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        if (showFilters) FilterDialog(state, viewModel) { showFilters = false }

        if (state.transactions.isEmpty()) {
            EmptyState(
                title = if (!state.isFiltered) "No transactions yet" else "No transactions found",
                description = if (!state.isFiltered) "Allow SMS access or import a statement from Home, or add one with the + button." else "Try a different search or filter.",
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.xxl)) {
                grouped.forEach { (date, dayTransactions) ->
                    item(key = "h-$date") { DateHeader(date) }
                    items(dayTransactions, key = { it.id }) { transaction ->
                        TrailRow(transaction, onClick = { onOpenTransaction(transaction.id) })
                        HorizontalDivider(color = Tinward.colors.divider, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterDialog(state: TrailUiState, viewModel: TrailViewModel, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text("Filters", color = Tinward.colors.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Period", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                dev.kosh.financetracker.ui.components.ChoiceChips(Period.values().toList(), state.filters.period, { it.label }, viewModel::setPeriod)
                if (state.accounts.isNotEmpty()) {
                    Text("Account", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                    dev.kosh.financetracker.ui.components.ChoiceChips(
                        state.accounts.filter { !it.archived }, state.accounts.firstOrNull { it.id == state.filters.accountId }, { it.name },
                        { a -> viewModel.setAccount(if (a.id == state.filters.accountId) null else a.id) },
                    )
                }
                Text("Category", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                dev.kosh.financetracker.ui.components.ChoiceChips(
                    dev.kosh.financetracker.core.model.TransactionCategory.values().filter { it != dev.kosh.financetracker.core.model.TransactionCategory.UNCATEGORIZED },
                    state.filters.category, { dev.kosh.financetracker.ui.components.labelFor(it) },
                    { c -> viewModel.setCategory(if (c == state.filters.category) null else c) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Tinward.colors.accentText) } },
        dismissButton = { TextButton(onClick = { viewModel.clearExtras() }) { Text("Clear", color = Tinward.colors.textSecondary) } },
    )
}

@Composable
private fun DateHeader(date: LocalDate) {
    val today = LocalDate.now()
    val text = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Tinward.colors.textTertiary,
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
    )
}

@Composable
private fun TrailRow(transaction: Transaction, onClick: () -> Unit) {
    val isTransfer = transaction.type == TransactionType.TRANSFER || transaction.type == TransactionType.CREDIT_CARD_PAYMENT ||
        transaction.type == TransactionType.CASH_WITHDRAWAL || transaction.type == TransactionType.ADJUSTMENT
    val isDebit = transaction.direction == TransactionDirection.DEBIT
    val merchant = transaction.displayMerchant()
    val subtitle = when {
        transaction.reconciliationNote != null ->
            if (transaction.isUnmatchedOverlap) "Unmatched \u00B7 not counted" else "Needs a decision"
        transaction.type == TransactionType.CREDIT_CARD_PAYMENT -> "Card payment"
        transaction.type == TransactionType.ADJUSTMENT -> "Balance correction"
        transaction.type == TransactionType.CASH_WITHDRAWAL -> "Cash withdrawal"
        isTransfer -> listOfNotNull("Transfer", accountLabel(transaction)).joinToString(" \u00B7 ")
        else -> listOfNotNull(
            transaction.category?.let { labelFor(it) }?.takeIf { it != "Uncategorized" },
            accountLabel(transaction),
        ).joinToString(" · ").ifBlank { "Uncategorized" }
    }
    val amountText = formatSignedMoney(transaction.amount, if (isTransfer) null else isDebit, currencyCode = transaction.currency)
    val time = transaction.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    val amountColor = when {
        isTransfer -> Tinward.colors.textTertiary
        isDebit -> Tinward.colors.textPrimary
        else -> Tinward.colors.positive
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.ms)
            .semantics(mergeDescendants = true) { contentDescription = "$merchant, $subtitle, $amountText, $time" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconTile(transaction.category, merchant, size = 40.dp)
        Spacer(Modifier.width(Spacing.ms))
        Column(modifier = Modifier.weight(1f)) {
            Text(merchant, style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(amountText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = amountColor)
            Text(time, style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary)
        }
    }
}

private fun accountLabel(t: Transaction): String? {
    val masked = t.accountSuffix?.let { s -> t.bank?.let { "$it ••$s" } ?: "••$s" }
    return masked ?: t.paymentMethod?.takeIf { it.name != "UNKNOWN" }?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
}
