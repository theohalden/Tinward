package dev.kosh.financetracker.feature.debt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.finance.Strategy
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.components.TinwardTextField
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DebtPlannerScreen(onBack: () -> Unit, onOpenPlus: () -> Unit, onEditAccount: (Long) -> Unit, viewModel: DebtPlannerViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsState()
    Column(Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Debt planner", onBack = onBack)
        if (!ui.plus) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Pay your cards off sooner", style = MaterialTheme.typography.headlineMedium, color = Tinward.colors.textPrimary)
                Text(
                    "See how long your credit card debt takes to clear, what it costs in interest, how much of each limit you are using month by month, and whether paying the highest-interest card first saves money. This is part of Tinward Plus.",
                    style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
                )
                PrimaryButton("See Tinward Plus", onOpenPlus, Modifier.fillMaxWidth())
            }
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (ui.cards.isEmpty()) {
                Text("No credit card debt tracked in ${ui.currency}. Cards that owe money show up here.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
            } else {
            Text("Owed on cards: ${formatMoney(ui.totalOwed, currencyCode = ui.currency)}", style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary)
            ui.cards.forEach { row ->
                KoshCard(modifier = Modifier.fillMaxWidth().clickable { onEditAccount(row.account.id) }) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.account.name, style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                            Text(formatMoney(row.owed, currencyCode = ui.currency), style = MaterialTheme.typography.titleSmall, color = Tinward.colors.negative)
                        }
                        Text(
                            row.account.apr?.let { "$it% a year" + (row.minimum?.let { m -> " \u00B7 about ${formatMoney(m, currencyCode = ui.currency)} minimum" } ?: "") } ?: "No interest rate yet. Tap to add it.",
                            style = MaterialTheme.typography.bodySmall, color = if (row.account.apr == null) Tinward.colors.accentText else Tinward.colors.textSecondary,
                        )
                        if (row.account.creditLimit != null) {
                            Text("Limit used at the end of each month", style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary)
                            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
row.history.forEach { p ->
                                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                        if (!p.tracked) Text("\u2013", style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary)
                                        p.percent?.let { Text("$it%", style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary) }
                                        if (p.tracked) Box(Modifier.fillMaxWidth().height(((p.percent ?: 0).coerceIn(2, 100) * 0.28f).dp).background(if ((p.percent ?: 0) >= 50) Tinward.colors.negative.copy(alpha = 0.7f) else Tinward.colors.accent.copy(alpha = 0.7f), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.history.forEach { p -> Text(p.month.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary) }
                            }
                            Text(if (row.history.any { !it.tracked }) "A dash means Tinward was not tracking this card yet. Under about 30% of the limit is generally considered healthy." else "Under about 30% of the limit is generally considered healthy.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
                        }
                    }
                }
            }

            if (ui.withoutApr.isNotEmpty()) Text(
                "${ui.withoutApr.joinToString { it.name }} ${if (ui.withoutApr.size == 1) "has" else "have"} no interest rate, so ${if (ui.withoutApr.size == 1) "it is" else "they are"} left out of the plan below.",
                style = MaterialTheme.typography.bodySmall, color = Tinward.colors.accentText,
            )

            if (ui.outcomes.isNotEmpty() || ui.suggestedBudget != null) {
                SectionLabel("Pay it off")
                TinwardTextField(ui.budgetText, viewModel::setBudget, "Total you can pay toward cards each month (${ui.currency})", numeric = true)
                val minimums = ui.outcomes.firstOrNull { it.strategy == Strategy.MINIMUMS }?.result
                // With one card (or when both orders give the same result) two identical strategy cards are just noise.
                val avalanche = ui.outcomes.firstOrNull { it.strategy == Strategy.AVALANCHE }
                val snowball = ui.outcomes.firstOrNull { it.strategy == Strategy.SNOWBALL }
                val merged = avalanche != null && snowball != null && avalanche.result == snowball.result
                (if (merged) ui.outcomes.filter { it.strategy != Strategy.SNOWBALL } else ui.outcomes).forEach { o ->
                    KoshCard {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(if (merged && o.strategy == Strategy.AVALANCHE) "Your payoff plan" else o.strategy.label, style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                            val months = o.result.months
                            if (months == null) Text(
                                if (o.strategy == Strategy.MINIMUMS) "Would take more than 50 years at the minimums, because most of each payment is interest." else "This payment does not clear the debt within 50 years. Try a higher amount.",
                                style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.negative,
                            )
                            else {
                                Text("Debt-free in ${years(months)} (${LocalDate.now().plusMonths(months.toLong()).format(DateTimeFormatter.ofPattern("MMM yyyy"))})", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
                                Text("Interest paid: ${formatMoney(o.result.totalInterest, currencyCode = ui.currency)}", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                                if (o.strategy != Strategy.MINIMUMS && minimums != null && minimums.months != null) {
                                    val saved = minimums.totalInterest - o.result.totalInterest
                                    if (saved.signum() > 0) Text("Saves ${formatMoney(saved, currencyCode = ui.currency)} in interest versus minimums only.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.positive)
                                }
                            }
                        }
                    }
                }
                Text(
                    "A simple model: interest is added once a month at the yearly rate divided by 12, each card gets a minimum (that month's interest plus 1% of what is owed), the rest of your budget goes to the card the strategy picks, and no new spending is assumed. Your issuer's real figures will differ.",
                    style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
                )
            }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

private fun years(months: Int): String = if (months < 12) "$months month${if (months == 1) "" else "s"}" else "${months / 12} yr ${months % 12} mo"
