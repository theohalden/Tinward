package dev.kosh.financetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kosh.financetracker.core.finance.DaySpend
import dev.kosh.financetracker.core.finance.MonthPoint
import dev.kosh.financetracker.core.finance.SpendPace
import dev.kosh.financetracker.ui.format.compactMoney
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** One line on a [LineAreaChart]. A null value means "no point here", so the line simply stops (for example, the future). */
data class ChartSeries(
    val values: List<Float?>,
    val color: Color,
    val fill: Boolean = false,
    val dashed: Boolean = false,
)

/** Rounds a maximum up to a tidy axis value (1, 2, 2.5, 5 times a power of ten). */
internal fun niceMax(v: Float): Float {
    if (v <= 0f) return 1f
    val p = 10f.pow(kotlin.math.floor(kotlin.math.log10(v)))
    val n = v / p
    val step = when { n <= 1f -> 1f; n <= 2f -> 2f; n <= 2.5f -> 2.5f; n <= 5f -> 5f; else -> 10f }
    return step * p
}

/**
 * Smooth line chart with a soft gradient under the main line, faint gridlines with amounts, day or month
 * labels, an end-of-line marker, and touch-and-drag to read any point (the caller shows the reading).
 */
@Composable
fun LineAreaChart(
    series: List<ChartSeries>,
    xCount: Int,
    xLabels: Map<Int, String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    yLabel: (Float) -> String,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
) {
    val measurer = rememberTextMeasurer()
    val grid = Tinward.colors.divider
    val axisText = Tinward.colors.textTertiary
    val label = TextStyle(fontSize = 10.sp, color = axisText)
    val background = Tinward.colors.surface
    val maxY = niceMax(series.flatMap { it.values }.filterNotNull().maxOrNull() ?: 0f)

    Canvas(
        modifier
            .fillMaxWidth().height(height)
            .semantics { contentDescription = description }
            .pointerInput(xCount) {
                awaitEachGesture {
                    fun pick(x: Float) = ((x / size.width) * (xCount - 1)).roundToInt().coerceIn(0, (xCount - 1).coerceAtLeast(0))
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onSelect(pick(down.position.x))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        onSelect(pick(change.position.x))
                    }
                }
            },
    ) {
        val bottomPad = 20.dp.toPx(); val topPad = 16.dp.toPx()
        val w = size.width; val h = size.height - bottomPad - topPad
        fun x(i: Int) = if (xCount <= 1) w / 2f else w * i / (xCount - 1)
        fun y(v: Float) = topPad + h * (1f - (v / maxY).coerceIn(0f, 1f))

        // Gridlines at 0, half and max, labelled just above the line at the left edge.
        listOf(0f, 0.5f, 1f).forEach { f ->
            val gy = y(maxY * f)
            drawLine(grid.copy(alpha = if (f == 0f) 0.9f else 0.45f), Offset(0f, gy), Offset(w, gy), 1.dp.toPx(),
                pathEffect = if (f == 0f) null else PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            if (f > 0f) {
                val t = measurer.measure(yLabel(maxY * f), label)
                drawText(t, topLeft = Offset(0f, gy - t.size.height - 1.dp.toPx()))
            }
        }
        // X labels along the bottom; first left-aligned, last right-aligned, others centred.
        xLabels.forEach { (i, text) ->
            val t = measurer.measure(text, label)
            val cx = x(i)
            val left = when (i) { 0 -> 0f; xCount - 1 -> w - t.size.width; else -> (cx - t.size.width / 2f).coerceIn(0f, w - t.size.width) }
            drawText(t, topLeft = Offset(left, size.height - t.size.height))
        }

        series.forEach { s ->
            val pts = s.values.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), y(it)) } }
            if (pts.size < 2) {
                pts.firstOrNull()?.let { drawCircle(s.color, 4.dp.toPx(), it) }
                return@forEach
            }
            val line = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (k in 1 until pts.size) {
                    val a = pts[k - 1]; val b = pts[k]; val mx = (a.x + b.x) / 2f
                    cubicTo(mx, a.y, mx, b.y, b.x, b.y)   // flat tangents at each point: smooth, and never overshoots the data
                }
            }
            if (s.fill) {
                val area = Path().apply {
                    addPath(line); lineTo(pts.last().x, y(0f)); lineTo(pts.first().x, y(0f)); close()
                }
                drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.32f), s.color.copy(alpha = 0f)), startY = topPad, endY = y(0f)))
            }
            drawPath(
                line, s.color,
                style = Stroke(
                    width = (if (s.dashed) 1.6.dp else 2.6.dp).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 9f)) else null,
                ),
            )
            if (!s.dashed) {
                val end = pts.last()
                drawCircle(s.color.copy(alpha = 0.25f), 9.dp.toPx(), end)
                drawCircle(s.color, 4.5.dp.toPx(), end)
                drawCircle(background, 1.8.dp.toPx(), end)
            }
        }

        selected?.takeIf { it in 0 until xCount }?.let { i ->
            val sx = x(i)
            drawLine(axisText.copy(alpha = 0.6f), Offset(sx, topPad), Offset(sx, y(0f)), 1.dp.toPx())
            series.forEach { s ->
                s.values.getOrNull(i)?.let { v ->
                    drawCircle(background, 6.dp.toPx(), Offset(sx, y(v)))
                    drawCircle(s.color, 4.dp.toPx(), Offset(sx, y(v)))
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dashed) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(3) { Box(Modifier.size(width = 4.dp, height = 2.dp).background(color)) } }
        else Box(Modifier.size(width = 14.dp, height = 3.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary)
    }
}

