package com.seasons.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.seasons.app.ui.AppBackground
import com.seasons.app.ui.Grey1
import com.seasons.app.ui.Grey2
import com.seasons.app.ui.Grey3
import com.seasons.app.ui.TextMain
import com.seasons.app.ui.formatAmount
import java.time.LocalDate
import kotlin.math.max

// Rules: grey by default, only the tracker color carries data, no legend, no gridlines, no borders,
// bars start at zero, left-aligned text, horizontal text only.

private val LabelStyle = TextStyle(fontSize = 11.sp)

private enum class Anchor { START, END, CENTER }

/** Draws one line of text. [x] is the left edge, right edge or center depending on [anchor]. [y] is the top. */
private fun DrawScope.label(
    measurer: TextMeasurer,
    text: String,
    color: Color,
    x: Float,
    y: Float,
    anchor: Anchor = Anchor.START,
    bold: Boolean = false,
    backdrop: Color? = null,
) {
    val style = if (bold) LabelStyle.copy(fontWeight = FontWeight.Medium) else LabelStyle
    val layout = measurer.measure(text, style)
    val left = when (anchor) {
        Anchor.START -> x
        Anchor.END -> x - layout.size.width
        Anchor.CENTER -> x - layout.size.width / 2f
    }
    // A background-colored patch keeps a label readable where a line passes behind it.
    if (backdrop != null) {
        drawRect(backdrop, Offset(left - 2.dp.toPx(), y), Size(layout.size.width + 4.dp.toPx(), layout.size.height.toFloat()))
    }
    drawText(layout, color = color, topLeft = Offset(left, y))
}

// ---------- Tabs ----------

@Composable
fun ChartTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        labels.forEachIndexed { i, text ->
            Text(
                text,
                color = if (i == selected) TextMain else Grey1,
                fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.clickable { onSelect(i) }.padding(vertical = 6.dp),
            )
        }
    }
}

// ---------- Bar chart (Daily and Weekly) ----------

@Composable
fun BarChart(model: BarChartModel, color: Color, description: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    // Tap a bar to see its value. Tap it again, or another tab's chart, to clear.
    var selected by remember(model) { mutableStateOf<Int?>(null) }
    val rightPadPx = with(LocalDensity.current) { 44.dp.toPx() }
    Canvas(
        modifier.fillMaxWidth().height(190.dp)
            .pointerInput(model) {
                detectTapGestures { offset ->
                    val hit = barIndexAt(offset.x, size.width - rightPadPx, model.bars.size)
                    selected = if (hit == null || hit == selected) null else hit
                }
            }
            .semantics { contentDescription = description },
    ) {
        val rightPad = 44.dp.toPx()
        val topPad = 20.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val plotW = size.width - rightPad
        val plotH = size.height - topPad - bottomPad
        val n = model.bars.size
        val slot = plotW / n
        val barW = slot * 0.62f
        fun y(v: Double) = topPad + plotH * (1f - (v / model.maxY).toFloat())
        val baseY = y(0.0)

        // Minimum line: steps as the band changes. Each bar gets its own slice.
        model.bars.forEachIndexed { i, bar ->
            if (bar.lower != null) {
                val lineY = y(bar.lower)
                drawRect(Grey1, Offset(i * slot, lineY - 1.dp.toPx()), Size(slot + 1f, 2.dp.toPx())) // +1px hides seams
            }
        }

        // Bars, always from zero.
        model.bars.forEachIndexed { i, bar ->
            if (bar.value > 0.0) {
                val top = y(bar.value)
                drawRect(
                    if (bar.dim) Grey2 else color,
                    Offset(i * slot + (slot - barW) / 2f, top),
                    Size(barW, baseY - top),
                )
            }
        }
        drawLine(Grey2, Offset(0f, baseY), Offset(plotW, baseY), strokeWidth = 1.dp.toPx())

        // Only the last bar (today / this week) gets its value.
        val last = model.bars.last()
        val lastCenter = (n - 1) * slot + slot / 2f
        val picked = selected?.takeIf { it in model.bars.indices }
        if (picked != n - 1) {
            label(measurer, formatAmount(last.value), TextMain, lastCenter, y(last.value) - 16.dp.toPx(), Anchor.CENTER, bold = true)
        }
        // The tapped bar: its value above it, which day or week it is in the top left.
        if (picked != null) {
            val bar = model.bars[picked]
            label(measurer, formatAmount(bar.value), TextMain, picked * slot + slot / 2f, y(bar.value) - 16.dp.toPx(), Anchor.CENTER, bold = true)
            label(measurer, bar.label, TextMain, 0f, 0f)
        }

        // Minimum, labeled directly at the right end of the line.
        if (model.bandLabel != null && last.lower != null) {
            label(measurer, model.bandLabel, Grey1, plotW + 6.dp.toPx(), y(last.lower) - 7.dp.toPx())
        }

        label(measurer, model.firstLabel, Grey1, 0f, baseY + 5.dp.toPx())
        label(measurer, model.lastLabel, Grey1, plotW, baseY + 5.dp.toPx(), Anchor.END)
    }
}

