package com.seasons.app

import com.seasons.app.data.BandPeriod
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerType
import com.seasons.app.domain.Band
import com.seasons.app.ui.charts.buildDailyChart
import com.seasons.app.ui.charts.buildOnTrackChart
import com.seasons.app.ui.charts.buildSeasonModel
import com.seasons.app.ui.charts.buildWeeklyChart
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

    @Test fun daily_bandStripeSteps_andDimsDaysBelowLower() {
        val bands = listOf(Band(start, 3.0, 6.0), Band(d(-3), 5.0, 8.0))
        val t = totals(-13 to 2.0, -4 to 3.0, -3 to 4.0, 0 to 9.0)
        val m = buildDailyChart(tracker(band = BandPeriod.DAILY), t, bands, today)
        val byOffset = { off: Int -> m.bars[13 + off] }
        assertEquals(3.0, byOffset(-4).lower!!, 0.0)
        assertEquals(6.0, byOffset(-4).upper!!, 0.0)
        assertEquals(5.0, byOffset(-3).lower!!, 0.0)
        assertEquals(8.0, byOffset(0).upper!!, 0.0)
        assertTrue(byOffset(-13).dim) // 2 < 3
        assertFalse(byOffset(-4).dim) // 3 >= 3
        assertTrue(byOffset(-3).dim) // 4 < 5 under the new band
        assertFalse(byOffset(0).dim) // over the upper bound is fine
        assertEquals("5–8", m.bandLabel)
        assertEquals(9.0, m.maxY, 0.0)
    }

    @Test fun daily_noBand_neverDims_andHasNoStripe() {
        val m = buildDailyChart(tracker(), totals(0 to 1.0), emptyList(), today)
        assertTrue(m.bars.all { !it.dim && it.lower == null && it.upper == null })
    }

    @Test fun daily_weeklyBandTrackerHasNoDailyStripe() {
        val m = buildDailyChart(tracker(band = BandPeriod.WEEKLY), totals(0 to 1.0), listOf(Band(start, 3.0, 5.0)), today)
        assertTrue(m.bars.all { it.lower == null })
    }

    @Test fun daily_maxYAtLeastUpperBoundAndAtLeastOne() {
        val bands = listOf(Band(start, 3.0, 20.0))
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

    @Test fun weekly_weeklyBand_plotsActiveDaysWithStripe() {
        val bands = listOf(Band(start, 3.0, 5.0))
        // This week: Mon and Wed active -> 2. Last week: Mon, Tue, Thu active, one day twice -> 3 active days.
        val t = totals(-2 to 1.0, 0 to 1.0, -9 to 1.0, -8 to 4.0, -6 to 1.0)
        val m = buildWeeklyChart(tracker(band = BandPeriod.WEEKLY), t, bands, today)
        assertEquals(2.0, m.bars.last().value, 0.0)
        assertEquals(3.0, m.bars[9].value, 0.0)
        assertEquals(3.0, m.bars.last().lower!!, 0.0)
        assertEquals(5.0, m.bars.last().upper!!, 0.0)
        assertEquals("3–5", m.bandLabel)
        assertEquals(5.0, m.maxY, 0.0)
        assertTrue(m.bars.none { it.dim })
    }

    @Test fun weekly_weeksBeforeTheTrackerStartedHaveNoStripe() {
        val bands = listOf(Band(start, 3.0, 5.0))
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
        val bands = listOf(Band(start, 3.0, 6.0))
        val t = totals(0 to 5.0, -2 to 2.0)
        val m = buildSeasonModel(tracker(band = BandPeriod.DAILY), t, bands, today, d(-10), 30)!!
        assertEquals(11, m.dayOfSeason)
        val todayCell = m.cells[10]
        assertEquals(today, todayCell.date)
        assertEquals(5f / 6f, todayCell.ratio, 1e-6f)
        assertTrue(todayCell.met)
        assertTrue(todayCell.counted)
        val partial = m.cells[8]
        assertEquals(2f / 6f, partial.ratio, 1e-6f)
        assertFalse(partial.met) // 2 < lower 3: partial progress
        assertTrue(partial.counted)
        assertFalse(m.cells[11].counted) // tomorrow
        assertEquals(0.0, m.cells[11].total, 0.0)
    }

    @Test fun season_ratioUsesSeasonMaxWhenAboveUpperBound() {
        val bands = listOf(Band(start, 3.0, 6.0))
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
        assertNull(buildOnTrackChart(tracker(), emptyMap(), emptyList(), today))
    }

    @Test fun onTrack_cumulativeFromStartToToday() {
        val m = buildOnTrackChart(goal(deadline = d(6)), goalLogs, emptyList(), today)!!
        assertEquals(listOf(10.0, 10.0, 15.0, 15.0), m.cumulative.map { it.value })
        assertEquals(listOf(0, 1, 2, 3), m.cumulative.map { it.dayOffset })
        assertEquals("15", m.cumulativeLabel)
    }

    @Test fun onTrack_deadline_drawsNeededLineStartToDeadline() {
        val m = buildOnTrackChart(goal(deadline = d(6)), goalLogs, emptyList(), today)!!
        val (a, b) = m.needed!!
        assertEquals(0, a.dayOffset)
        assertEquals(0.0, a.value, 0.0)
        assertEquals(9, b.dayOffset)
        assertEquals(100.0, b.value, 0.0)
        assertNull(m.projection)
        assertEquals("100 by Oct 13", m.lineLabel)
        assertEquals(9, m.maxDayOffset)
        assertEquals("Oct 4", m.firstLabel)
        assertEquals("Oct 13", m.lastLabel)
    }

    @Test fun onTrack_noDeadline_projectsAtPace() {
        // pace 15/7, left 85 -> ceil(39.67) = 40 days.
        val m = buildOnTrackChart(goal(), goalLogs, emptyList(), today)!!
        assertNull(m.needed)
        val (a, b) = m.projection!!
        assertEquals(3, a.dayOffset)
        assertEquals(15.0, a.value, 0.0)
        assertEquals(43, b.dayOffset)
        assertEquals(100.0, b.value, 0.0)
        assertEquals("100 ~Nov 16", m.lineLabel)
        assertEquals(43, m.maxDayOffset)
    }

    @Test fun onTrack_noDeadline_withBand_projectsAtBandLower() {
        val bands = listOf(Band(d(-3), 4.0, 8.0))
        // left 85, lower 4 -> ceil(21.25) = 22 days.
        val m = buildOnTrackChart(goal(band = BandPeriod.DAILY), goalLogs, bands, today)!!
        assertEquals(25, m.projection!!.second.dayOffset)
        assertEquals("100 ~Oct 29", m.lineLabel)
    }

    @Test fun onTrack_noDeadline_paceZero_hasNoProjection() {
        val m = buildOnTrackChart(goal(), emptyMap(), emptyList(), today)!!
        assertNull(m.projection)
        assertNull(m.needed)
        assertNull(m.lineLabel)
        assertEquals(3, m.maxDayOffset)
    }

    @Test fun onTrack_done_hasNoProjection_andYAxisCoversOverage() {
        val m = buildOnTrackChart(goal(target = 10.0), goalLogs, emptyList(), today)!!
        assertNull(m.projection)
        assertEquals(15.0, m.maxY, 0.0)
    }

    @Test fun onTrack_deadlineInPast_xAxisStopsAtToday() {
        val m = buildOnTrackChart(goal(deadline = d(-1)), goalLogs, emptyList(), today)!!
        assertEquals(2, m.needed!!.second.dayOffset)
        assertEquals(3, m.maxDayOffset)
    }

    @Test fun onTrack_startedToday_singlePoint_andAtLeastOneDayWide() {
        val t = tracker(TrackerType.GOAL, BandPeriod.NONE, 100.0, null, startDate = today)
        val m = buildOnTrackChart(t, emptyMap(), emptyList(), today)!!
        assertEquals(1, m.cumulative.size)
        assertEquals(1, m.maxDayOffset)
    }

    @Test fun shortDate_addsYearOnlyWhenDifferent() {
        assertEquals("Oct 7", shortDate(today, today))
        assertEquals("Jan 8, 2027", shortDate(LocalDate.of(2027, 1, 8), today))
    }

    @Test fun onTrack_notNullForGoal() = assertNotNull(buildOnTrackChart(goal(), emptyMap(), emptyList(), today))
}
