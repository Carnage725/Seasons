package com.seasons.app

import com.seasons.app.data.BandPeriod
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerType
import com.seasons.app.domain.Band
import com.seasons.app.domain.StreakStats
import com.seasons.app.ui.StatusKind
import com.seasons.app.ui.TrackerSummary
import com.seasons.app.ui.buildTrackerSummary
import com.seasons.app.ui.streakLabels
import com.seasons.app.ui.validateBandBounds
import org.junit.Assert.assertEquals
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

    private fun statusText(s: TrackerSummary) = s.status!!.let { it.before + it.label + it.after }

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
        assertNull(s.status)
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

    // ---------- status line ----------

    @Test fun status_deadline_onTrack() {
        // done 100, pace 10/day, deadline in 9 days -> need 10/day.
        val s = buildTrackerSummary(goal(deadline = today.plusDays(9)), listOf(log(0, 70.0), log(10, 30.0)), emptyList(), today)
        assertEquals(StatusKind.ON_TRACK, s.status!!.kind)
        assertEquals("You 10/day · Need 10/day → On track · finish ~Oct 17", statusText(s))
    }

    @Test fun status_deadline_behind_showsGap() {
        // done 100, pace 3/day, need 10/day -> need 7 more/day. 100/3 -> ceil 34 days -> Nov 10.
        val s = buildTrackerSummary(goal(deadline = today.plusDays(9)), listOf(log(0, 21.0), log(10, 79.0)), emptyList(), today)
        assertEquals(StatusKind.BEHIND, s.status!!.kind)
        assertEquals("You 3/day · Need 10/day → Behind, need 7 more/day · finish ~Nov 10", statusText(s))
    }

    @Test fun status_deadlineInPast_needsEverythingLeftToday() {
        val s = buildTrackerSummary(goal(deadline = today.minusDays(5)), listOf(log(0, 7.0)), emptyList(), today)
        assertEquals(StatusKind.BEHIND, s.status!!.kind)
        assertEquals("You 1/day · Need 193/day → Behind, need 192 more/day · finish ~Apr 18, 2027", statusText(s))
    }

    @Test fun status_band_noDeadline_showsBothProjections() {
        // left 193, band min 4 -> ceil(193/4)=49 days -> Nov 25. pace 1 -> 193 days -> Apr 18, 2027.
        val bands = listOf(Band(start, 4.0, 8.0))
        val s = buildTrackerSummary(goal(band = BandPeriod.DAILY), listOf(log(0, 7.0)), bands, today)
        assertEquals(
            "You 1/day · Band min 4/day → Behind, need 3 more/day · finish ~Nov 25 at band min · ~Apr 18, 2027 at your pace",
            statusText(s),
        )
    }

    @Test fun status_noBandNoDeadline_projectsAtPace() {
        // pace 2/day, left 186 -> 93 days -> Jan 8, 2027.
        val s = buildTrackerSummary(goal(), listOf(log(0, 14.0)), emptyList(), today)
        assertEquals(StatusKind.ON_TRACK, s.status!!.kind)
        assertEquals("You 2/day → On track · finish ~Jan 8, 2027", statusText(s))
    }

    @Test fun status_paceZero_showsDash() {
        val s = buildTrackerSummary(goal(), emptyList(), emptyList(), today)
        assertEquals("You 0/day → Behind · finish —", statusText(s))
    }

    @Test fun status_done() {
        val s = buildTrackerSummary(goal(target = 100.0, deadline = today.minusDays(3)), listOf(log(0, 100.0)), emptyList(), today)
        assertEquals(StatusKind.DONE, s.status!!.kind)
        assertEquals("Done", statusText(s))
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
