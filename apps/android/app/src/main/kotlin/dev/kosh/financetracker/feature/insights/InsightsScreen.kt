package dev.kosh.financetracker.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.finance.CategorySlice
import dev.kosh.financetracker.ui.components.CategoryIconTile
import dev.kosh.financetracker.ui.components.colorFor
import dev.kosh.financetracker.ui.components.EmptyState
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.MonthSelectorPill
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.components.SpendTrendChart
import dev.kosh.financetracker.ui.components.labelFor
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    onOpenPlus: () -> Unit,
    onOpenReview: (() -> Unit)? = null,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Insights", onBack = onBack) {
            if (state.hasAnyData) MonthSelectorPill(state.selectedMonth, state.availableMonths, state.monthsWithData, viewModel::selectMonth)
        }
        if (!state.hasAnyData) {
            EmptyState(
                title = "No spending yet",
                description = "Once Tinward reads some bank SMS, your spending trend and category breakdown will show up here.",
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        } else LazyColumn(
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                KoshCard {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Spent this month", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary, modifier = Modifier.weight(1f))
                            // A month still in progress is not comparable with a whole one; the pace chart below does that fairly.
                            if (state.selectedMonth != java.time.YearMonth.now()) state.percentChangeVsPreviousMonth?.let { PercentChange(it) }
                        }
                        if (state.categoryBreakdown.isEmpty()) {
                            Text(formatMoney(state.monthSummary.expense), style = MaterialTheme.typography.displaySmall, color = Tinward.colors.textPrimary)
                            Text("No categorised spending this month yet.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textTertiary)
                        } else {
                            Donut(state.categoryBreakdown, formatMoney(state.monthSummary.expense))
                            val unsorted = state.categoryBreakdown.firstOrNull { it.category == dev.kosh.financetracker.core.model.TransactionCategory.UNCATEGORIZED }
                            if (unsorted != null && unsorted.fraction >= 0.3f && onOpenReview != null) {
                                Text(
                                    "${(unsorted.fraction * 100).toInt()}% of this month is not categorised yet, so the chart is mostly grey. Review a few to sharpen it.",
                                    style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary,
                                )
                                TextButton(onClick = onOpenReview) { Text("Review transactions", color = Tinward.colors.accentText) }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Last 6 months") }
            item { TrendCard(state) }
            state.comparison?.let { c -> item { ComparisonCard(c, state.currency, state.selectedMonth) } }
            state.stand?.let { st -> item { StandingCard(st, state.currency) } }
            item { SectionLabel("Financial Review") }
            item {
                val review = state.review
                if (state.plus && review != null) ReviewCards(review, state.currency) else ReviewTeaser(onOpenPlus)
            }
            if (state.categoryBreakdown.isNotEmpty()) {
                item { SectionLabel("Where it went") }
                item {
                    KoshCard {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.ms)) {
                            state.categoryBreakdown.forEach { CategoryRow(it) }
                        }
                    }
                }
            }
            item { SectionLabel("Spending pace") }
            item {
                KoshCard {
                    Box(Modifier.padding(Spacing.md)) {
                        dev.kosh.financetracker.ui.components.SpendPaceChart(days = state.dailySpend, previousDays = state.previousDailySpend, month = state.selectedMonth, currency = state.currency, hidden = false, chartHeight = 190.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Donut(slices: List<CategorySlice>, centreText: String) {
    val colors = slices.map { colorFor(it.category) }
    val track = Tinward.colors.track
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(180.dp)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            slices.forEachIndexed { i, s ->
                val sweep = (s.fraction * 360f).coerceAtLeast(0f)
                if (sweep > 0.5f) drawArc(colors[i], start, (sweep - 1.5f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centreText, style = MaterialTheme.typography.titleLarge, color = Tinward.colors.textPrimary)
            Text("spent", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
        }
    }
}

@Composable
private fun PercentChange(change: Float) {
    val up = change > 0f
    val color = if (up) Tinward.colors.negative else Tinward.colors.positive
    Text(
        "${if (up) "▲" else "▼"} ${abs((change * 100).roundToInt())}% vs last month",
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

@Composable
private fun CategoryRow(slice: CategorySlice) {
    val color = colorFor(slice.category)
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CategoryIconTile(slice.category, labelFor(slice.category), size = 32.dp)
            Spacer(Modifier.width(Spacing.ms))
            Text(labelFor(slice.category), style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary, modifier = Modifier.weight(1f))
            Text("${(slice.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
            Spacer(Modifier.width(Spacing.sm))
            Text(formatMoney(slice.amount), style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary)
        }
        Spacer(Modifier.height(Spacing.xs))
        Box(Modifier.fillMaxWidth().height(4.dp).background(Tinward.colors.track, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(slice.fraction.coerceIn(0f, 1f)).height(4.dp).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun TrendCard(state: InsightsUiState) {
    val hasData = state.trend.any { it.hasActivity }
    KoshCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (!hasData) {
                Text("No spending or income in these months yet.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
            } else {
                dev.kosh.financetracker.ui.components.MonthlyTrendChart(state.trend, state.selectedMonth, state.currency, chartHeight = 170.dp)
                state.averageSpent?.let { Text("Average spent in months with activity: ${formatMoney(it, currencyCode = state.currency)}", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary) }
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: androidx.compose.ui.graphics.Color) {
    // No bar at all for a month with nothing in it, so an empty month reads as empty.
    if (fraction > 0f) Box(Modifier.width(10.dp).fillMaxHeight(fraction.coerceIn(0.02f, 1f)).background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
    else Box(Modifier.width(10.dp))
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary)
    }
}

@Composable
private fun ComparisonCard(c: dev.kosh.financetracker.core.finance.MonthComparison, currency: String, month: java.time.YearMonth) {
    val prev = month.minusMonths(1).month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
    KoshCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Compared with $prev", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
            CompareRow("Spent", c.spent, c.previousSpent, c.spentChangePercent, currency, upIsBad = true)
            CompareRow("Income", c.income, c.previousIncome, c.incomeChangePercent, currency, upIsBad = false)
            if (month == java.time.YearMonth.now()) Text(
                "This month is still in progress, so it looks lower than a whole month. See the spending pace below for a like-for-like view.",
                style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun CompareRow(label: String, now: java.math.BigDecimal, before: java.math.BigDecimal, pct: Int?, currency: String, upIsBad: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
            Text("was ${formatMoney(before, currencyCode = currency)}", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(now, currencyCode = currency), style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
            Text(
                pct?.let { "${if (it > 0) "\u25B2" else if (it < 0) "\u25BC" else "\u2013"} ${abs(it)}%" } ?: "no earlier month to compare",
                style = MaterialTheme.typography.labelSmall,
                color = when { pct == null || pct == 0 -> Tinward.colors.textTertiary; (pct > 0) == upIsBad -> Tinward.colors.negative; else -> Tinward.colors.positive },
            )
        }
    }
}

@Composable
private fun StandingCard(st: Standing, currency: String) {
    KoshCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Where you stand today", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
            Text(formatMoney(st.net, currencyCode = currency), style = MaterialTheme.typography.displaySmall, color = if (st.net.signum() < 0) Tinward.colors.negative else Tinward.colors.textPrimary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Tracked accounts", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                Text(formatMoney(st.assets, currencyCode = currency), style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (st.cardDebt.signum() < 0) "Card credit" else "Card debt", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                Text((if (st.cardDebt.signum() > 0) "\u2212 " else "+ ") + formatMoney(st.cardDebt.abs(), currencyCode = currency), style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary)
            }
            Text(
                "Calculated from your records, so an estimate, not a live bank balance." +
                    if (st.accountsWithoutBalance > 0) " ${st.accountsWithoutBalance} account${if (st.accountsWithoutBalance == 1) " shows" else "s show"} zero; set the real balance in Accounts." else "",
                style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun ReviewTeaser(onOpenPlus: () -> Unit) {
    KoshCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPlus)) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("See what changed and why", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
            Text("Top merchants, which categories rose or fell, how this month is pacing against last, your savings rate and how much of your income is already committed to bills. Part of Tinward Plus.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
            Text("See Tinward Plus", style = MaterialTheme.typography.labelLarge, color = Tinward.colors.accentText)
        }
    }
}

@Composable
private fun ReviewCards(r: dev.kosh.financetracker.core.finance.ReviewReport, currency: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        r.pace?.let { p ->
            KoshCard {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Pace this month", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                    Text("${formatMoney(p.spentToDate, currencyCode = currency)} spent by day ${p.dayOfMonth}", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
                    Text("Same days last month: ${formatMoney(p.lastMonthByThisDay, currencyCode = currency)}", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                    Text(
                        p.projectedMonthEnd?.let { "At this pace the month ends near ${formatMoney(it, currencyCode = currency)}." } ?: "Too early in the month to project the end.",
                        style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
                    )
                }
            }
        }
        if (r.savingsRatePercent != null || r.fixedObligationPercent != null) KoshCard {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Your income, split", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                r.savingsRatePercent?.let { Text("Kept after spending: $it%", style = MaterialTheme.typography.bodyLarge, color = if (it < 0) Tinward.colors.negative else Tinward.colors.textPrimary) }
                r.fixedObligationPercent?.let { Text("Already committed to bills: $it% (${formatMoney(r.monthlyBills, currencyCode = currency)} a month)", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary) }
                Text("Uses your planned income, or what you actually received if it was higher.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
            }
        }
        if (r.topMerchants.isNotEmpty()) KoshCard {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Top places you spent", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                if (r.unnamedSpend.signum() > 0) Text("${formatMoney(r.unnamedSpend, currencyCode = currency)} more is in transactions with no merchant name, so it is not ranked.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
                r.topMerchants.forEach { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(m.merchant, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary, maxLines = 1)
                            Text(
                                "${m.count} time${if (m.count == 1) "" else "s"}" + (m.changePercent?.let { " \u00B7 ${if (it >= 0) "up" else "down"} ${abs(it)}% on last month" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
                            )
                        }
                        Text(formatMoney(m.amount, currencyCode = currency), style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary)
                    }
                }
            }
        }
        if (r.risers.isNotEmpty() || r.fallers.isNotEmpty()) KoshCard {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("What changed since last month", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                (r.risers + r.fallers).forEach { c ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(labelFor(c.category), style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(
                            (if (c.delta.signum() > 0) "\u25B2 " else "\u25BC ") + formatMoney(c.delta.abs(), currencyCode = currency),
                            style = MaterialTheme.typography.bodyMedium, color = if (c.delta.signum() > 0) Tinward.colors.negative else Tinward.colors.positive,
                        )
                    }
                }
            }
        }
    }
}
