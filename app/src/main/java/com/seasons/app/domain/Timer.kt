package com.seasons.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

// Pure rules for the stopwatch. Times are epoch millis (absolute instants). Nothing here reads the clock.

/** The day a stopwatch entry counts for: the local date of its END time. */
fun dateOfEnd(endTime: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(endTime).atZone(zone).toLocalDate()

/** Whole minutes between start and end, rounded down. Never negative. */
fun elapsedMinutes(startTime: Long, endTime: Long): Long = ((endTime - startTime) / 60_000L).coerceAtLeast(0L)

/** Why a start/end pair cannot be saved, or null when it is fine. */
fun timeError(startTime: Long, endTime: Long, now: Long): String? = when {
    endTime <= startTime -> "End must be after start."
    endTime > now -> "End cannot be in the future."
    else -> null
}

/** Why a running timer's start cannot be saved, or null. */
fun runningStartError(startTime: Long, now: Long): String? =
    if (startTime > now) "Start cannot be in the future." else null

/** "15m", "1h 5m", "2h". Zero (or under a minute, rounded down) gives "0m". No trailing zeros. */
fun formatMinutes(minutes: Long): String = when {
    minutes <= 0L -> "0m"
    minutes < 60L -> "${minutes}m"
    minutes % 60L == 0L -> "${minutes / 60L}h"
    else -> "${minutes / 60L}h ${minutes % 60L}m"
}

/** "2:13:05" for a running timer. Hours are not padded. */
fun formatClock(elapsedMillis: Long): String {
    val total = (elapsedMillis / 1000L).coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return "%d:%02d:%02d".format(h, m, s)
}

/** [millis] moved by [minutes] (negative = earlier). Seconds are kept. */
fun adjustMinutes(millis: Long, minutes: Int): Long = millis + minutes * 60_000L

/** The same time of day, on [date]. */
fun withDate(millis: Long, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long {
    val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    return ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
}

/** The same date, at [hour]:[minute] with seconds cleared. */
fun withTime(millis: Long, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}
