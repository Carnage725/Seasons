package com.seasons.app.ui

import com.seasons.app.domain.seasonNumber
import com.seasons.app.domain.seasonStartDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// The current season as a calendar. Pure, so every rule is unit-tested. Weeks start on Monday.

/** The running season. [number] is the display number (it carries on after a season length change). */
data class SeasonWindow(val number: Int, val start: LocalDate, val end: LocalDate, val length: Int)

/**
 * The season that contains [today]. If the first season has not started yet, that first season.
 * [numberBase] is how many seasons were snapshotted before the current start date (see `seasonBase`).
 */
fun currentSeasonWindow(seasonStart: LocalDate, length: Int, today: LocalDate, numberBase: Int): SeasonWindow {
    val formulaNumber = if (today.isBefore(seasonStart)) 1 else seasonNumber(today, seasonStart, length)
    val start = seasonStartDate(formulaNumber, seasonStart, length)
    return SeasonWindow(numberBase + formulaNumber, start, start.plusDays(length - 1L), length)
}

/** One month of the season. Only days inside the season are listed, oldest first. */
data class MonthBlock(val month: YearMonth, val days: List<LocalDate>) {
    /** Empty cells before the first listed day, so it sits under the right weekday. Monday = 0. */
    val leadingBlanks: Int get() = days.first().dayOfWeek.value - 1
}

/** The calendar: the first block starts on [start], the last ends on [end]. Days outside the season are not drawn. */
fun buildSeasonCalendar(start: LocalDate, end: LocalDate): List<MonthBlock> {
    require(!end.isBefore(start)) { "A season ends on or after its start" }
    val blocks = mutableListOf<MonthBlock>()
    var month = YearMonth.from(start)
    val lastMonth = YearMonth.from(end)
    while (!month.isAfter(lastMonth)) {
        val first = maxOf(month.atDay(1), start)
        val last = minOf(month.atEndOfMonth(), end)
        blocks += MonthBlock(month, (0..ChronoUnit.DAYS.between(first, last)).map { first.plusDays(it) })
        month = month.plusMonths(1)
    }
    return blocks
}

/** The block as week rows of exactly 7 cells (null = empty), Monday first. */
fun MonthBlock.weeks(): List<List<LocalDate?>> {
    val cells: List<LocalDate?> = List(leadingBlanks) { null } + days
    val padded = cells + List((7 - cells.size % 7) % 7) { null }
    return padded.chunked(7)
}

/** "Day 31 of 77 · 46 days left", "Starts in 3 days", or "Ended". */
fun seasonProgressText(window: SeasonWindow, today: LocalDate): String {
    if (today.isBefore(window.start)) {
        val d = ChronoUnit.DAYS.between(today, window.start)
        return "Starts in $d ${if (d == 1L) "day" else "days"}"
    }
    if (today.isAfter(window.end)) return "Ended"
    val day = ChronoUnit.DAYS.between(window.start, today).toInt() + 1
    val left = ChronoUnit.DAYS.between(today, window.end)
    val leftText = when (left) {
        0L -> "last day"
        1L -> "1 day left"
        else -> "$left days left"
    }
    return "Day $day of ${window.length} · $leftText"
}
