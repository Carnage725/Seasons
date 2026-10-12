package com.seasons.app.ui

import com.seasons.app.data.LogEntry
import com.seasons.app.domain.elapsedMinutes
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
