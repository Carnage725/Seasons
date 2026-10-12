package com.seasons.app

import com.seasons.app.data.LogEntry
import com.seasons.app.ui.charts.buildTimeStrip
import com.seasons.app.ui.charts.describeTimeStrip
import com.seasons.app.ui.charts.timeLabelIndices
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
        assertEquals("Time line over the same days. Today 1h 5m. Longest day 2h 5m.", describeTimeStrip(m))
    }

    @Test fun labels_longestDay_andTodayWhenItHasTime() {
        val m = buildTimeStrip(listOf(timed(1, today.minusDays(5), 60), timed(2, today, 20)), today)!!
        assertEquals(listOf(8, 13), timeLabelIndices(m))
    }

    @Test fun labels_todayIsTheLongest_onlyOnce() {
        val m = buildTimeStrip(listOf(timed(1, today.minusDays(5), 20), timed(2, today, 60)), today)!!
        assertEquals(listOf(13), timeLabelIndices(m))
    }

    @Test fun labels_todayWithoutTime_notLabeled() {
        val m = buildTimeStrip(listOf(timed(1, today.minusDays(2), 45)), today)!!
        assertEquals(listOf(11), timeLabelIndices(m))
    }

    @Test fun labels_tiedLongestDays_firstOneWins() {
        val m = buildTimeStrip(listOf(timed(1, today.minusDays(6), 30), timed(2, today.minusDays(2), 30)), today)!!
        assertEquals(listOf(7), timeLabelIndices(m))
    }
}
