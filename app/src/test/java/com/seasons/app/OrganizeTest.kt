package com.seasons.app

import com.seasons.app.data.BandHistory
import com.seasons.app.data.BandPeriod
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerStatus
import com.seasons.app.data.TrackerType
import com.seasons.app.data.freshCopy
import com.seasons.app.ui.buildHomeRow
import com.seasons.app.ui.buildTrophy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class OrganizeTest {
    private val today = LocalDate.of(2026, 10, 7)
    private fun d(offset: Int): LocalDate = today.plusDays(offset.toLong())

    private fun tracker(
        id: Long,
        type: TrackerType = TrackerType.GOAL,
        start: LocalDate = d(-10),
    ) = Tracker(
        id = id, name = "T$id", unit = "pages", type = type, color = 123,
        target = 100.0, startDate = start, bandPeriod = BandPeriod.NONE,
    )

    private fun row(t: Tracker) = buildHomeRow(t, emptyList(), emptyList(), today)

    // ---------- start fresh ----------

    @Test fun freshCopy_keepsSettings_resetsHistoryFields() {
        val old = tracker(5).copy(
            status = TrackerStatus.ARCHIVED, completedDate = d(-1), deadline = d(20), bandPeriod = BandPeriod.DAILY,
            sortOrder = 4,
        )
        val c = freshCopy(old, today)
        assertEquals(0L, c.id)
        assertEquals(old.name, c.name)
        assertEquals(old.unit, c.unit)
        assertEquals(old.type, c.type)
        assertEquals(old.color, c.color)
        assertEquals(100.0, c.target!!, 0.0)
        assertEquals(BandPeriod.DAILY, c.bandPeriod)
        assertEquals(today, c.startDate)
        assertEquals(TrackerStatus.ACTIVE, c.status)
        assertNull(c.completedDate)
        assertEquals(d(20), c.deadline)
        assertEquals(4, c.sortOrder)
    }

    @Test fun freshCopy_dropsPastDeadline_keepsTodayOrFuture() {
        assertNull(freshCopy(tracker(1).copy(deadline = d(-1)), today).deadline)
        assertEquals(today, freshCopy(tracker(1).copy(deadline = today), today).deadline)
        assertNull(freshCopy(tracker(1), today).deadline)
    }

    // ---------- trophy ----------

    private fun log(offset: Int, amount: Double) =
        LogEntry(trackerId = 1, date = d(offset), amount = amount, createdAt = 0)

    @Test fun trophy_totalDaysAndBestStreak() {
        // Started 10 days ago, finished 2 days ago -> 9 days inclusive. Logged on -9,-8,-7 then -5,-4.
        val t = tracker(1, start = d(-10)).copy(status = TrackerStatus.COMPLETED, completedDate = d(-2))
        val logs = listOf(log(-9, 10.0), log(-8, 10.0), log(-7, 10.0), log(-5, 20.0), log(-4, 50.0))
        val card = buildTrophy(t, logs, emptyList(), today)
        assertEquals(100.0, card.total, 0.0)
        assertEquals(9, card.daysTaken)
        assertEquals(3, card.bestStreak)
        assertEquals(d(-2), card.finished)
    }

    @Test fun trophy_ignoresLogsAfterFinishDate() {
        val t = tracker(1, start = d(-10)).copy(status = TrackerStatus.COMPLETED, completedDate = d(-5))
        val card = buildTrophy(t, listOf(log(-6, 60.0), log(-5, 40.0), log(-1, 99.0)), emptyList(), today)
        assertEquals(100.0, card.total, 0.0)
    }

    @Test fun trophy_finishedOnStartDay_isOneDay() {
        val t = tracker(1, start = d(-3)).copy(status = TrackerStatus.COMPLETED, completedDate = d(-3))
        assertEquals(1, buildTrophy(t, listOf(log(-3, 100.0)), emptyList(), today).daysTaken)
    }

    @Test fun trophy_bestStreakUsesBandAtTheTime() {
        val t = tracker(1, start = d(-10)).copy(
            status = TrackerStatus.COMPLETED, completedDate = d(-6), bandPeriod = BandPeriod.DAILY,
        )
        val bands = listOf(BandHistory(trackerId = 1, effectiveFrom = d(-10), lower = 5.0, upper = 9.0))
        // -10: 5 ok, -9: 4 below lower (breaks), -8: 6 ok, -7: 9 ok (over nothing), -6: 5 ok -> best run 3
        val logs = listOf(log(-10, 5.0), log(-9, 4.0), log(-8, 6.0), log(-7, 9.0), log(-6, 5.0))
        assertEquals(3, buildTrophy(t, logs, bands, today).bestStreak)
    }
}
