package com.seasons.app.ui

import com.seasons.app.data.BandPeriod
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerType
import com.seasons.app.domain.Band
import com.seasons.app.domain.StreakStats
import com.seasons.app.domain.bandOn
import com.seasons.app.domain.dailyStreaks
import com.seasons.app.domain.daysRemaining
import com.seasons.app.domain.neededPerDay
import com.seasons.app.domain.goalProgress
import com.seasons.app.domain.weeklyStreaks
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class StreakLabels(val current: String, val best: String, val average: String, val unit: String)

/** Everything the tracker screen shows above the chart area. Pure, so it can be unit-tested. */
data class TrackerSummary(
    val headline: String,
    val subline: String?,
    val progress: Float,
    /** Goals with a deadline: "Need 10 pages/day · 10 days left". Null otherwise. */
    val needLine: String?,
    /** A goal whose total has reached the target. */
    val goalReached: Boolean,
    val streaks: StreakLabels,
    val todayLine: String,
)

fun buildTrackerSummary(tracker: Tracker, logs: List<LogEntry>, bands: List<Band>, today: LocalDate): TrackerSummary {
    val totals = logs.dailyTotals()
    val todayTotal = totals[today] ?: 0.0
    val band = if (tracker.bandPeriod == BandPeriod.NONE) null else bandOn(bands, today)
    val dailyBand = if (tracker.bandPeriod == BandPeriod.DAILY) band else null
    val unit = tracker.unit
    val weekly = tracker.bandPeriod == BandPeriod.WEEKLY

    val headline: String
    val subline: String?
    val progress: Double
    var needLine: String? = null
    var goalReached = false

    if (tracker.type == TrackerType.GOAL) {
        val target = tracker.target ?: 0.0
        val p = goalProgress(totals, target)
        headline = "${formatAmount(p.done)} / ${formatAmount(target)} $unit"
        subline = "${formatAmount(p.left)} left · ${Math.round(p.fraction * 100)}%"
        progress = p.fraction
        goalReached = p.left <= 0.0
        needLine = needLine(tracker, p.left, today)
    } else if (weekly) {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val active = generateSequence(monday) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .count { (totals[it] ?: 0.0) > 0.0 }
        headline = if (band != null) "$active / ${range(band)} days" else "$active days"
        subline = "active this week"
        progress = if (band != null && band.upper > 0.0) active / band.upper else 0.0
    } else {
        headline = if (band != null) "${formatAmount(todayTotal)} / ${range(band)} $unit" else "${formatAmount(todayTotal)} $unit"
        subline = "today"
        progress = if (band != null && band.upper > 0.0) todayTotal / band.upper else 0.0
    }

    val streaks = if (weekly) {
        weeklyStreaks(totals, bands, tracker.startDate, today)
    } else {
        dailyStreaks(totals, bands, tracker.bandPeriod == BandPeriod.DAILY, tracker.startDate, today)
    }

    val todayLine = buildString {
        append("Today ${formatAmount(todayTotal)} $unit")
        if (dailyBand != null) append(" · band ${range(dailyBand)}")
    }

    return TrackerSummary(
        headline = headline,
        subline = subline,
        progress = progress.coerceIn(0.0, 1.0).toFloat(),
        needLine = needLine,
        goalReached = goalReached,
        streaks = streakLabels(streaks, weekly),
        todayLine = todayLine,
    )
}

fun streakLabels(s: StreakStats, weekly: Boolean) = StreakLabels(
    current = s.current.toString(),
    best = s.best.toString(),
    average = String.format(Locale.US, "%.1f", s.average),
    unit = if (weekly) "weeks" else "days",
)

private fun range(b: Band) = "${formatAmount(b.lower)}–${formatAmount(b.upper)}"

private fun needLine(tracker: Tracker, left: Double, today: LocalDate): String? {
    val deadline = tracker.deadline ?: return null
    if (left <= 0.0) return null
    if (deadline.isBefore(today)) return "Deadline passed · ${formatAmount(left)} ${tracker.unit} left"
    val days = daysRemaining(deadline, today)
    return "Need ${formatAmount(neededPerDay(left, deadline, today))} ${tracker.unit}/day · $days ${if (days == 1) "day" else "days"} left"
}
