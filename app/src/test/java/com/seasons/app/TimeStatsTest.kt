package com.seasons.app

import com.seasons.app.data.LogEntry
import com.seasons.app.ui.amountPerHour
import com.seasons.app.ui.buildTimeLine
import com.seasons.app.ui.buildTimeTab
import com.seasons.app.ui.minutesByDay
import com.seasons.app.ui.timeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimeStatsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 7)
    private fun ms(date: LocalDate, h: Int, m: Int) = LocalDateTime.of(date, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private fun timed(id: Long, date: LocalDate, minutes: Int, amount: Double = 1.0) =
        LogEntry(id, 1, date, amount, 0, startTime = ms(date, 9, 0), endTime = ms(date, 9, 0) + minutes * 60_000L)

    private fun plain(id: Long, date: LocalDate) = LogEntry(id, 1, date, 5.0, 0)

    @Test fun minutesByDay_sumsTimedEntries_ignoresPlain() {
        val logs = listOf(timed(1, today, 30), timed(2, today, 15), plain(3, today), timed(4, today.minusDays(1), 20))
        assertEquals(mapOf(today to 45L, today.minusDays(1) to 20L), logs.minutesByDay())
    }

    @Test fun minutesByDay_overnightEntryCountsForItsDate() {
        val start = ms(today.minusDays(1), 23, 50)
        val end = ms(today, 0, 20)
        val e = LogEntry(1, 1, today, 2.0, 0, startTime = start, endTime = end)
        assertEquals(mapOf(today to 30L), listOf(e).minutesByDay())
    }

    @Test fun timeLine_nullWithoutAnyStopwatchEntry() {
        assertNull(buildTimeLine(emptyList(), today))
        assertNull(buildTimeLine(listOf(plain(1, today)), today))
    }

    @Test fun timeLine_todayAndLast14Days() {
        val logs = listOf(
            timed(1, today, 45),
            timed(2, today.minusDays(13), 60), // inside the 14 days
            timed(3, today.minusDays(14), 600), // outside
            timed(4, today.minusDays(5), 15),
        )
        assertEquals("Time today 45m · last 14 days 2h", buildTimeLine(logs, today))
    }

    @Test fun timeLine_zeroToday_isZeroMinutes() {
        assertEquals("Time today 0m · last 14 days 20m", buildTimeLine(listOf(timed(1, today.minusDays(1), 20)), today))
    }

    @Test fun timeText_rangeAndDuration_nullForPlain() {
        assertNull(plain(1, today).timeText(zone))
        val text = timed(1, today, 15).timeText(zone)!!
        assertEquals(true, text.endsWith("· 15m"))
        assertEquals(true, text.contains("9:00") && text.contains("9:15"))
    }

    // ---------- the Time tab ----------

    @Test fun timeTab_hiddenWithoutStopwatchEntries() {
        assertNull(buildTimeTab(listOf(plain(1, today)), "pages", today))
        assertNull(buildTimeTab(emptyList(), "pages", today))
    }

    @Test fun timeTab_minutesLine_daysWithoutTimeAreNullNotZero() {
        val logs = listOf(timed(1, today, 30), timed(2, today, 15), timed(3, today.minusDays(13), 20), timed(4, today.minusDays(14), 99), plain(5, today.minusDays(2)))
        val m = buildTimeTab(logs, "pages", today)!!.minutes
        assertEquals(14, m.values.size)
        assertEquals(45.0, m.values.last()!!, 0.0)
        assertEquals(20.0, m.values.first()!!, 0.0) // 14 days ago is outside the window
        assertNull(m.values[11]) // a day with only a plain entry has no time
        assertNull(m.values[5])
        assertEquals(45.0, m.maxY, 0.0)
        assertEquals("Today", m.lastLabel)
        assertEquals(14, m.dayLabels.size)
    }

    @Test fun timeTab_paceLine_isAmountPerHourThatDay() {
        // Day A: 30 pages in 60 min = 30/h. Day B: two entries, 10 pages in 20 min and 10 pages in 40 min = 20 pages in 1 h = 20/h.
        val logs = listOf(
            timed(1, today, 60, amount = 30.0),
            timed(2, today.minusDays(1), 20, amount = 10.0),
            timed(3, today.minusDays(1), 40, amount = 10.0),
            plain(4, today), // a plain entry on the same day never counts
        )
        val p = buildTimeTab(logs, "pages", today)!!.pace
        assertEquals(30.0, p.values.last()!!, 1e-9)
        assertEquals(20.0, p.values[12]!!, 1e-9)
        assertNull(p.values[0])
        assertEquals(30.0, p.maxY, 0.0)
    }

    @Test fun timeTab_emptyLine_axisStillHasAHeight() {
        val m = buildTimeTab(listOf(timed(1, today.minusDays(30), 10)), "pages", today)!!
        assertEquals(1.0, m.minutes.maxY, 0.0)
        assertEquals(true, m.minutes.values.all { it == null })
    }

    @Test fun perHour_usesOnlyTimedEntries() {
        // 30 pages in 1 hour, plus a plain 5 that must not count.
        val logs = listOf(timed(1, today, 60, amount = 30.0), plain(2, today))
        assertEquals("Avg 30 pages/hour", amountPerHour(logs, "pages", today))
    }

    @Test fun perHour_roundsToOneDecimal_andIgnoresOldEntries() {
        val logs = listOf(timed(1, today, 40, amount = 10.0), timed(2, today.minusDays(30), 60, amount = 500.0))
        assertEquals("Avg 15 pages/hour", amountPerHour(logs, "pages", today)) // 10 / (40/60 h) = 15
        assertEquals("Avg 7.5 pages/hour", amountPerHour(listOf(timed(1, today, 80, amount = 10.0)), "pages", today))
    }

    @Test fun perHour_noTimedEntryInWindow_isNull() {
        assertNull(amountPerHour(listOf(timed(1, today.minusDays(20), 30)), "pages", today))
        assertNull(amountPerHour(listOf(plain(1, today)), "pages", today))
    }
}