/**
 * "How is this month going?": what has been spent so far, against the same point last month.
 * Drag across the chart to read any day; the figures above follow your finger.
 */
@Composable
fun SpendPaceChart(
    days: List<DaySpend>,
    previousDays: List<DaySpend>,
    month: YearMonth,
    currency: String,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 150.dp,
    today: LocalDate = LocalDate.now(),
) {
    val current = remember(days, month, today) { SpendPace.cumulative(days, month, today) }
    val previous = remember(previousDays, month, today) { SpendPace.cumulative(previousDays, month.minusMonths(1), today) }
    var picked by remember(month) { mutableStateOf<Int?>(null) }
    val monthLength = month.lengthOfMonth()

    if (current.isEmpty() || current.last().signum() <= 0) {
        Text("Nothing spent yet this month.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary, modifier = modifier)
        return
    }
    val day = (picked ?: current.lastIndex).coerceIn(0, current.lastIndex) + 1
    val now = SpendPace.at(current, day) ?: BigDecimal.ZERO
    val before = SpendPace.at(previous, day)
    val pct = SpendPace.percentVs(now, before)
    val dateText = month.atDay(day).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(if (picked == null) "Spent so far" else "Spent by $dateText", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(formatMoney(now, hidden, currency), style = MaterialTheme.typography.headlineSmall, color = Tinward.colors.textPrimary)
            if (pct != null && !hidden) Text(
                (if (pct > 0) "▲ " else if (pct < 0) "▼ " else "") + abs(pct) + "% vs last month",
                style = MaterialTheme.typography.labelMedium,
                color = if (pct > 0) Tinward.colors.negative else Tinward.colors.positive,
            )
        }
        if (before != null && !hidden) Text(
            "Last month by this day: " + formatMoney(before, false, currency),
            style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
        )
        val cur = List(monthLength) { i -> current.getOrNull(i)?.toFloat() }
        val prev = List(monthLength) { i -> previous.getOrNull(i)?.toFloat() }
        LineAreaChart(
            series = listOfNotNull(
                if (previous.isNotEmpty()) ChartSeries(prev, Tinward.colors.textTertiary, dashed = true) else null,
                ChartSeries(cur, Tinward.colors.accent, fill = true),
            ),
            xCount = monthLength,
            xLabels = listOf(0, 7, 14, 21, monthLength - 1).distinct().filter { it < monthLength }.associateWith { (it + 1).toString() },
            selected = day - 1,
            onSelect = { picked = it.coerceAtMost(current.lastIndex) },
            yLabel = { v -> if (hidden) "" else compactMoney(BigDecimal(v.toDouble()), currency) },
            description = "Spending so far this month: ${formatMoney(current.last(), hidden, currency)}",
            height = chartHeight,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LegendDot(Tinward.colors.accent, "This month")
            if (previous.isNotEmpty()) LegendDot(Tinward.colors.textTertiary, "Last month", dashed = true)
        }
    }
}

/** Income and spending over the last months as two lines; drag to read a month. */
@Composable
fun MonthlyTrendChart(
    trend: List<MonthPoint>,
    selectedMonth: YearMonth,
    currency: String,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 160.dp,
) {
    var picked by remember(selectedMonth) { mutableStateOf<Int?>(null) }
    val index = (picked ?: trend.indexOfFirst { it.month == selectedMonth }.takeIf { it >= 0 } ?: trend.lastIndex).coerceIn(0, (trend.size - 1).coerceAtLeast(0))
    val p = trend.getOrNull(index) ?: return
    val name = p.month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault()) + " " + p.month.year
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Column {
                Text("Spent", style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary)
                Text(formatMoney(p.spent, false, currency), style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary)
            }
            Column {
                Text("Income", style = MaterialTheme.typography.labelSmall, color = Tinward.colors.textTertiary)
                Text(formatMoney(p.income, false, currency), style = MaterialTheme.typography.titleMedium, color = Tinward.colors.positive)
            }
        }
        LineAreaChart(
            series = listOf(
                ChartSeries(trend.map { it.income.toFloat() }, Tinward.colors.positive),
                ChartSeries(trend.map { it.spent.toFloat() }, Tinward.colors.accent, fill = true),
            ),
            xCount = trend.size,
            xLabels = trend.mapIndexed { i, t -> i to t.month.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()) }.toMap(),
            selected = index,
            onSelect = { picked = it },
            yLabel = { v -> compactMoney(BigDecimal(v.toDouble()), currency) },
            description = "Income and spending over the last ${trend.size} months",
            height = chartHeight,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LegendDot(Tinward.colors.accent, "Spent"); LegendDot(Tinward.colors.positive, "Income")
        }
    }
}
