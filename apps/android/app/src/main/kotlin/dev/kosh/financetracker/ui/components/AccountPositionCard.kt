package dev.kosh.financetracker.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kosh.financetracker.core.finance.AccountPosition
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.math.BigDecimal

/** A real account or card with its tracked position. Flat, no card art, no live-balance claims. */
@Composable
fun AccountPositionCard(p: AccountPosition, hidden: Boolean, onClick: () -> Unit) {
    val a = p.account
    KoshCard(modifier = Modifier.width(248.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(Spacing.md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(a.name, style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                Text(a.last4?.takeIf { !a.name.contains(it) }?.let { "\u2022\u2022$it" } ?: if (a.last4 != null) "" else a.type.label, style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
            }
            Spacer(Modifier.height(Spacing.sm))
            when (a.type) {
                AccountType.CREDIT_CARD -> {
                    val owed = p.amount ?: BigDecimal.ZERO
                    Text(if (owed.signum() < 0) "In credit" else "Owed", style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary)
                    Text(formatMoney(owed.abs(), hidden, a.currency), style = MaterialTheme.typography.titleMedium, color = if (owed.signum() > 0) Tinward.colors.negative else Tinward.colors.positive)
                    p.available?.let { Text("${formatMoney(it, hidden, a.currency)} available", style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary) }
                }
                AccountType.DEBIT_CARD -> {
                    Text("Spends from linked account", style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary)
                    Text(p.amount?.let { formatMoney(it, hidden, a.currency) } ?: "Not linked", style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary)
                }
                else -> {
                    Text("Tracked balance", style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary)
                    Text(formatMoney(p.amount ?: BigDecimal.ZERO, hidden, a.currency), style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary)
                    if (a.openingBalance.signum() == 0 && p.amount?.signum() == 0) Text("Set your balance", style = MaterialTheme.typography.labelSmall, color = Tinward.colors.accentText)
                }
            }
        }
    }
}
