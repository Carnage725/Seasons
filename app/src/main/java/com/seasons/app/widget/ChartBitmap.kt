package com.seasons.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.seasons.app.ui.charts.BarChartModel

/**
 * Draws the Daily bar chart into a bitmap. Glance has no Canvas, so a widget shows its chart as an image.
 * Same rules as the app: grey by default, only the tracker color carries data, no legend, no gridlines,
 * bars start at zero, horizontal text only, only the last bar gets its value.
 */
fun renderBarChartBitmap(model: BarChartModel, trackerColor: Int, widthPx: Int, heightPx: Int, density: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val grey1 = 0xFF9E9E9E.toInt()
    val grey2 = 0xFF616161.toInt()
    val grey3 = 0xFF2C2C2C.toInt()
    val textMain = 0xFFE0E0E0.toInt()

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f * density }
    val textHeight = text.fontMetrics.let { it.descent - it.ascent }

    val rightPad = if (model.bandLabel != null) 34f * density else 2f * density
    val topPad = textHeight + 3f * density // room for today's value above the tallest bar
    val bottomPad = textHeight + 3f * density
    val plotW = bitmap.width - rightPad
    val plotH = bitmap.height - topPad - bottomPad
    if (plotW <= 0f || plotH <= 0f) return bitmap

    val n = model.bars.size
    val slot = plotW / n
    val barW = slot * 0.62f
    fun y(value: Double) = topPad + plotH * (1f - (value / model.maxY).toFloat())
    val baseY = y(0.0)

    // Band stripe, stepping as the band changes. The extra pixel hides seams between days.
    fill.color = grey3
    model.bars.forEachIndexed { i, bar ->
        if (bar.lower != null && bar.upper != null) {
            val top = y(bar.upper)
            val bottom = maxOf(y(bar.lower), top + 2f * density)
            canvas.drawRect(RectF(i * slot, top, (i + 1) * slot + 1f, bottom), fill)
        }
    }

    // Bars, always from zero. Days below the band's lower bound are dim grey.
    model.bars.forEachIndexed { i, bar ->
        if (bar.value > 0.0) {
            fill.color = if (bar.dim) grey2 else trackerColor
            val left = i * slot + (slot - barW) / 2f
            canvas.drawRect(RectF(left, y(bar.value), left + barW, baseY), fill)
        }
    }

    fill.color = grey2
    canvas.drawRect(RectF(0f, baseY, plotW, baseY + density), fill)

    // Today's value only.
    val last = model.bars.last()
    text.color = textMain
    text.isFakeBoldText = true
    text.textAlign = Paint.Align.CENTER
    val valueText = com.seasons.app.ui.formatAmount(last.value)
    canvas.drawText(valueText, (n - 1) * slot + slot / 2f, y(last.value) - 4f * density, text)
    text.isFakeBoldText = false

    // The band range, labeled directly at the right end of the stripe.
    if (model.bandLabel != null && last.lower != null && last.upper != null) {
        text.color = grey1
        text.textAlign = Paint.Align.LEFT
        canvas.drawText(model.bandLabel, plotW + 4f * density, y((last.lower + last.upper) / 2.0) + textHeight / 4f, text)
    }

    text.color = grey1
    text.textAlign = Paint.Align.LEFT
    canvas.drawText(model.firstLabel, 0f, bitmap.height - text.fontMetrics.descent, text)
    text.textAlign = Paint.Align.RIGHT
    canvas.drawText(model.lastLabel, plotW, bitmap.height - text.fontMetrics.descent, text)

    return bitmap
}
