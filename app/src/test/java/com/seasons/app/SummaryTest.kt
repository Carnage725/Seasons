package com.seasons.app

import com.seasons.app.data.BandPeriod
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerType
import com.seasons.app.domain.Band
import com.seasons.app.domain.StreakStats
import com.seasons.app.ui.TrackerSummary
import com.seasons.app.ui.buildTrackerSummary
import com.seasons.app.ui.streakLabels
import com.seasons.app.ui.validateBandBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SummaryTest {
    // 2026-10-07 is a Wednesday.
    private val today = LocalDate.of(2026, 10, 7)
    private val start = today.minusDays(30)

    private fun goal(deadline: LocalDate? = null, target: Double = 200.0, band: BandPeriod = BandPeriod.NONE) = Tracker(
        id = 1, name = "Book", unit = "pages", type = TrackerType.GOAL, color = 0,
        target = target, deadline = deadline, bandPeriod = band, startDate = start,
    )

    private fun ongoing(period: BandPeriod) = Tracker(
        id = 2, name = "Run", unit = "laps", type = TrackerType.ONGOING, color = 0,
        bandPeriod = period, startDate = start,
    )

    private fun log(daysAgo: Long, amount: Double) =
        LogEntry(trackerId = 1, date = today.minusDays(daysAgo), amount = amount, createdAt = 0)

    // ---------- header ----------

    @Test fun goalHeader_doneLeftPercent() {
        val s = buildTrackerSummary(goal(), listOf(log(0, 24.0), log(5, 100.0)), emptyList(), today)
        assertEquals("124 / 200 pages", s.headline)
        assertEquals("76 left · 62%", s.subline)
        assertEquals(0.62f, s.progress, 1e-6f)
    }

    @Test fun goalHeader_overTarget_leftZero_progressCapped() {
        val s = buildTrackerSummary(goal(target = 100.0), listOf(log(0, 125.0)), emptyList(), today)
        assertEquals("0 left · 125%", s.subline)
        assertEquals(1f, s.progress, 0f)
    }

    @Test fun ongoingDailyHeader_todayVsBand() {
        val bands = listOf(Band(start, 3.0, 6.0))
        val s = buildTrackerSummary(ongoing(BandPeriod.DAILY), listOf(log(0, 2.0), log(0, 3.0)), bands, today)
        assertEquals("5 / 3–6 laps", s.headline)
        assertEquals("today", s.subline)
        assertEquals(5f / 6f, s.progress, 1e-6f)
        assertNull(s.needLine)
        assertEquals("Today 5 laps · band 3–6", s.todayLine)
    }

    @Test fun ongoingWeeklyHeader_activeDaysThisWeek() {
        val bands = listOf(Band(start, 3.0, 5.0))
        // Mon Oct 5 and Wed Oct 7 are active this week. Sun Oct 4 is last week.
        val s = buildTrackerSummary(ongoing(BandPeriod.WEEKLY), listOf(log(2, 1.0), log(0, 1.0), log(3, 1.0)), bands, today)
        assertEquals("2 / 3–5 days", s.headline)
        assertEquals("active this week", s.subline)
        assertEquals(0.4f, s.progress, 1e-6f)
        assertEquals("weeks", s.streaks.unit)
    }

    // ---------- needed pace line ----------

    @Test fun needLine_deadline_saysHowMuchPerDay() {
        // 100 pages left, deadline in 9 days -> 10 days including today -> 10 pages a day.
        val s = buildTrackerSummary(goal(deadline = today.plusDays(9)), listOf(log(0, 70.0), log(10, 30.0)), emptyList(), today)
        assertEquals("Need 10 pages/day · 10 days left", s.needLine)
    }

    @Test fun needLine_roundsToTwoDecimalsAndDropsTrailingZeros() {
        val s = buildTrackerSummary(goal(deadline = today.plusDays(2)), listOf(log(0, 1.0)), emptyList(), today)
        assertEquals("Need 66.33 pages/day · 3 days left", s.needLine) // 199 / 3
    }

    @Test fun needLine_deadlineToday_oneDayLeft() {
        val s = buildTrackerSummary(goal(deadline = today), listOf(log(0, 7.0)), emptyList(), today)
        assertEquals("Need 193 pages/day · 1 day left", s.needLine)
    }

    @Test fun needLine_deadlinePassed() {
        val s = buildTrackerSummary(goal(deadline = today.minusDays(5)), listOf(log(0, 7.0)), emptyList(), today)
        assertEquals("Deadline passed · 193 pages left", s.needLine)
    }

    @Test fun needLine_noDeadline_orOngoing_orDone_hasNoLine() {
        assertNull(buildTrackerSummary(goal(), listOf(log(0, 7.0)), emptyList(), today).needLine)
        assertNull(buildTrackerSummary(ongoing(BandPeriod.DAILY), emptyList(), listOf(Band(start, 3.0, 6.0)), today).needLine)
        assertNull(buildTrackerSummary(goal(deadline = today.plusDays(5), target = 10.0), listOf(log(0, 10.0)), emptyList(), today).needLine)
    }

    @Test fun goalReached_whenTotalReachesTarget() {
        assertTrue(buildTrackerSummary(goal(target = 10.0), listOf(log(0, 10.0)), emptyList(), today).goalReached)
        assertTrue(buildTrackerSummary(goal(target = 10.0), listOf(log(0, 12.0)), emptyList(), today).goalReached)
        assertFalse(buildTrackerSummary(goal(target = 10.0), listOf(log(0, 9.0)), emptyList(), today).goalReached)
        assertFalse(buildTrackerSummary(ongoing(BandPeriod.NONE), listOf(log(0, 9.0)), emptyList(), today).goalReached)
    }

    @Test fun summary_noPaceOrProjectionAnywhere() {
        val s = buildTrackerSummary(goal(deadline = today.plusDays(9)), listOf(log(0, 70.0)), emptyList(), today)
        val all = listOfNotNull(s.headline, s.subline, s.needLine, s.todayLine).joinToString(" ")
        assertFalse(all.contains("You "))
        assertFalse(all.contains("finish"))
        assertFalse(all.contains("On track"))
        assertFalse(all.contains("Behind"))
    }

    // ---------- streak labels ----------

    @Test fun streakLabels_oneDecimal_andUnit() {
        val l = streakLabels(StreakStats(current = 2, best = 3, average = 2.0), weekly = false)
        assertEquals("2", l.current)
        assertEquals("3", l.best)
        assertEquals("2.0", l.average)
        assertEquals("days", l.unit)
        assertEquals("1.7", streakLabels(StreakStats(1, 2, 5.0 / 3.0), weekly = true).average)
        assertEquals("weeks", streakLabels(StreakStats(0, 0, 0.0), weekly = true).unit)
    }

    @Test fun summary_streaksComeFromLogs() {
        val s = buildTrackerSummary(goal(), listOf(log(0, 1.0), log(1, 1.0), log(3, 1.0)), emptyList(), today)
        assertEquals("2", s.streaks.current)
        assertEquals("2", s.streaks.best)
        assertEquals("1.5", s.streaks.average)
    }

    // ---------- change-band validation ----------

    @Test fun bandBounds_validation() {
        assertNull(validateBandBounds(BandPeriod.NONE, "", ""))
        assertNotNull(validateBandBounds(BandPeriod.DAILY, "", "5"))
        assertNotNull(validateBandBounds(BandPeriod.DAILY, "6", "5"))
        assertNull(validateBandBounds(BandPeriod.DAILY, "3", "6"))
        assertNotNull(validateBandBounds(BandPeriod.WEEKLY, "3", "8"))
        assertNull(validateBandBounds(BandPeriod.WEEKLY, "3", "7"))
    }
}