// ---------- Season heatmap ----------

@Composable
fun SeasonHeatmap(model: SeasonModel, color: Color, unit: String, today: LocalDate, modifier: Modifier = Modifier) {
    var selected by rememberSaveable(model.number, model.length) { mutableStateOf<Int?>(null) }
    val cols = model.columns

    Column(modifier) {
        Text("Season ${model.number} · day ${model.dayOfSeason}/${model.length}", color = Grey1)
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(cols / 7f)
                .semantics { contentDescription = describeSeason(model) }
                .pointerInput(model) {
                    detectTapGestures { tap ->
                        val pitch = size.width / cols.toFloat()
                        val col = (tap.x / pitch).toInt()
                        val row = (tap.y / pitch).toInt()
                        val index = col * 7 + row
                        if (row in 0..6 && index < model.cells.size) selected = index
                    }
                },
        ) {
            val pitch = size.width / cols
            val gap = 2.dp.toPx()
            val cell = Size(pitch - gap, pitch - gap)
            val radius = CornerRadius(2.dp.toPx())
            model.cells.forEach { c ->
                val topLeft = Offset((c.index / 7) * pitch, (c.index % 7) * pitch)
                when {
                    !c.counted -> drawRoundRect(Grey3, topLeft, cell, radius, alpha = 0.4f)
                    c.total <= 0.0 -> drawRoundRect(Grey3, topLeft, cell, radius)
                    else -> drawRoundRect(
                        lerp(Grey3, color, 0.3f + 0.7f * c.ratio),
                        topLeft, cell, radius,
                        alpha = if (c.met) 1f else 0.55f,
                    )
                }
                val isToday = c.date == today
                val isSelected = c.index == selected
                if (isToday || isSelected) {
                    drawRoundRect(
                        if (isSelected) TextMain else Grey1,
                        topLeft, cell, radius,
                        style = Stroke(1.5.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val cell = selected?.let { model.cells.getOrNull(it) }
        Text(
            when {
                cell == null -> "Tap a day"
                !cell.counted -> shortDate(cell.date, today)
                else -> "${shortDate(cell.date, today)} · ${formatAmount(cell.total)} $unit"
            },
            color = Grey1,
        )
    }
}

// ---------- On track (goals) ----------

@Composable
fun OnTrackChart(model: OnTrackModel, color: Color, description: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = description }) {
        val rightPad = 84.dp.toPx()
        val topPad = 20.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val plotW = size.width - rightPad
        val plotH = size.height - topPad - bottomPad
        fun x(offset: Int) = plotW * offset / model.maxDayOffset.toFloat()
        fun y(v: Double) = topPad + plotH * (1f - (v / model.maxY).toFloat())
        val baseY = y(0.0)
        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))

        drawLine(Grey2, Offset(0f, baseY), Offset(plotW, baseY), strokeWidth = 1.dp.toPx())

        // Grey dashed reference line: the pace needed to hit the target by the deadline.
        val reference = model.needed
        reference?.let { (a, b) ->
            drawLine(
                Grey1,
                Offset(x(a.dayOffset), y(a.value)),
                Offset(x(b.dayOffset), y(b.value)),
                strokeWidth = 2.dp.toPx(),
                pathEffect = dash,
            )
        }

        // Cumulative progress in the tracker color.
        val points = model.cumulative
        if (points.size == 1) {
            drawCircle(color, 3.dp.toPx(), Offset(x(points[0].dayOffset), y(points[0].value)))
        } else {
            val path = Path()
            points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(x(p.dayOffset), y(p.value)) else path.lineTo(x(p.dayOffset), y(p.value))
            }
            drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Direct labels at the line ends.
        val lastPoint = points.last()
        val lastY = y(lastPoint.value)
        label(
            measurer, model.cumulativeLabel, color, x(lastPoint.dayOffset) + 6.dp.toPx(), lastY - 7.dp.toPx(),
            bold = true, backdrop = AppBackground,
        )
        if (reference != null && model.lineLabel != null) {
            val end = reference.second
            var labelY = y(end.value) - 7.dp.toPx()
            // Keep the two end labels from sitting on top of each other.
            val sameSpot = Math.abs(x(end.dayOffset) - x(lastPoint.dayOffset)) < 40.dp.toPx() &&
                Math.abs(labelY - (lastY - 7.dp.toPx())) < 14.dp.toPx()
            if (sameSpot) labelY -= 14.dp.toPx()
            label(measurer, model.lineLabel, Grey1, x(end.dayOffset) + 6.dp.toPx(), labelY)
        }

        label(measurer, model.firstLabel, Grey1, 0f, baseY + 5.dp.toPx())
        label(measurer, model.lastLabel, Grey1, plotW, baseY + 5.dp.toPx(), Anchor.END)
    }
}

