package com.seasons.app.ui

import com.seasons.app.data.BandPeriod
import com.seasons.app.data.BandHistory
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerGroup
import com.seasons.app.domain.dailyStreaks
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One block on Home: a group and its active trackers. [group] null = ungrouped. */
data class HomeSection(val group: TrackerGroup?, val rows: List<HomeRow>)

/** Groups in their saved order, then ungrouped trackers at the bottom. Empty groups are left out. */
fun buildSections(groups: List<TrackerGroup>, rows: List<HomeRow>): List<HomeSection> {
    val sortedGroups = groups.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    val knownIds = groups.map { it.id }.toSet()
    val byGroup = rows.groupBy { it.tracker.groupId }
    val grouped = sortedGroups.mapNotNull { g ->
        byGroup[g.id]?.takeIf { it.isNotEmpty() }?.let { HomeSection(g, it) }
    }
    val ungrouped = rows.filter { it.tracker.groupId == null || it.tracker.groupId !in knownIds }
    return grouped + if (ungrouped.isEmpty()) emptyList() else listOf(HomeSection(null, ungrouped))
}

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
