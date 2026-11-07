package dev.kosh.financetracker.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.ui.components.EmptyState
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AccountsScreen(viewModel: AccountsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    Column(Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Accounts")
        if (state.accounts.isEmpty()) {
            EmptyState(
                title = "No accounts yet",
                description = "Accounts appear once Tinward reads a bank SMS or statement that names one.",
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        } else LazyColumn(
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.ms),
        ) {
            state.totalKnownBalance?.let { total ->
                item {
                    KoshCard(containerColor = Tinward.colors.raised) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text("Total known balance", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                            Text(formatMoney(total), style = MaterialTheme.typography.displaySmall, color = Tinward.colors.textPrimary)
                            Text(
                                "Across ${state.knownBalances.size} of ${state.accounts.size} accounts that reported a balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = Tinward.colors.textTertiary,
                            )
                        }
                    }
                }
            }
            items(state.accounts, key = { "${it.bank}-${it.suffix}" }) { a ->
                KoshCard {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(a.bank ?: "Account", style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary)
                            Text("••${a.suffix}", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                        }
                        if (a.balance != null) {
                            Text(formatMoney(a.balance), style = MaterialTheme.typography.headlineMedium, color = Tinward.colors.textPrimary)
                            a.balanceAsOf?.let {
                                Text(
                                    "As of ${it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Tinward.colors.textTertiary,
                                )
                            }
                        } else {
                            Text("Balance unknown", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textTertiary)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("This month in ${formatMoney(a.monthIn)}", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.positive)
                            Text("out ${formatMoney(a.monthOut)}", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
