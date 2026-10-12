package com.seasons.app

import com.seasons.app.data.LogEntry
import com.seasons.app.ui.buildTimeLine
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
}