// ---------- Average per day (trackers without a target) ----------

@Composable
fun AveragePaceChart(model: AveragePaceModel, color: Color, description: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = description }) {
        val rightPad = 64.dp.toPx()
        val topPad = 20.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val plotW = size.width - rightPad
        val plotH = size.height - topPad - bottomPad
        val lastIndex = max(1, model.points.size - 1)
        fun x(i: Int) = plotW * i / lastIndex.toFloat()
        fun y(v: Double) = topPad + plotH * (1f - (v / model.maxY).toFloat())
        val baseY = y(0.0)

        drawLine(Grey2, Offset(0f, baseY), Offset(plotW, baseY), strokeWidth = 1.dp.toPx())

        val points = model.points
        if (points.size == 1) {
            drawCircle(color, 3.dp.toPx(), Offset(x(0), y(points[0].value)))
        } else {
            val path = Path()
            points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(x(i), y(p.value)) else path.lineTo(x(i), y(p.value))
            }
            drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        label(measurer, "Avg per day, 7 days", Grey1, 0f, 0f)
        val end = points.last()
        label(measurer, model.currentLabel, color, x(points.size - 1) + 6.dp.toPx(), y(end.value) - 7.dp.toPx(), bold = true, backdrop = AppBackground)
        label(measurer, model.firstLabel, Grey1, 0f, baseY + 5.dp.toPx())
        label(measurer, model.lastLabel, Grey1, plotW, baseY + 5.dp.toPx(), Anchor.END)
    }
}

// ---------- Recent streaks ----------

private val StreakRow = 30.dp

/** Bars centered, longer = wider. Start date on the left, end date on the right, length inside. Newest on top. */
@Composable
fun StreakChart(model: StreakChartModel, color: Color, description: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val longest = model.bars.maxOf { it.length }
    Canvas(
        modifier.fillMaxWidth().height(StreakRow * model.bars.size).semantics { contentDescription = description },
    ) {
        val dateW = 84.dp.toPx()
        val gap = 8.dp.toPx()
        val maxBar = size.width - 2 * (dateW + gap)
        val minBar = 34.dp.toPx()
        val rowH = StreakRow.toPx()
        val barH = 22.dp.toPx()
        val cx = size.width / 2f
        model.bars.forEachIndexed { i, bar ->
            val barW = minBar + (maxBar - minBar) * (bar.length - 1).coerceAtLeast(0) / (longest - 1).coerceAtLeast(1)
            val top = i * rowH + (rowH - barH) / 2f
            val fill = when {
                bar.longest -> color
                bar.current -> color.copy(alpha = 0.55f)
                else -> Grey3
            }
            drawRoundRect(fill, Offset(cx - barW / 2f, top), Size(barW, barH), CornerRadius(3.dp.toPx()))
            val textColor = if (bar.longest || bar.current) Color.Black else TextMain
            label(measurer, bar.length.toString(), textColor, cx, top + 2.dp.toPx(), Anchor.CENTER, bold = true)
            label(measurer, bar.startLabel, Grey1, cx - barW / 2f - gap, top + 2.dp.toPx(), Anchor.END)
            label(measurer, bar.endLabel, Grey1, cx + barW / 2f + gap, top + 2.dp.toPx())
        }
    }
}
