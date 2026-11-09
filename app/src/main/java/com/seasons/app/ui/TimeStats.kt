package com.seasons.app.ui

import com.seasons.app.data.LogEntry
import com.seasons.app.domain.elapsedMinutes
import com.seasons.app.ui.charts.DayLineModel
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

// ---------- The Time tab: time per day and pace per day, as lines ----------

data class TimeTabModel(
    /** Stopwatch minutes per day, last 14 days. Null on days with no stopwatch entry. */
    val minutes: DayLineModel,
    /** Work done per stopwatch hour that day. Null on days with no stopwatch entry. */
    val pace: DayLineModel,
    /** "Avg 14 pages/hour" over the window. Null when there is no timed entry in the window. */
    val perHour: String?,
)

/** Null when the tracker has no stopwatch entries at all (the tab is hidden). */
fun buildTimeTab(logs: List<LogEntry>, unit: String, today: LocalDate, days: Int = 14): TimeTabModel? {
    if (logs.none { it.isTimed }) return null
    val dates = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val timedByDay = logs.filter { it.isTimed }.groupBy { it.date }

    val minutes = dates.map { d ->
        timedByDay[d]?.sumOf { elapsedMinutes(it.startTime!!, it.endTime!!) }?.toDouble()
    }
    val pace = dates.map { d -> timedByDay[d]?.let { paceOf(it) } }
    fun line(values: List<Double?>) = DayLineModel(
        values = values,
        dayLabels = dates.map { shortDate(it, today) },
        maxY = maxOf(1.0, values.filterNotNull().maxOrNull() ?: 0.0),
        firstLabel = shortDate(dates.first(), today),
        lastLabel = "Today",
    )
    return TimeTabModel(line(minutes), line(pace), amountPerHour(logs, unit, today, days))
}

/** Amount per hour for a set of timed entries. Null when the total time is zero. */
private fun paceOf(timed: List<LogEntry>): Double? {
    val millis = timed.sumOf { it.endTime!! - it.startTime!! }
    if (millis <= 0L) return null
    return timed.sumOf { it.amount } / (millis / 3_600_000.0)
}

/**
 * Work done per hour of stopwatch time in the last [days] days. Only timed entries count, both the amount and the time,
 * so plain +1 / +5 entries never inflate it. Null when there is no timed entry in the window.
 */
fun amountPerHour(logs: List<LogEntry>, unit: String, today: LocalDate, days: Int = 14): String? {
    val from = today.minusDays((days - 1).toLong())
    val timed = logs.filter { it.isTimed && !it.date.isBefore(from) && !it.date.isAfter(today) }
    val perHour = paceOf(timed) ?: return null
    return "Avg ${formatAmount(Math.round(perHour * 10) / 10.0)} $unit/hour"
}

fun describeTimeChart(model: DayLineModel): String {
    val last = model.values.lastOrNull { it != null }
    val longest = model.values.filterNotNull().maxOrNull()
    return "Time chart, last 14 days. " +
        (last?.let { "Latest day with time: ${formatMinutes(it.toLong())}. " } ?: "") +
        (longest?.let { "Longest day ${formatMinutes(it.toLong())}." } ?: "")
}

fun describePaceChart(model: DayLineModel, unit: String): String {
    val last = model.values.lastOrNull { it != null }
    return "Pace chart, last 14 days, $unit per hour. " + (last?.let { "Latest ${formatAmount(Math.round(it * 10) / 10.0)}." } ?: "")
}

/** "1.5/h" style text for a pace point. */
fun paceText(value: Double): String = "${formatAmount(Math.round(value * 10) / 10.0)}/h"
