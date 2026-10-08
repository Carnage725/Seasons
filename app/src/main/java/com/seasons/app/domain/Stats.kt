package com.seasons.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
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

// ---------- Needed pace (goals with a deadline) ----------

/** Days left to the deadline, today included. A deadline today, or already past, counts as 1. */
fun daysRemaining(deadline: LocalDate, today: LocalDate): Int =
    max(1L, ChronoUnit.DAYS.between(today, deadline) + 1).toInt()

/** What you must average per day, from today, to finish by the deadline. */
fun neededPerDay(left: Double, deadline: LocalDate, today: LocalDate): Double =
    left / daysRemaining(deadline, today)

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
