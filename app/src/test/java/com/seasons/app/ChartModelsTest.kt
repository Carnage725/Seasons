package com.seasons.app

import com.seasons.app.data.BandPeriod
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerType
import com.seasons.app.domain.Band
import com.seasons.app.ui.charts.buildDailyChart
import com.seasons.app.ui.charts.describeBarChart
import com.seasons.app.ui.charts.describeOnTrack
import com.seasons.app.ui.charts.describeSeason
import com.seasons.app.ui.charts.buildOnTrackChart
import com.seasons.app.ui.charts.buildSeasonModel
import com.seasons.app.ui.charts.buildWeeklyChart
import com.seasons.app.ui.charts.barIndexAt
import com.seasons.app.ui.charts.nearestIndex
import com.seasons.app.domain.StreakRun
import com.seasons.app.ui.charts.buildAveragePace
import com.seasons.app.ui.charts.buildStreakChart
import com.seasons.app.ui.charts.shortDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChartModelsTest {
    // 2026-10-07 is a Wednesday. This week's Monday is Oct 5.
    private val today = LocalDate.of(2026, 10, 7)
    private val start = today.minusDays(30)

    private fun d(offset: Int): LocalDate = today.plusDays(offset.toLong())
    private fun totals(vararg p: Pair<Int, Double>) = p.associate { d(it.first) to it.second }

    private fun tracker(
        type: TrackerType = TrackerType.ONGOING,
        band: BandPeriod = BandPeriod.NONE,
        target: Double? = null,
        deadline: LocalDate? = null,
        startDate: LocalDate = start,
    ) = Tracker(
        id = 1, name = "T", unit = "pages", type = type, color = 0,
        target = target, deadline = deadline, bandPeriod = band, startDate = startDate,
    )

    // ---------- daily ----------

    @Test fun daily_is14BarsEndingToday_withLabels() {
        val m = buildDailyChart(tracker(), totals(0 to 4.0), emptyList(), today)
        assertEquals(14, m.bars.size)
        assertEquals(4.0, m.bars.last().value, 0.0)
        assertEquals("Sep 24", m.firstLabel)
        assertEquals("Today", m.lastLabel)
        assertNull(m.bandLabel)
    }

    @Test fun daily_minimumLineSteps_andDimsDaysBelowMinimum() {
        val bands = listOf(Band(start, 3.0), Band(d(-3), 5.0))
        val t = totals(-13 to 2.0, -4 to 3.0, -3 to 4.0, 0 to 9.0)
        val m = buildDailyChart(tracker(band = BandPeriod.DAILY), t, bands, today)
        val byOffset = { off: Int -> m.bars[13 + off] }
        assertEquals(3.0, byOffset(-4).lower!!, 0.0)
        assertEquals(5.0, byOffset(-3).lower!!, 0.0)
        assertEquals(5.0, byOffset(0).lower!!, 0.0)
        assertTrue(byOffset(-13).dim) // 2 < 3
        assertFalse(byOffset(-4).dim) // 3 >= 3
        assertTrue(byOffset(-3).dim) // 4 < 5 under the new band
        assertFalse(byOffset(0).dim) // far over the minimum is fine
        assertEquals("5", m.bandLabel)
        assertEquals(9.0, m.maxY, 0.0)
    }

    @Test fun daily_noBand_neverDims_andHasNoStripe() {
        val m = buildDailyChart(tracker(), totals(0 to 1.0), emptyList(), today)
        assertTrue(m.bars.all { !it.dim && it.lower == null })
    }

    @Test fun daily_weeklyBandTrackerHasNoDailyStripe() {
        val m = buildDailyChart(tracker(band = BandPeriod.WEEKLY), totals(0 to 1.0), listOf(Band(start, 3.0)), today)
        assertTrue(m.bars.all { it.lower == null })
    }

    @Test fun daily_maxYAtLeastMinimumAndAtLeastOne() {
        val bands = listOf(Band(start, 20.0))
        assertEquals(20.0, buildDailyChart(tracker(band = BandPeriod.DAILY), totals(0 to 2.0), bands, today).maxY, 0.0)
        assertEquals(1.0, buildDailyChart(tracker(), emptyMap(), emptyList(), today).maxY, 0.0)
    }

    // ---------- weekly ----------

    @Test fun weekly_is11MondayWeeks_summingTotals() {
        // Mon Oct 5 and Wed Oct 7 are this week. Sun Oct 4 is the week before.
        val m = buildWeeklyChart(tracker(), totals(-2 to 3.0, 0 to 2.0, -3 to 10.0), emptyList(), today)
        assertEquals(11, m.bars.size)
        assertEquals(5.0, m.bars.last().value, 0.0)
        assertEquals(10.0, m.bars[9].value, 0.0)
        assertEquals("Jul 27", m.firstLabel)
        assertEquals("This week", m.lastLabel)
        assertTrue(m.bars.all { it.lower == null })
    }

    @Test fun weekly_weeklyBand_plotsActiveDaysWithMinimumLine() {
        val bands = listOf(Band(start, 3.0))
        // This week: Mon and Wed active -> 2. Last week: Mon, Tue, Thu active, one day twice -> 3 active days.
        val t = totals(-2 to 1.0, 0 to 1.0, -9 to 1.0, -8 to 4.0, -6 to 1.0)
        val m = buildWeeklyChart(tracker(band = BandPeriod.WEEKLY), t, bands, today)
        assertEquals(2.0, m.bars.last().value, 0.0)
        assertEquals(3.0, m.bars[9].value, 0.0)
        assertEquals(3.0, m.bars.last().lower!!, 0.0)
        assertEquals("3", m.bandLabel)
        assertEquals(3.0, m.maxY, 0.0)
        assertTrue(m.bars.none { it.dim })
    }

    @Test fun weekly_weeksBeforeTheTrackerStartedHaveNoStripe() {
        val bands = listOf(Band(start, 3.0))
        val m = buildWeeklyChart(tracker(band = BandPeriod.WEEKLY), emptyMap(), bands, today)
        assertNull(m.bars.first().lower) // Jul 27, long before the start
        assertEquals(3.0, m.bars.last().lower!!, 0.0)
    }

    // ---------- season ----------

    @Test fun season_cellCountAndColumns_forSeveralLengths() {
        val expectedColumns = mapOf(7 to 1, 30 to 5, 77 to 11, 90 to 13)
        for ((len, cols) in expectedColumns) {
            val m = buildSeasonModel(tracker(startDate = today), totals(0 to 1.0), emptyList(), today, today, len)!!
            assertEquals("len $len cells", len, m.cells.size)
            assertEquals("len $len columns", cols, m.columns)
            assertEquals(1, m.number)
            assertEquals(1, m.dayOfSeason)
            assertEquals(today, m.cells[0].date)
            assertEquals(today.plusDays(len - 1L), m.cells.last().date)
        }
    }

    @Test fun season_middleOfSeason_ratioMetAndCounted() {
        val bands = listOf(Band(start, 3.0))
        val t = totals(0 to 5.0, -2 to 2.0)
        val m = buildSeasonModel(tracker(band = BandPeriod.DAILY), t, bands, today, d(-10), 30)!!
        assertEquals(11, m.dayOfSeason)
        val todayCell = m.cells[10]
        assertEquals(today, todayCell.date)
        assertEquals(1f, todayCell.ratio, 1e-6f) // 5 is the season max, above the minimum 3
        assertTrue(todayCell.met)
        assertTrue(todayCell.counted)
        val partial = m.cells[8]
        assertEquals(2f / 5f, partial.ratio, 1e-6f)
        assertFalse(partial.met) // 2 < lower 3: partial progress
        assertTrue(partial.counted)
        assertFalse(m.cells[11].counted) // tomorrow
        assertEquals(0.0, m.cells[11].total, 0.0)
    }

    @Test fun season_ratioUsesSeasonMaxWhenAboveMinimum() {
        val bands = listOf(Band(start, 3.0))
        val m = buildSeasonModel(tracker(band = BandPeriod.DAILY), totals(0 to 12.0, -1 to 6.0), bands, today, d(-10), 30)!!
        assertEquals(1f, m.cells[10].ratio, 1e-6f)
        assertEquals(0.5f, m.cells[9].ratio, 1e-6f)
    }

    @Test fun season_noBand_anyPositiveDayIsMet_scaledBySeasonMax() {
        val m = buildSeasonModel(tracker(), totals(0 to 4.0, -1 to 1.0), emptyList(), today, d(-10), 30)!!
        assertTrue(m.cells[9].met)
        assertEquals(0.25f, m.cells[9].ratio, 1e-6f)
        assertFalse(m.cells[8].met) // nothing logged
    }

    @Test fun season_daysBeforeTrackerStartAreNotCounted() {
        val m = buildSeasonModel(tracker(startDate = d(-2)), totals(0 to 1.0), emptyList(), today, d(-10), 30)!!
        assertFalse(m.cells[7].counted) // day -3, before start
        assertTrue(m.cells[8].counted) // day -2
    }

    @Test fun season_secondSeason_usesItsOwnWindow() {
        // Season 1 began 100 days ago with length 77: today is day 24 of season 2.
        val m = buildSeasonModel(tracker(), totals(0 to 1.0), emptyList(), today, d(-100), 77)!!
        assertEquals(2, m.number)
        assertEquals(24, m.dayOfSeason)
        assertEquals(d(-100).plusDays(77), m.cells[0].date)
        assertEquals(today, m.cells[23].date)
    }

    @Test fun season_notStartedYet_isNull() {
        assertNull(buildSeasonModel(tracker(), emptyMap(), emptyList(), today, d(1), 77))
    }

    // ---------- on track ----------

    private fun goal(deadline: LocalDate? = null, band: BandPeriod = BandPeriod.NONE, target: Double = 100.0) =
        tracker(TrackerType.GOAL, band, target, deadline, startDate = d(-3))

    private val goalLogs = totals(-3 to 10.0, -1 to 5.0)

    @Test fun onTrack_ongoingTrackerHasNoChart() {
        assertNull(buildOnTrackChart(tracker(), emptyMap(), today))
    }

    @Test fun onTrack_cumulativeFromStartToToday() {
        val m = buildOnTrackChart(goal(deadline = d(6)), goalLogs, today)!!
        assertEquals(listOf(10.0, 10.0, 15.0, 15.0), m.cumulative.map { it.value })
        assertEquals(listOf(0, 1, 2, 3), m.cumulative.map { it.dayOffset })
        assertEquals("15", m.cumulativeLabel)
    }

    @Test fun onTrack_deadline_drawsNeededLineStartToDeadline() {
        val m = buildOnTrackChart(goal(deadline = d(6)), goalLogs, today)!!
        val (a, b) = m.needed!!
        assertEquals(0, a.dayOffset)
        assertEquals(0.0, a.value, 0.0)
        assertEquals(9, b.dayOffset)
        assertEquals(100.0, b.value, 0.0)
        assertEquals("100 by Oct 13", m.lineLabel)
        assertEquals(9, m.maxDayOffset)
        assertEquals("Oct 4", m.firstLabel)
        assertEquals("Oct 13", m.lastLabel)
    }

    @Test fun onTrack_noDeadline_hasTheCumulativeLineAndAFlatTarget() {
        val m = buildOnTrackChart(goal(), goalLogs, today)!!
        assertEquals(100.0, m.needed!!.second.value, 0.0)
        assertEquals("Target 100", m.lineLabel)
        assertEquals(3, m.maxDayOffset) // stops at today: nothing is projected
        assertEquals("Oct 7", m.lastLabel)
    }

    @Test fun onTrack_done_yAxisCoversOverage() {
        val m = buildOnTrackChart(goal(target = 10.0), goalLogs, today)!!
        assertEquals(15.0, m.maxY, 0.0)
    }

    @Test fun onTrack_deadlineInPast_xAxisStopsAtToday() {
        val m = buildOnTrackChart(goal(deadline = d(-1)), goalLogs, today)!!
        assertEquals(2, m.needed!!.second.dayOffset)
        assertEquals(3, m.maxDayOffset)
    }

    @Test fun onTrack_startedToday_singlePoint_andAtLeastOneDayWide() {
        val t = tracker(TrackerType.GOAL, BandPeriod.NONE, 100.0, null, startDate = today)
        val m = buildOnTrackChart(t, emptyMap(), today)!!
        assertEquals(1, m.cumulative.size)
        assertEquals(1, m.maxDayOffset)
    }

    @Test fun shortDate_addsYearOnlyWhenDifferent() {
        assertEquals("Oct 7", shortDate(today, today))
        assertEquals("Jan 8, 2027", shortDate(LocalDate.of(2027, 1, 8), today))
    }

    @Test fun onTrack_notNullForGoal() = assertNotNull(buildOnTrackChart(goal(), emptyMap(), today))

    // ---------- spoken descriptions ----------

    @Test fun describe_barChart_saysLatestBandAndHighest() {
        val bands = listOf(Band(start, 3.0))
        val m = buildDailyChart(tracker(band = BandPeriod.DAILY), totals(0 to 5.0, -2 to 9.0), bands, today)
        assertEquals(
            "Daily chart, last 14 days. Today: 5 laps. Minimum 3. Highest bar 9.",
            describeBarChart("Daily chart, last 14 days", m, "laps"),
        )
    }

    @Test fun describe_barChart_noBand() {
        val m = buildDailyChart(tracker(), totals(0 to 2.5), emptyList(), today)
        assertEquals("Daily chart. Today: 2.5 pages. Highest bar 2.5.", describeBarChart("Daily chart", m, "pages"))
    }

    @Test fun describe_season_countsMetDays() {
        val m = buildSeasonModel(tracker(), totals(0 to 4.0, -1 to 1.0), emptyList(), today, d(-10), 30)!!
        assertEquals(
            "Season 1 heatmap, day 11 of 30. 2 days met their goal. Tap a day to hear its value below.",
            describeSeason(m),
        )
    }

    @Test fun describe_onTrack_withAndWithoutDeadline() {
        val withDeadline = buildOnTrackChart(goal(deadline = d(6)), goalLogs, today)!!
        assertEquals(
            "Progress chart. Done 15 of 100 pages. Line: 100 by Oct 13.",
            describeOnTrack(withDeadline, "pages"),
        )
        val without = buildOnTrackChart(goal(), goalLogs, today)!!
        assertEquals("Progress chart. Done 15 of 100 pages. Line: Target 100.", describeOnTrack(without, "pages"))
    }

    // ---------- tap a bar ----------

    @Test fun barIndexAt_mapsXToTheBarUnderTheFinger() {
        assertEquals(0, barIndexAt(0f, 140f, 14))
        assertEquals(0, barIndexAt(9.9f, 140f, 14))
        assertEquals(1, barIndexAt(10f, 140f, 14))
        assertEquals(13, barIndexAt(139.9f, 140f, 14))
    }

    @Test fun barIndexAt_outsideThePlot_isNull() {
        assertNull(barIndexAt(-1f, 140f, 14))
        assertNull(barIndexAt(140f, 140f, 14)) // the label area on the right
        assertNull(barIndexAt(10f, 140f, 0))
    }

    @Test fun bars_knowTheirDayOrWeek() {
        val daily = buildDailyChart(tracker(), emptyMap(), emptyList(), today)
        assertEquals(14, daily.bars.size)
        assertEquals(shortDate(today, today), daily.bars.last().label)
        assertEquals(shortDate(today.minusDays(13), today), daily.bars.first().label)
        val weekly = buildWeeklyChart(tracker(), emptyMap(), emptyList(), today)
        assertTrue(weekly.bars.all { it.label.startsWith("Week of ") })
    }

    // ---------- average per day (pace without a target) ----------

    @Test fun pace_averagesTheLastSevenDays() {
        // 7 days ending today: 7 on one day, nothing else -> 1 per day.
        val m = buildAveragePace(tracker(), totals(0 to 7.0), today)!!
        assertEquals(1.0, m.points.last().value, 1e-9)
        assertEquals("1/day", m.currentLabel)
        assertEquals(7.0 / 7.0, m.maxY, 1e-9)
    }

    @Test fun pace_dayLeavesTheWindowAfterSevenDays() {
        val m = buildAveragePace(tracker(), totals(-7 to 7.0), today)!!
        assertEquals(0.0, m.points.last().value, 1e-9) // 7 days ago is outside today's window
        assertEquals(1.0, m.points[m.points.size - 2].value, 1e-9) // still inside yesterday's window
    }

    @Test fun pace_shortWindowNearTheStart() {
        // Tracker started 2 days ago: today's window is 3 days, not 7.
        val t = tracker().copy(startDate = today.minusDays(2))
        val m = buildAveragePace(t, totals(-2 to 3.0, 0 to 3.0), today)!!
        assertEquals(3, m.points.size) // nothing before the start date
        assertEquals(2.0, m.points.last().value, 1e-9) // 6 / 3 days
    }

    @Test fun pace_beforeStart_isNull_andEmptyIsOneForTheAxis() {
        assertNull(buildAveragePace(tracker().copy(startDate = today.plusDays(1)), emptyMap(), today))
        assertEquals(1.0, buildAveragePace(tracker(), emptyMap(), today)!!.maxY, 0.0)
    }

    @Test fun onTrack_goalWithoutDeadline_getsAFlatTargetLine() {
        val t = tracker().copy(type = TrackerType.GOAL, target = 100.0, deadline = null)
        val m = buildOnTrackChart(t, totals(-1 to 10.0), today)!!
        val (a, b) = m.needed!!
        assertEquals(100.0, a.value, 0.0)
        assertEquals(100.0, b.value, 0.0)
        assertEquals("Target 100", m.lineLabel)
    }

    // ---------- recent streaks ----------

    private fun run(startOffset: Int, endOffset: Int, current: Boolean = false) =
        StreakRun(d(startOffset), d(endOffset), endOffset - startOffset + 1, current)

    @Test fun streakChart_newestFirst_longestMarked() {
        val m = buildStreakChart(listOf(run(-20, -15), run(-10, -9), run(-3, 0, current = true)), today, weekly = false)!!
        assertEquals(listOf(4, 2, 6), m.bars.map { it.length })
        assertEquals(listOf(false, false, true), m.bars.map { it.longest })
        assertEquals(listOf(true, false, false), m.bars.map { it.current })
    }

    @Test fun streakChart_keepsOnlyTheTenMostRecent() {
        val runs = (0 until 13).map { run(-100 + it * 5, -100 + it * 5 + 1) }
        val m = buildStreakChart(runs, today, weekly = false)!!
        assertEquals(10, m.bars.size)
        assertEquals(shortDate(d(-100 + 12 * 5), today), m.bars.first().startLabel) // newest on top
        assertEquals(shortDate(d(-100 + 3 * 5), today), m.bars.last().startLabel) // the 3 oldest are dropped
    }

    @Test fun streakChart_noStreaks_isNull() {
        assertNull(buildStreakChart(emptyList(), today, weekly = false))
    }

    @Test fun streakChart_weeklyEndNeverPastToday() {
        val m = buildStreakChart(listOf(StreakRun(d(-2), d(4), 1, current = true)), today, weekly = true)!!
        assertEquals(shortDate(today, today), m.bars[0].endLabel)
    }

    // ---------- tap a point on the pace charts ----------

    @Test fun nearestIndex_picksTheClosestPoint_withinReach() {
        val xs = listOf(0f, 50f, 100f)
        assertEquals(0, nearestIndex(10f, xs, 30f))
        assertEquals(1, nearestIndex(40f, xs, 30f))
        assertEquals(2, nearestIndex(95f, xs, 30f))
    }

    @Test fun nearestIndex_tooFarOrNoPoints_isNull() {
        assertNull(nearestIndex(200f, listOf(0f, 50f, 100f), 30f))
        assertNull(nearestIndex(5f, emptyList(), 30f))
    }

    @Test fun pace_pointsCarryTheirDateAndValueText() {
        val m = buildAveragePace(tracker(), totals(0 to 7.0), today)!!
        assertEquals(m.points.size, m.pointDates.size)
        assertEquals(m.points.size, m.pointValues.size)
        assertEquals("1/day", m.pointValues.last())
        assertEquals(shortDate(today, today), m.pointDates.last())
    }

    @Test fun onTrack_pointsCarryTheirDateAndTotalText() {
        val m = buildOnTrackChart(goal(), goalLogs, today)!!
        assertEquals(m.cumulative.size, m.pointDates.size)
        assertEquals(m.cumulative.size, m.pointValues.size)
        assertEquals(m.cumulativeLabel, m.pointValues.last())
        assertEquals(shortDate(today, today), m.pointDates.last())
    }
}
