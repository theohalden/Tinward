package dev.kosh.financetracker.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kosh.financetracker.feature.accounts.COMMON_CURRENCIES
import dev.kosh.financetracker.ui.components.ChoiceChips
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward

/** Choose the currency that Home, Insights and Plan total in. Accounts keep their own currency. */
@Composable
fun CurrencyRow(current: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = runCatching { java.util.Currency.getInstance(current).displayName }.getOrDefault(current)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open = true }.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Main currency", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
            Text("Home and Insights total in this. Other currencies stay on their own accounts.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
        }
        Text("$current \u00B7 $name", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false }, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text("Main currency", color = Tinward.colors.textPrimary) },
        text = { ChoiceChips((COMMON_CURRENCIES + current).distinct(), current, { it }, { onChange(it); open = false }) },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Close", color = Tinward.colors.accentText) } },
    )
}

/** A bare "$" in a message could be several currencies; the user says which one their bank means. */
@Composable
fun DollarRow(current: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open = true }.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Dollar sign on messages", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
            Text("When a bank text says just \"$\", Tinward records it in this currency.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
        }
        Text(current, style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false }, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text("What does \"$\" mean?", color = Tinward.colors.textPrimary) },
        text = { ChoiceChips(dev.kosh.financetracker.core.parser.CurrencyResolution.DOLLAR_CURRENCIES, current, { it }, { onChange(it); open = false }) },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Close", color = Tinward.colors.accentText) } },
    )
}
