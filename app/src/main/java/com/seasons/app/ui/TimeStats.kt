package com.seasons.app.ui

import com.seasons.app.data.LogEntry
import com.seasons.app.domain.elapsedMinutes
import com.seasons.app.ui.charts.BarChartModel
import com.seasons.app.ui.charts.BarSpec
import com.seasons.app.ui.charts.shortDate
import com.seasons.app.domain.formatMinutes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Stopwatch numbers for display. Pure, so they are unit-tested.

/** Whole minutes of stopwatch time per day (the entry's date). Plain entries have no time and are left out. */
fun List<LogEntry>.minutesByDay(): Map<LocalDate, Long> =
    filter { it.isTimed }
        .groupBy { it.date }
        .mapValues { (_, entries) -> entries.sumOf { elapsedMinutes(it.startTime!!, it.endTime!!) } }

/** "Time today 45m · last 14 days 5h 20m". Null when the tracker has no stopwatch entries at all. */
fun buildTimeLine(logs: List<LogEntry>, today: LocalDate): String? {
    if (logs.none { it.isTimed }) return null
    val perDay = logs.minutesByDay()
    val todayMinutes = perDay[today] ?: 0L
    val total = (0L..13L).sumOf { perDay[today.minusDays(it)] ?: 0L }
    return "Time today ${formatMinutes(todayMinutes)} · last 14 days ${formatMinutes(total)}"
}

private val clockFormat = DateTimeFormatter.ofPattern("h:mm a")

/** "1:19 PM" in the phone's time zone. */
fun formatTimeOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(clockFormat)

/** "1:19 PM – 1:34 PM · 15m" for a stopwatch entry in History. */
fun LogEntry.timeText(zone: ZoneId = ZoneId.systemDefault()): String? {
    val s = startTime ?: return null
    val e = endTime ?: return null
    return "${formatTimeOfDay(s, zone)} – ${formatTimeOfDay(e, zone)} · ${formatMinutes(elapsedMinutes(s, e))}"
}

// ---------- The Time tab: time per day next to the amount per day ----------

data class TimeTabModel(
    /** Stopwatch minutes per day for the last 14 days, as bars. */
    val minutes: BarChartModel,
    /** "Avg 14 pages/hour". Null when there is no timed entry in the window. */
    val perHour: String?,
)

/** Null when the tracker has no stopwatch entries at all (the tab is hidden). */
fun buildTimeTab(logs: List<LogEntry>, unit: String, today: LocalDate, days: Int = 14): TimeTabModel? {
    if (logs.none { it.isTimed }) return null
    val perDay = logs.minutesByDay()
    val dates = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val bars = dates.map { d -> BarSpec((perDay[d] ?: 0L).toDouble(), null, dim = false, label = shortDate(d, today)) }
    val minutes = BarChartModel(
        bars = bars,
        maxY = maxOf(1.0, bars.maxOf { it.value }),
        bandLabel = null,
        firstLabel = shortDate(dates.first(), today),
        lastLabel = "Today",
    )
    return TimeTabModel(minutes, amountPerHour(logs, unit, today, days))
}

/**
 * Work done per hour of stopwatch time in the last [days] days. Only timed entries count, both the amount and the time,
 * so plain +1 / +5 entries never inflate it. Null when there is no timed entry in the window.
 */
fun amountPerHour(logs: List<LogEntry>, unit: String, today: LocalDate, days: Int = 14): String? {
    val from = today.minusDays((days - 1).toLong())
    val timed = logs.filter { it.isTimed && !it.date.isBefore(from) && !it.date.isAfter(today) }
    val millis = timed.sumOf { it.endTime!! - it.startTime!! }
    if (millis <= 0L) return null
    val perHour = timed.sumOf { it.amount } / (millis / 3_600_000.0)
    return "Avg ${formatAmount(Math.round(perHour * 10) / 10.0)} $unit/hour"
}

fun describeTimeChart(model: BarChartModel): String {
    val last = model.bars.last()
    return "Time chart, last 14 days. Today: ${formatMinutes(last.value.toLong())}. " +
        "Longest day ${formatMinutes(model.bars.maxOf { it.value }.toLong())}."
}
