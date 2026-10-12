package com.seasons.app.ui

import com.seasons.app.data.BandPeriod
import com.seasons.app.data.BandHistory
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Tracker
import com.seasons.app.domain.dailyStreaks
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class TrophyCard(
    val tracker: Tracker,
    val total: Double,
    val daysTaken: Int,
    val bestStreak: Int,
    val finished: LocalDate,
)

/** Numbers for a finished goal. Logs after the finish date are not counted. */
fun buildTrophy(tracker: Tracker, logs: List<LogEntry>, bands: List<BandHistory>, today: LocalDate): TrophyCard {
    val finished = tracker.completedDate ?: today
    val counted = logs.filter { !it.date.isAfter(finished) }
    val totals = counted.dailyTotals()
    val days = (ChronoUnit.DAYS.between(tracker.startDate, finished).toInt() + 1).coerceAtLeast(1)
    val best = dailyStreaks(totals, bands.toBands(), tracker.bandPeriod == BandPeriod.DAILY, tracker.startDate, finished).best
    return TrophyCard(tracker, totals.values.sum(), days, best, finished)
}
