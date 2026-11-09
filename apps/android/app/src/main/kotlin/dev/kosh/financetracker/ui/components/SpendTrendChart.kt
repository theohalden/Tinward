package dev.kosh.financetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.kosh.financetracker.core.finance.DaySpend
import java.math.BigDecimal

/**
 * Daily spend bars for a full month — real per-day totals only, no interpolation.
 * A day with no spending draws as a flat baseline, which is the honest reading
 * (not "no data", it's "₹0 that day").
 */
@Composable
fun SpendTrendChart(
    days: List<DaySpend>,
    color: Color,
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 160.dp,
    highlightPeak: Boolean = false,
    peakColor: Color = color,
) {
    val maxAmount = days.maxOfOrNull { it.amount } ?: BigDecimal.ZERO

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
            .semantics {
                contentDescription = if (days.size >= 2) {
                    "Daily spending for the month, ${days.size} days of data"
                } else {
                    "Not enough daily data yet to show a trend"
                }
            },
    ) {
        if (days.isEmpty() || maxAmount <= BigDecimal.ZERO) return@Canvas

        val barCount = days.size
        val gap = if (barCount > 20) 1.dp.toPx() else 3.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)
        val maxBarHeight = size.height

        days.forEachIndexed { index, day ->
            val fraction = (day.amount.toFloat() / maxAmount.toFloat()).coerceIn(0.02f, 1f)
            val barHeight = maxBarHeight * fraction
            val x = index * (barWidth + gap)
            val isPeak = highlightPeak && day.amount == maxAmount
            drawRoundRect(
                color = if (highlightPeak && !isPeak) color.copy(alpha = 0.55f) else if (isPeak) peakColor else color,
                topLeft = Offset(x, maxBarHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

/**
 * Daily bars with a readable frame: day-of-month under the chart, and the busiest day called out in words.
 * Real daily totals only; days with nothing spent are flat.
 */
@Composable
fun DailySpendChart(
    days: List<DaySpend>,
    color: Color,
    currencyCode: String,
    hidden: Boolean = false,
    chartHeight: androidx.compose.ui.unit.Dp = 140.dp,
    modifier: Modifier = Modifier,
) {
    val peak = days.maxByOrNull { it.amount }?.takeIf { it.amount.signum() > 0 }
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        peak?.let {
            androidx.compose.material3.Text(
                "Busiest day: " + it.date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")) + " \u00B7 " + dev.kosh.financetracker.ui.format.compactMoney(it.amount, currencyCode, hidden),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = dev.kosh.financetracker.ui.theme.Tinward.colors.textSecondary,
            )
        }
        SpendTrendChart(days, color, chartHeight = chartHeight, highlightPeak = true, peakColor = dev.kosh.financetracker.ui.theme.Tinward.colors.accentText)
        if (days.size >= 2) {
            // Evenly spaced day numbers under the bars (first, a few between, last).
            val ticks = listOf(0, days.size / 4, days.size / 2, days.size * 3 / 4, days.size - 1).distinct()
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                ticks.forEach { i ->
                    androidx.compose.material3.Text(
                        days[i].date.dayOfMonth.toString(),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = dev.kosh.financetracker.ui.theme.Tinward.colors.textTertiary,
                    )
                }
            }
        }
    }
}
