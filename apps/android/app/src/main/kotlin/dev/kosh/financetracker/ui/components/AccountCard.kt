package dev.kosh.financetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kosh.financetracker.core.finance.AccountSummary
import dev.kosh.financetracker.ui.format.formatRupees
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward

/**
 * Per-account month activity, only what the SMS parser recognised (bank name and
 * masked last digits). Flat editorial card; no fabricated card art or balances.
 */
@Composable
fun AccountCard(summary: AccountSummary, amountsHidden: Boolean) {
    KoshCard(modifier = Modifier.width(248.dp)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    summary.bank ?: "Account",
                    style = MaterialTheme.typography.titleSmall,
                    color = Tinward.colors.textPrimary,
                )
                Text(
                    "••${summary.accountSuffix}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Tinward.colors.textTertiary,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Out", style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary)
                    Text(
                        formatRupees(summary.monthDebit, amountsHidden),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Tinward.colors.textPrimary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("In", style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary)
                    Text(
                        formatRupees(summary.monthCredit, amountsHidden),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Tinward.colors.positive,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "${summary.debitCount} out · ${summary.creditCount} in this month",
                style = MaterialTheme.typography.labelSmall,
                color = Tinward.colors.textTertiary,
            )
        }
    }
}
