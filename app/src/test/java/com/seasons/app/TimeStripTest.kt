package com.seasons.app

import com.seasons.app.data.LogEntry
import com.seasons.app.ui.charts.buildTimeStrip
import com.seasons.app.ui.charts.describeTimeStrip
import com.seasons.app.ui.charts.amountAxisTicks
import com.seasons.app.ui.charts.niceAmountMax
import com.seasons.app.ui.charts.niceMinutesMax
import com.seasons.app.ui.charts.timeAxisTicks
import com.seasons.app.ui.charts.timedDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TimeStripTest {
    private val today = LocalDate.of(2026, 10, 12)
    private val min = 60_000L

    private fun timed(id: Long, date: LocalDate, minutes: Long) =
        LogEntry(id, 1, date, 1.0, 0, startTime = 1_000_000L, endTime = 1_000_000L + minutes * min)

    private fun plain(id: Long, date: LocalDate) = LogEntry(id, 1, date, 5.0, 0)

    @Test fun noStopwatchData_noStrip() {
        assertNull(buildTimeStrip(emptyList(), today))
        assertNull(buildTimeStrip(listOf(plain(1, today)), today))
    }

    @Test fun onlyOutsideTheWindow_noStrip() {
        assertNull(buildTimeStrip(listOf(timed(1, today.minusDays(14), 60)), today))
    }

    @Test fun lastDayIsToday_oldestFirst_fourteenDays() {
        val m = buildTimeStrip(listOf(timed(1, today, 30), timed(2, today.minusDays(13), 45)), today)!!
        assertEquals(14, m.minutes.size)
        assertEquals(30L, m.minutes.last())
        assertEquals(45L, m.minutes.first())
        assertEquals(45L, m.maxMinutes)
    }

    @Test fun twoEntriesOnOneDay_areAdded_plainEntriesIgnored() {
        val m = buildTimeStrip(listOf(timed(1, today, 30), timed(2, today, 20), plain(3, today)), today)!!
        assertEquals(50L, m.minutes.last())
        assertEquals(50L, m.maxMinutes)
    }

    @Test fun daysWithoutTimeAreZero_soBarsStartAtZero() {
        val m = buildTimeStrip(listOf(timed(1, today.minusDays(3), 10)), today)!!
        assertEquals(1, m.minutes.count { it > 0 })
        assertEquals(0L, m.minutes.last())
        assertEquals(0L, m.minutes.min())
    }

    @Test fun description_mentionsTodayAndLongestDay() {
        val m = buildTimeStrip(listOf(timed(1, today, 65), timed(2, today.minusDays(1), 125)), today)!!
        assertEquals("Time chart over the same days. Today 1h 5m. Longest day 2h 5m.", describeTimeStrip(m))
    }

    @Test fun axisMax_isARoundNumberAtOrAboveTheLongestDay() {
        assertEquals(10L, niceMinutesMax(1))
        assertEquals(10L, niceMinutesMax(10))
        assertEquals(20L, niceMinutesMax(11))
        assertEquals(30L, niceMinutesMax(21))
        assertEquals(40L, niceMinutesMax(31))
        assertEquals(60L, niceMinutesMax(41))
        assertEquals(90L, niceMinutesMax(66))
        assertEquals(120L, niceMinutesMax(100))
        assertEquals(720L, niceMinutesMax(700))
        assertEquals(840L, niceMinutesMax(721)) // beyond the list: next multiple of 2h
    }

    @Test fun axisMax_neverBelowTheData() {
        for (m in 1L..1500L) assertEquals(true, niceMinutesMax(m) >= m)
    }

    @Test fun ticks_zeroHalfAndMax_allWholeMinutes() {
        for (m in 1L..1500L) {
            val max = niceMinutesMax(m)
            assertEquals(listOf(0L, max / 2L, max), timeAxisTicks(max))
            assertEquals(0L, max % 2L)
        }
        assertEquals(listOf(0L, 30L, 60L), timeAxisTicks(60L))
    }

    @Test fun model_axisMaxFollowsTheLongestDay() {
        val m = buildTimeStrip(listOf(timed(1, today, 66)), today)!!
        assertEquals(66L, m.maxMinutes)
        assertEquals(90L, m.axisMax)
    }

    @Test fun timedDays_onlyDaysWithTime_oldestFirst() {
        val m = buildTimeStrip(listOf(timed(1, today, 20), timed(2, today.minusDays(3), 10), timed(3, today.minusDays(13), 5)), today)!!
        assertEquals(listOf(0, 10, 13), timedDays(m))
    }

    @Test fun timedDays_singleDay_isOneDot() {
        val m = buildTimeStrip(listOf(timed(1, today.minusDays(4), 30)), today)!!
        assertEquals(listOf(9), timedDays(m))
    }

    @Test fun amountAxis_roundMaximumAtOrAboveTheData() {
        assertEquals(0.8, niceAmountMax(0.7), 1e-9)
        assertEquals(20.0, niceAmountMax(17.4), 1e-9)
        assertEquals(10.0, niceAmountMax(10.0), 1e-9)
        assertEquals(20.0, niceAmountMax(12.0), 1e-9)
        assertEquals(40.0, niceAmountMax(33.0), 1e-9)
        assertEquals(50.0, niceAmountMax(41.0), 1e-9)
        assertEquals(1000.0, niceAmountMax(1000.0), 1e-9)
        assertEquals(1.0, niceAmountMax(0.0), 1e-9)
        assertEquals(0.4, niceAmountMax(0.31), 1e-9)
    }

    @Test fun amountAxis_neverBelowTheData() {
        var v = 0.01
        while (v < 100_000.0) {
            assertEquals(true, niceAmountMax(v) >= v)
            v *= 1.07
        }
    }

    @Test fun amountTicks_zeroHalfMax() {
        assertEquals(listOf(0.0, 10.0, 20.0), amountAxisTicks(20.0))
        assertEquals(listOf(0.0, 2.5, 5.0), amountAxisTicks(5.0))
    }
}
