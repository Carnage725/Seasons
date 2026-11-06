package com.seasons.app.ui

import com.seasons.app.data.LogEntry
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// History on the tracker screen, grouped by month and then by day. Pure, so it is unit-tested.

data class DayGroup(val date: LocalDate, val total: Double, val entries: List<LogEntry>)

data class MonthGroup(
    val month: YearMonth,
    /** "November 2026" */
    val title: String,
    val total: Double,
    /** Days of the month that have at least one entry. */
    val dayCount: Int,
    val days: List<DayGroup>,
) {
    /** Stable text key, for saved state and list keys. */
    val key: String get() = month.toString()
}

/** Newest month first, newest day first, newest entry first (ties by id, so the order never jumps). */
fun groupHistory(logs: List<LogEntry>): List<MonthGroup> =
    logs.groupBy { YearMonth.from(it.date) }
        .toSortedMap(compareByDescending { it })
        .map { (month, monthLogs) ->
            val days = monthLogs.groupBy { it.date }
                .toSortedMap(compareByDescending { it })
                .map { (date, dayLogs) ->
                    DayGroup(date, dayLogs.sumOf { it.amount }, dayLogs.sortedWith(compareByDescending<LogEntry> { it.createdAt }.thenByDescending { it.id }))
                }
            MonthGroup(
                month = month,
                title = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
                total = days.sumOf { it.total },
                dayCount = days.size,
                days = days,
            )
        }

/** "Thu Nov 5", with "Today" and "Yesterday" kept as words. */
fun dayHeader(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEE MMM d", Locale.US))
}

fun monthSummary(group: MonthGroup, unit: String): String =
    "${formatAmount(group.total)} $unit · ${group.dayCount} ${if (group.dayCount == 1) "day" else "days"}"
