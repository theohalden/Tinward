package dev.kosh.financetracker.feature.deleted

import dev.kosh.financetracker.core.model.displayMerchant
import androidx.compose.foundation.background
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.data.repository.TransactionRepository
import dev.kosh.financetracker.ui.components.EmptyState
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.format.formatSignedMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class DeletedViewModel @Inject constructor(private val repository: TransactionRepository) : ViewModel() {
    val items: StateFlow<List<Transaction>?> = repository.observeDeleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(t: Transaction) { viewModelScope.launch { repository.restore(listOf(t.id)) } }
    fun emptyAll() { viewModelScope.launch { repository.purgeDeleted() } }
}

@Composable
fun DeletedScreen(onBack: () -> Unit, viewModel: DeletedViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsState()
    var confirmEmpty by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Recently deleted", onBack = onBack, actions = {
            if (!items.isNullOrEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Empty", color = Tinward.colors.negative, style = MaterialTheme.typography.labelLarge) }
        })
        val list = items
        if (list != null && list.isEmpty()) {
            EmptyState("Nothing deleted", "Transactions you delete wait here until you restore them or empty this list.", Modifier.padding(horizontal = Spacing.lg))
        } else if (list != null) LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.ms),
        ) {
            item { Text("Deleted entries are left out of every total. Imported ones stay here so a rescan does not bring them back.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary) }
            items(list, key = { it.id }) { t ->
                KoshCard {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.displayMerchant(), style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary, maxLines = 1)
                            Text(
                                formatSignedMoney(t.amount, t.direction == TransactionDirection.DEBIT, currencyCode = t.currency) + " · " +
                                    t.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                                style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary,
                            )
                        }
                        TextButton(onClick = { viewModel.restore(t) }) { Text("Restore", color = Tinward.colors.accentText) }
                    }
                }
            }
        }
    }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false }, tonalElevation = 0.dp, containerColor = Tinward.colors.surface,
        title = { Text("Delete these for good?", color = Tinward.colors.textPrimary) },
        text = { Text("This cannot be undone. Imported bank messages deleted here may be picked up again by the next scan.", color = Tinward.colors.textSecondary) },
        confirmButton = { TextButton(onClick = { confirmEmpty = false; viewModel.emptyAll() }) { Text("Delete for good", color = Tinward.colors.negative) } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel", color = Tinward.colors.textSecondary) } },
    )
}
