package dev.kosh.financetracker.feature.review

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.ui.components.CategoryIconTile
import dev.kosh.financetracker.ui.components.EmptyState
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.REVIEW_PICKER_CATEGORIES
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.labelFor
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReviewScreen(onBack: () -> Unit, viewModel: ReviewViewModel = hiltViewModel()) {
    val queue by viewModel.queue.collectAsState()
    var skipped by remember { mutableStateOf(setOf<Long>()) }
    // The largest the queue has been this visit, so progress moves forward as items are confirmed.
    var startSize by remember { mutableStateOf(0) }
    if (queue.size > startSize) startSize = queue.size
    val current = queue.firstOrNull { it.id !in skipped }

    Column(modifier = Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Review", onBack = onBack)
        when {
            queue.isEmpty() -> EmptyState(
                title = "All caught up", description = "Every transaction has a category. Your charts and budgets are as accurate as they can be.",
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
            current == null -> Column(Modifier.padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("${queue.size} skipped", style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary)
                Text("You can come back to these any time. They stay out of your charts until they have a category.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                PrimaryButton("Go through them again", { skipped = emptySet() }, Modifier.fillMaxWidth())
            }
            else -> Column(
                Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                val done = (startSize - queue.size).coerceAtLeast(0)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${queue.size} left, across all months", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                    Text("$done done", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                }
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { if (startSize == 0) 0f else done.toFloat() / startSize },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Tinward.colors.accent, trackColor = Tinward.colors.track,
                )
                Text(
                    "Pick a category. Tinward remembers it for this merchant, so you will not be asked again.",
                    style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
                )
                ReviewCard(current, onConfirm = { viewModel.confirm(current.id, it) })
                TextButton(onClick = { skipped = skipped + current.id }, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip for now", color = Tinward.colors.textSecondary)
                }
                Spacer(Modifier.height(104.dp))   // clears the bottom bar and the + button
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(t: Transaction, onConfirm: (TransactionCategory) -> Unit) {
    var selected by remember(t.id) { mutableStateOf<TransactionCategory?>(null) }
    var showAll by remember(t.id) { mutableStateOf(false) }
    var showMessage by remember(t.id) { mutableStateOf(true) }
    val options = if (showAll) TransactionCategory.values().filter { it != TransactionCategory.UNCATEGORIZED } else REVIEW_PICKER_CATEGORIES

    KoshCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconTile(t.category, t.merchant ?: "?", size = 40.dp)
                Spacer(Modifier.width(Spacing.ms))
                Column(Modifier.weight(1f)) {
                    Text(t.merchant ?: "Unknown merchant", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary, maxLines = 1)
                    Text(
                        t.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a")),
                        style = MaterialTheme.typography.labelMedium,
                        color = Tinward.colors.textTertiary,
                    )
                }
                Text(formatMoney(t.amount, currencyCode = t.currency), style = MaterialTheme.typography.titleLarge, color = Tinward.colors.textPrimary)
            }
            if (!t.rawSourceText.isNullOrBlank()) {
                Text(
                    if (showMessage) "Hide bank message" else "Show bank message",
                    style = MaterialTheme.typography.labelLarge,
                    color = Tinward.colors.accentText,
                    modifier = Modifier.heightIn(min = 40.dp).clickable { showMessage = !showMessage }.wrapContentCenter(),
                )
                if (showMessage) {
                    Text(
                        t.rawSourceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Tinward.colors.textSecondary,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Tinward.colors.raised).padding(Spacing.ms).fillMaxWidth(),
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                options.forEach { c ->
                    val on = selected == c
                    Text(
                        labelFor(c),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) Tinward.colors.onAccent else Tinward.colors.textPrimary,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (on) Tinward.colors.accent else Tinward.colors.raised)
                            .clickable { selected = c }
                            .padding(horizontal = Spacing.md, vertical = Spacing.ms),
                    )
                }
                if (!showAll) {
                    Text(
                        "More…",
                        style = MaterialTheme.typography.labelLarge,
                        color = Tinward.colors.accentText,
                        modifier = Modifier.heightIn(min = 48.dp).clickable { showAll = true }.padding(horizontal = Spacing.md, vertical = Spacing.ms),
                    )
                }
            }
            PrimaryButton("Confirm", onClick = { selected?.let(onConfirm) }, enabled = selected != null, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun Modifier.wrapContentCenter(): Modifier = this.padding(vertical = 10.dp)
