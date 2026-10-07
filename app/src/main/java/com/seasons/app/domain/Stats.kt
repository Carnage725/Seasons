package com.seasons.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.max

// Pure Kotlin. No Android, no database. `totals` is the sum of logs per date.

data class Band(val effectiveFrom: LocalDate, val lower: Double, val upper: Double)

/** The band for [date] = the row with the latest effectiveFrom <= date. Null if none yet. */
fun bandOn(bands: List<Band>, date: LocalDate): Band? =
    bands.filter { !it.effectiveFrom.isAfter(date) }.maxByOrNull { it.effectiveFrom }

// ---------- Done / left ----------

data class GoalProgress(val done: Double, val left: Double, val fraction: Double)

fun goalProgress(totals: Map<LocalDate, Double>, target: Double): GoalProgress {
    val done = totals.values.sum()
    return GoalProgress(done = done, left = max(0.0, target - done), fraction = done / target)
}

// ---------- Pace ----------

/** Sum of the last 7 days including today, divided by 7. */
fun pace(totals: Map<LocalDate, Double>, today: LocalDate): Double =
    (0L..6L).sumOf { totals[today.minusDays(it)] ?: 0.0 } / 7.0

// ---------- Needed pace and projection ----------

enum class GoalState { DONE, ON_TRACK, BEHIND }

data class GoalForecast(
    val state: GoalState,
    /** Deadline case only: left / days remaining (today included). */
    val neededPerDay: Double?,
    /** BEHIND only: how much more per day is needed. Null when it can't be worked out. */
    val shortfallPerDay: Double?,
    /** Finish date by the spec's rule: deadline -> at pace, band -> at lower bound, none -> at pace. */
    val projectedFinish: LocalDate?,
    /** Finish date at the current pace. Null when pace is 0. */
    val projectedFinishAtPace: LocalDate?,
)

fun goalForecast(
    totals: Map<LocalDate, Double>,
    target: Double,
    deadline: LocalDate?,
    dailyBand: Band?,
    today: LocalDate,
): GoalForecast {
    val left = goalProgress(totals, target).left
    val pace = pace(totals, today)
    val atPace = finishAt(today, left, pace)

    if (left <= 0.0) {
        return GoalForecast(GoalState.DONE, null, null, today, today)
    }

    if (deadline != null) {
        // A deadline today or in the past still counts as 1 day remaining.
        val daysRemaining = max(1L, ChronoUnit.DAYS.between(today, deadline) + 1)
        val needed = left / daysRemaining
        val onTrack = pace >= needed
        return GoalForecast(
            state = if (onTrack) GoalState.ON_TRACK else GoalState.BEHIND,
            neededPerDay = needed,
            shortfallPerDay = if (onTrack) null else needed - pace,
            projectedFinish = atPace,
            projectedFinishAtPace = atPace,
        )
    }

    if (dailyBand != null) {
        val onTrack = pace >= dailyBand.lower
        return GoalForecast(
            state = if (onTrack) GoalState.ON_TRACK else GoalState.BEHIND,
            neededPerDay = null,
            shortfallPerDay = if (onTrack) null else dailyBand.lower - pace,
            projectedFinish = finishAt(today, left, dailyBand.lower),
            projectedFinishAtPace = atPace,
        )
    }

    return GoalForecast(
        state = if (pace > 0.0) GoalState.ON_TRACK else GoalState.BEHIND,
        neededPerDay = null,
        shortfallPerDay = null,
        projectedFinish = atPace,
        projectedFinishAtPace = atPace,
    )
}

private fun finishAt(today: LocalDate, left: Double, rate: Double): LocalDate? {
    if (left <= 0.0) return today
    if (rate <= 0.0) return null
    return today.plusDays(ceil(left / rate).toLong())
}

// ---------- Daily streaks ----------

data class StreakStats(val current: Int, val best: Int, val average: Double)

/** Does a day with [total] count as a streak day? With a band: total >= lower. No band: total > 0. */
fun dayMet(total: Double, band: Band?): Boolean =
    if (band != null) total >= band.lower else total > 0.0

fun dailyStreaks(
    totals: Map<LocalDate, Double>,
    bands: List<Band>,
    useBand: Boolean,
    startDate: LocalDate,
    today: LocalDate,
): StreakStats {
    fun counts(d: LocalDate) = dayMet(totals[d] ?: 0.0, if (useBand) bandOn(bands, d) else null)

    // Today still in progress never breaks a streak: if not met yet, look only up to yesterday.
    val lastDay = if (counts(today)) today else today.minusDays(1)
    if (lastDay.isBefore(startDate)) return StreakStats(0, 0, 0.0)

    val flags = generateSequence(startDate) { it.plusDays(1) }
        .takeWhile { !it.isAfter(lastDay) }
        .map { counts(it) }
        .toList()
    return streaksFromFlags(flags)
}

// ---------- Weekly streaks (Mon-Sun) ----------

fun weeklyStreaks(
    totals: Map<LocalDate, Double>,
    bands: List<Band>,
    startDate: LocalDate,
    today: LocalDate,
): StreakStats {
    val firstMonday = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekMet(monday: LocalDate): Boolean {
        val from = if (monday.isBefore(startDate)) startDate else monday
        val to = monday.plusDays(6)
        val activeDays = generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) && !it.isAfter(today) }
            .count { (totals[it] ?: 0.0) > 0.0 }
        val lower = bandOn(bands, from)?.lower ?: 1.0
        return activeDays >= lower
    }

    // The current, unfinished week never breaks the streak: if not met yet, leave it out.
    val lastMonday = if (weekMet(thisMonday)) thisMonday else thisMonday.minusWeeks(1)
    if (lastMonday.isBefore(firstMonday)) return StreakStats(0, 0, 0.0)

    val flags = generateSequence(firstMonday) { it.plusWeeks(1) }
        .takeWhile { !it.isAfter(lastMonday) }
        .map { weekMet(it) }
        .toList()
    return streaksFromFlags(flags)
}

/** Current = trailing run. Best = longest run. Average = mean length of all runs >= 1. */
private fun streaksFromFlags(flags: List<Boolean>): StreakStats {
    val runs = mutableListOf<Int>()
    var run = 0
    for (met in flags) {
        if (met) run++ else {
            if (run > 0) runs.add(run)
            run = 0
        }
    }
    val current = run
    if (run > 0) runs.add(run)
    return StreakStats(
        current = current,
        best = runs.maxOrNull() ?: 0,
        average = if (runs.isEmpty()) 0.0 else runs.average(),
    )
}

// ---------- Seasons ----------

fun seasonNumber(date: LocalDate, seasonStart: LocalDate, length: Int): Int =
    Math.floorDiv(ChronoUnit.DAYS.between(seasonStart, date), length.toLong()).toInt() + 1

/** 1-based day inside the season that [date] falls in. */
fun seasonDay(date: LocalDate, seasonStart: LocalDate, length: Int): Int =
    Math.floorMod(ChronoUnit.DAYS.between(seasonStart, date), length.toLong()).toInt() + 1

fun seasonStartDate(seasonNumber: Int, seasonStart: LocalDate, length: Int): LocalDate =
    seasonStart.plusDays((seasonNumber - 1).toLong() * length)

fun seasonEndDate(seasonNumber: Int, seasonStart: LocalDate, length: Int): LocalDate =
    seasonStartDate(seasonNumber, seasonStart, length).plusDays(length - 1L)

fun heatmapColumns(length: Int): Int = (length + 6) / 7
