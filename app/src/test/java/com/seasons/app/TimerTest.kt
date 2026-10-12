package com.seasons.app

import com.seasons.app.domain.adjustMinutes
import com.seasons.app.domain.dateOfEnd
import com.seasons.app.domain.elapsedMinutes
import com.seasons.app.domain.formatClock
import com.seasons.app.domain.formatMinutes
import com.seasons.app.domain.runningStartError
import com.seasons.app.domain.timeError
import com.seasons.app.domain.withDate
import com.seasons.app.domain.withTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    @Test fun date_isTheLocalDateOfTheEnd() {
        assertEquals(LocalDate.of(2026, 10, 5), dateOfEnd(ms(2026, 10, 5, 13, 34), zone))
    }

    @Test fun date_timerLeftRunningOvernight_countsForTheEndDate() {
        val start = ms(2026, 10, 5, 23, 50)
        val end = ms(2026, 10, 6, 0, 10)
        assertEquals(20, elapsedMinutes(start, end))
        assertEquals(LocalDate.of(2026, 10, 6), dateOfEnd(end, zone))
    }

    @Test fun date_usesTheZoneGiven_notUtc() {
        // 2026-10-05 20:00 UTC is already 6 Oct 01:30 in India.
        val end = LocalDateTime.of(2026, 10, 5, 20, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 6), dateOfEnd(end, zone))
        assertEquals(LocalDate.of(2026, 10, 5), dateOfEnd(end, ZoneId.of("UTC")))
    }

    @Test fun elapsed_roundsDown_andIsNeverNegative() {
        assertEquals(15, elapsedMinutes(ms(2026, 10, 5, 13, 19), ms(2026, 10, 5, 13, 34, 59)))
        assertEquals(0, elapsedMinutes(ms(2026, 10, 5, 13, 19), ms(2026, 10, 5, 13, 19, 59)))
        assertEquals(0, elapsedMinutes(ms(2026, 10, 5, 13, 19), ms(2026, 10, 5, 13, 0)))
    }

    @Test fun timeError_endMustBeAfterStart_andNotInTheFuture() {
        val now = ms(2026, 10, 5, 14, 0)
        assertNull(timeError(ms(2026, 10, 5, 13, 19), ms(2026, 10, 5, 13, 34), now))
        assertNull(timeError(ms(2026, 10, 5, 13, 19), now, now)) // ending right now is fine
        assertNotNull(timeError(ms(2026, 10, 5, 13, 34), ms(2026, 10, 5, 13, 34), now)) // zero length
        assertNotNull(timeError(ms(2026, 10, 5, 13, 34), ms(2026, 10, 5, 13, 20), now)) // backwards
        assertNotNull(timeError(ms(2026, 10, 5, 13, 0), ms(2026, 10, 5, 14, 1), now)) // future end
    }

    @Test fun runningStart_cannotBeInTheFuture() {
        val now = ms(2026, 10, 5, 14, 0)
        assertNull(runningStartError(now, now))
        assertNull(runningStartError(now - 1, now))
        assertNotNull(runningStartError(now + 1, now))
    }

    @Test fun formatMinutes_noTrailingZeros() {
        assertEquals("<1m", formatMinutes(0))
        assertEquals("1m", formatMinutes(1))
        assertEquals("15m", formatMinutes(15))
        assertEquals("59m", formatMinutes(59))
        assertEquals("1h", formatMinutes(60))
        assertEquals("1h 5m", formatMinutes(65))
        assertEquals("2h", formatMinutes(120))
        assertEquals("25h 1m", formatMinutes(25 * 60 + 1))
    }

    @Test fun formatClock_hoursNotPadded() {
        assertEquals("0:00:00", formatClock(0))
        assertEquals("0:00:59", formatClock(59_999))
        assertEquals("0:01:05", formatClock(65_000))
        assertEquals("2:13:05", formatClock((2 * 3600 + 13 * 60 + 5) * 1000L))
        assertEquals("0:00:00", formatClock(-5000))
    }

    @Test fun adjust_movesByMinutes_keepsSeconds() {
        val t = ms(2026, 10, 5, 13, 19, 30)
        assertEquals(ms(2026, 10, 5, 13, 49, 30), adjustMinutes(t, 30))
        assertEquals(ms(2026, 10, 5, 13, 14, 30), adjustMinutes(t, -5))
        assertEquals(ms(2026, 10, 5, 12, 49, 30), adjustMinutes(t, -30))
        assertEquals(ms(2026, 10, 5, 13, 20, 30), adjustMinutes(t, 1))
    }

    @Test fun adjust_acrossMidnight_changesTheDate() {
        val t = ms(2026, 10, 5, 23, 50)
        assertEquals(LocalDate.of(2026, 10, 6), dateOfEnd(adjustMinutes(t, 30), zone))
        assertEquals(LocalDate.of(2026, 10, 5), dateOfEnd(adjustMinutes(t, -30), zone))
    }

    @Test fun withDate_keepsTimeOfDay() {
        val t = ms(2026, 10, 5, 13, 19, 30)
        assertEquals(ms(2026, 10, 3, 13, 19, 30), withDate(t, LocalDate.of(2026, 10, 3), zone))
    }

    @Test fun withTime_keepsDate_clearsSeconds() {
        val t = ms(2026, 10, 5, 13, 19, 30)
        assertEquals(ms(2026, 10, 5, 9, 5), withTime(t, 9, 5, zone))
    }
}
