package com.seasons.app.ui

import com.seasons.app.data.BandPeriod
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerType
import com.seasons.app.domain.Band
import com.seasons.app.domain.GoalState
import com.seasons.app.domain.StreakStats
import com.seasons.app.domain.bandOn
import com.seasons.app.domain.dailyStreaks
import com.seasons.app.domain.goalForecast
import com.seasons.app.domain.goalProgress
import com.seasons.app.domain.pace
import com.seasons.app.domain.weeklyStreaks
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class StatusKind { ON_TRACK, BEHIND, DONE }

/** "You 9/day · Need 6/day → [On track] · finish ~Oct 16". The label is drawn in its own color. */
data class StatusLine(val before: String, val label: String, val kind: StatusKind, val after: String)

data class StreakLabels(val current: String, val best: String, val average: String, val unit: String)

/** Everything the tracker screen shows above the chart area. Pure, so it can be unit-tested. */
data class TrackerSummary(
    val headline: String,
    val subline: String?,
    val progress: Float,
    val status: StatusLine?,
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
    var status: StatusLine? = null

    if (tracker.type == TrackerType.GOAL) {
        val target = tracker.target ?: 0.0
        val p = goalProgress(totals, target)
        headline = "${formatAmount(p.done)} / ${formatAmount(target)} $unit"
        subline = "${formatAmount(p.left)} left · ${Math.round(p.fraction * 100)}%"
        progress = p.fraction
        status = goalStatus(tracker, totals, dailyBand, today)
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
        status = status,
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

private fun goalStatus(tracker: Tracker, totals: Map<LocalDate, Double>, dailyBand: Band?, today: LocalDate): StatusLine {
    val target = tracker.target ?: 0.0
    val f = goalForecast(totals, target, tracker.deadline, dailyBand, today)
    if (f.state == GoalState.DONE) return StatusLine("", "Done", StatusKind.DONE, "")

    val you = "You ${formatAmount(pace(totals, today))}/day"
    val kind = if (f.state == GoalState.ON_TRACK) StatusKind.ON_TRACK else StatusKind.BEHIND
    val label = if (kind == StatusKind.ON_TRACK) "On track" else "Behind"
    val gap = f.shortfallPerDay?.let { ", need ${formatAmount(it)} more/day" } ?: ""

    return when {
        f.neededPerDay != null -> StatusLine(
            "$you · Need ${formatAmount(f.neededPerDay)}/day → ", label, kind,
            "$gap · finish ${finish(f.projectedFinish, today)}",
        )
        dailyBand != null -> StatusLine(
            "$you · Band min ${formatAmount(dailyBand.lower)}/day → ", label, kind,
            "$gap · finish ${finish(f.projectedFinish, today)} at band min · ${finish(f.projectedFinishAtPace, today)} at your pace",
        )
        else -> StatusLine("$you → ", label, kind, " · finish ${finish(f.projectedFinish, today)}")
    }
}

private fun finish(date: LocalDate?, today: LocalDate): String = when {
    date == null -> "—"
    date.year == today.year -> "~" + date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
    else -> "~" + date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
}
