package com.seasons.app

import com.seasons.app.domain.Band
import com.seasons.app.domain.GoalState
import com.seasons.app.domain.bandOn
import com.seasons.app.domain.dailyStreaks
import com.seasons.app.domain.goalForecast
import com.seasons.app.domain.goalProgress
import com.seasons.app.domain.heatmapColumns
import com.seasons.app.domain.pace
import com.seasons.app.domain.seasonDay
import com.seasons.app.domain.seasonEndDate
import com.seasons.app.domain.seasonNumber
import com.seasons.app.domain.weeklyStreaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    // 2026-10-07 is a Wednesday.
    private val today = LocalDate.of(2026, 10, 7)
    private fun day(offset: Int): LocalDate = today.plusDays(offset.toLong())
    private fun totals(vararg pairs: Pair<Int, Double>) = pairs.associate { day(it.first) to it.second }
    private fun band(from: LocalDate, lower: Double, upper: Double) = Band(from, lower, upper)

    // ---------- bandOn ----------

    @Test fun bandOn_noRowYet_isNull() {
        assertNull(bandOn(listOf(band(day(0), 2.0, 5.0)), day(-1)))
    }

    @Test fun bandOn_picksLatestEffectiveFromNotAfterDate() {
        val bands = listOf(band(day(-20), 2.0, 5.0), band(day(-5), 4.0, 8.0))
        assertEquals(2.0, bandOn(bands, day(-6))!!.lower, 0.0)
        assertEquals(4.0, bandOn(bands, day(-5))!!.lower, 0.0)
        assertEquals(4.0, bandOn(bands, day(0))!!.lower, 0.0)
    }

    // ---------- done / left ----------

    @Test fun progress_doneLeftPercent() {
        val p = goalProgress(totals(-2 to 50.0, -1 to 74.0), 200.0)
        assertEquals(124.0, p.done, 0.0)
        assertEquals(76.0, p.left, 0.0)
        assertEquals(0.62, p.fraction, 1e-9)
    }

    @Test fun progress_overTarget_leftIsZero() {
        val p = goalProgress(totals(0 to 250.0), 200.0)
        assertEquals(0.0, p.left, 0.0)
    }

    // ---------- pace ----------

    @Test fun pace_lastSevenDaysIncludingToday_ignoresOlder() {
        // day(-6) is inside the window, day(-7) is not.
        assertEquals(10.0, pace(totals(0 to 35.0, -6 to 35.0, -7 to 999.0), today), 1e-9)
    }

    @Test fun pace_noLogs_isZero() {
        assertEquals(0.0, pace(emptyMap(), today), 0.0)
    }

    // ---------- forecast ----------

    @Test fun forecast_deadline_onTrack() {
        // left 100, deadline in 9 days -> 10 days remaining incl today -> need 10/day. Pace 70/7 = 10.
        val t = totals(0 to 70.0, -10 to 30.0)
        val f = goalForecast(t, 200.0, day(9), null, today)
        assertEquals(GoalState.ON_TRACK, f.state)
        assertEquals(10.0, f.neededPerDay!!, 1e-9) // left = 200-100
        assertEquals(day(10), f.projectedFinish) // ceil(100/10)=10 days
    }

    @Test fun forecast_deadline_behind_reportsGap() {
        // left 100, 10 days remaining -> need 10/day. Pace 3.
        val f = goalForecast(totals(0 to 21.0, -10 to 79.0), 200.0, day(9), null, today)
        assertEquals(GoalState.BEHIND, f.state)
        assertEquals(10.0, f.neededPerDay!!, 1e-9)
        assertEquals(7.0, f.shortfallPerDay!!, 1e-9)
    }

    @Test fun forecast_deadlineToday_countsOneDayRemaining() {
        val f = goalForecast(totals(0 to 7.0), 20.0, today, null, today)
        assertEquals(13.0, f.neededPerDay!!, 1e-9)
    }

    @Test fun forecast_deadlineInPast_needsEverythingToday() {
        val f = goalForecast(totals(0 to 7.0), 20.0, day(-30), null, today)
        assertEquals(GoalState.BEHIND, f.state)
        assertEquals(13.0, f.neededPerDay!!, 1e-9)
    }

    @Test fun forecast_noDeadline_withBand_projectsAtLowerBound() {
        // left 100, lower 4 -> ceil(100/4)=25 days. Pace 7/7=1 -> 100 days.
        val f = goalForecast(totals(0 to 7.0, -10 to 93.0), 200.0, null, band(day(-30), 4.0, 8.0), today)
        assertEquals(day(25), f.projectedFinish)
        assertEquals(day(100), f.projectedFinishAtPace)
        assertEquals(GoalState.BEHIND, f.state)
        assertEquals(3.0, f.shortfallPerDay!!, 1e-9)
    }

    @Test fun forecast_noDeadline_withBand_ceilRoundsUp() {
        val f = goalForecast(emptyMap(), 10.0, null, band(day(-1), 3.0, 5.0), today)
        assertEquals(day(4), f.projectedFinish) // ceil(10/3)
    }

    @Test fun forecast_noDeadlineNoBand_projectsAtPace() {
        val f = goalForecast(totals(0 to 14.0), 100.0, null, null, today) // pace 2, left 86
        assertEquals(day(43), f.projectedFinish)
        assertEquals(GoalState.ON_TRACK, f.state)
    }

    @Test fun forecast_paceZero_noProjection() {
        val f = goalForecast(emptyMap(), 100.0, null, null, today)
        assertNull(f.projectedFinish)
        assertNull(f.projectedFinishAtPace)
        assertEquals(GoalState.BEHIND, f.state)
    }

    @Test fun forecast_done() {
        val f = goalForecast(totals(0 to 100.0), 100.0, day(-3), null, today)
        assertEquals(GoalState.DONE, f.state)
    }

    // ---------- daily streaks ----------

    private val start = day(-30)

    @Test fun streak_noData_allZero() {
        val s = dailyStreaks(emptyMap(), emptyList(), false, start, today)
        assertEquals(0, s.current)
        assertEquals(0, s.best)
        assertEquals(0.0, s.average, 0.0)
    }

    @Test fun streak_noBand_anyPositiveDayCounts() {
        val s = dailyStreaks(totals(0 to 0.5, -1 to 1.0, -2 to 1.0), emptyList(), false, start, today)
        assertEquals(3, s.current)
    }

    @Test fun streak_todayNotMetYet_countsBackFromYesterday() {
        val s = dailyStreaks(totals(-1 to 1.0, -2 to 1.0), emptyList(), false, start, today)
        assertEquals(2, s.current)
    }

    @Test fun streak_yesterdayAndTodayMissed_isZero() {
        val s = dailyStreaks(totals(-2 to 1.0, -3 to 1.0), emptyList(), false, start, today)
        assertEquals(0, s.current)
        assertEquals(2, s.best)
    }

    @Test fun streak_withBand_belowLowerDoesNotCount() {
        val bands = listOf(band(start, 3.0, 6.0))
        val s = dailyStreaks(totals(0 to 3.0, -1 to 2.0, -2 to 5.0), bands, true, start, today)
        assertEquals(1, s.current) // -1 is below lower and breaks it
    }

    @Test fun streak_overUpperBound_stillCounts() {
        val bands = listOf(band(start, 3.0, 6.0))
        val s = dailyStreaks(totals(0 to 50.0, -1 to 7.0, -2 to 3.0), bands, true, start, today)
        assertEquals(3, s.current)
    }

    @Test fun streak_bandChangesMidStreak_eachDayUsesItsOwnBand() {
        // lower 2 until day(-2), lower 5 from day(-1). 3 meets the old band but not the new one.
        val bands = listOf(band(start, 2.0, 4.0), band(day(-1), 5.0, 8.0))
        val t = totals(-4 to 3.0, -3 to 3.0, -2 to 3.0, -1 to 5.0, 0 to 3.0)
        val s = dailyStreaks(t, bands, true, start, today)
        // today (3 < 5) not met yet -> look to yesterday. Run is days -4..-1 = 4.
        assertEquals(4, s.current)
        assertEquals(4, s.best)
    }

    @Test fun streak_bandChangeBreaksStreak() {
        val bands = listOf(band(start, 2.0, 4.0), band(day(-1), 5.0, 8.0))
        val t = totals(-3 to 3.0, -2 to 3.0, -1 to 3.0, 0 to 5.0)
        val s = dailyStreaks(t, bands, true, start, today)
        assertEquals(1, s.current) // -1 now needs 5
        assertEquals(2, s.best)
    }

    @Test fun streak_backfillFillsGapAndMergesRuns() {
        val before = totals(0 to 1.0, -1 to 1.0, -3 to 1.0, -4 to 1.0)
        assertEquals(2, dailyStreaks(before, emptyList(), false, start, today).current)
        val after = before + (day(-2) to 1.0)
        val s = dailyStreaks(after, emptyList(), false, start, today)
        assertEquals(5, s.current)
        assertEquals(5, s.best)
    }

    @Test fun streak_bestAndAverage_includeCurrentRun() {
        // runs: 3, 1, 2 (current) -> best 3, average 2.0
        val t = totals(-10 to 1.0, -9 to 1.0, -8 to 1.0, -5 to 1.0, -1 to 1.0, 0 to 1.0)
        val s = dailyStreaks(t, emptyList(), false, start, today)
        assertEquals(2, s.current)
        assertEquals(3, s.best)
        assertEquals(2.0, s.average, 1e-9)
    }

    @Test fun streak_daysBeforeStartDateAreIgnored() {
        val t = totals(-40 to 1.0, 0 to 1.0)
        val s = dailyStreaks(t, emptyList(), false, today, today)
        assertEquals(1, s.current)
        assertEquals(1, s.best)
    }

    @Test fun streak_startsToday_notMet_isZero() {
        val s = dailyStreaks(emptyMap(), emptyList(), false, today, today)
        assertEquals(0, s.current)
    }

    // ---------- weekly streaks (Mon-Sun) ----------

    // Mondays: this week 2026-10-05, last week 2026-09-28, two weeks ago 2026-09-21.
    private val weekStart = LocalDate.of(2026, 9, 14)
    private fun d(m: Int, dd: Int) = LocalDate.of(2026, m, dd)
    private fun active(vararg dates: LocalDate) = dates.associateWith { 1.0 }

    @Test fun weekly_weeksMeetingLowerCount() {
        val bands = listOf(band(weekStart, 3.0, 7.0))
        // Last week: 3 active days. Week before: 3 active days. This week: 1 so far (unfinished).
        val t = active(d(9, 28), d(9, 29), d(9, 30), d(9, 21), d(9, 22), d(9, 23), d(10, 5))
        val s = weeklyStreaks(t, bands, weekStart, today)
        assertEquals(2, s.current) // current week not met yet -> not counted, not broken
        assertEquals(2, s.best)
    }

    @Test fun weekly_currentWeekCountsOnceMet() {
        val bands = listOf(band(weekStart, 2.0, 7.0))
        val t = active(d(9, 28), d(9, 29), d(10, 5), d(10, 6))
        val s = weeklyStreaks(t, bands, weekStart, today)
        assertEquals(2, s.current)
    }

    @Test fun weekly_missedFinishedWeekBreaksStreak() {
        val bands = listOf(band(weekStart, 3.0, 7.0))
        // Two weeks ago met, last week only 2 days, this week met.
        val t = active(d(9, 21), d(9, 22), d(9, 23), d(9, 28), d(9, 29), d(10, 5), d(10, 6), d(10, 7))
        val s = weeklyStreaks(t, bands, weekStart, today)
        assertEquals(1, s.current)
        assertEquals(1, s.best)
    }

    @Test fun weekly_zeroAmountDayIsNotActive() {
        val bands = listOf(band(weekStart, 1.0, 7.0))
        val t = mapOf(d(9, 28) to 0.0)
        val s = weeklyStreaks(t, bands, weekStart, today)
        assertEquals(0, s.current)
    }

    @Test fun weekly_noLogsAtAll_isZero() {
        val s = weeklyStreaks(emptyMap(), listOf(band(weekStart, 3.0, 7.0)), weekStart, today)
        assertEquals(0, s.current)
        assertEquals(0, s.best)
        assertEquals(0.0, s.average, 0.0)
    }

    @Test fun weekly_startedThisWeek_notMetYet_isZero() {
        val bands = listOf(band(today, 3.0, 7.0))
        val s = weeklyStreaks(emptyMap(), bands, today, today)
        assertEquals(0, s.current)
    }

    // ---------- seasons ----------

    private val seasonStart = LocalDate.of(2026, 1, 1)

    @Test fun season_numberAndDay_forSeveralLengths() {
        for (len in listOf(7, 30, 77, 90)) {
            assertEquals("len $len first day", 1, seasonNumber(seasonStart, seasonStart, len))
            assertEquals("len $len first day idx", 1, seasonDay(seasonStart, seasonStart, len))
            val last = seasonStart.plusDays(len - 1L)
            assertEquals("len $len last day", 1, seasonNumber(last, seasonStart, len))
            assertEquals("len $len last day idx", len, seasonDay(last, seasonStart, len))
            val next = seasonStart.plusDays(len.toLong())
            assertEquals("len $len next", 2, seasonNumber(next, seasonStart, len))
            assertEquals("len $len next idx", 1, seasonDay(next, seasonStart, len))
        }
    }

    @Test fun season_endDate() {
        assertEquals(LocalDate.of(2026, 1, 7), seasonEndDate(1, seasonStart, 7))
        assertEquals(seasonStart.plusDays(76), seasonEndDate(1, seasonStart, 77))
        assertEquals(seasonStart.plusDays(77 + 76), seasonEndDate(2, seasonStart, 77))
    }

    @Test fun season_beforeStartIsSeasonZeroOrLess() {
        assertEquals(0, seasonNumber(seasonStart.minusDays(1), seasonStart, 77))
    }

    @Test fun season_heatmapColumns() {
        assertEquals(1, heatmapColumns(7))
        assertEquals(5, heatmapColumns(30))
        assertEquals(11, heatmapColumns(77))
        assertEquals(13, heatmapColumns(90))
        assertEquals(53, heatmapColumns(365))
    }
}
