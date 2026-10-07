package com.seasons.app

import com.seasons.app.data.BandHistory
import com.seasons.app.data.BandPeriod
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerGroup
import com.seasons.app.data.TrackerStatus
import com.seasons.app.data.TrackerType
import com.seasons.app.data.freshCopy
import com.seasons.app.data.swapGroupOrder
import com.seasons.app.ui.buildHomeRow
import com.seasons.app.ui.buildSections
import com.seasons.app.ui.buildTrophy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class OrganizeTest {
    private val today = LocalDate.of(2026, 10, 7)
    private fun d(offset: Int): LocalDate = today.plusDays(offset.toLong())

    private fun group(id: Long, order: Int) = TrackerGroup(id = id, name = "G$id", color = 0, sortOrder = order)

    private fun tracker(
        id: Long,
        groupId: Long? = null,
        type: TrackerType = TrackerType.GOAL,
        start: LocalDate = d(-10),
    ) = Tracker(
        id = id, name = "T$id", unit = "pages", type = type, color = 123, groupId = groupId,
        target = 100.0, startDate = start, bandPeriod = BandPeriod.NONE,
    )

    private fun row(t: Tracker) = buildHomeRow(t, emptyList(), emptyList(), today)

    // ---------- group order ----------

    @Test fun swap_movesDownAndUp() {
        val g = listOf(group(1, 0), group(2, 1), group(3, 2))
        assertEquals(listOf(2L, 1L, 3L), swapGroupOrder(g, 1, up = false).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), swapGroupOrder(g, 3, up = true).map { it.id })
    }

    @Test fun swap_atEdges_changesNothing() {
        val g = listOf(group(1, 0), group(2, 1))
        assertEquals(listOf(1L, 2L), swapGroupOrder(g, 1, up = true).map { it.id })
        assertEquals(listOf(1L, 2L), swapGroupOrder(g, 2, up = false).map { it.id })
        assertEquals(listOf(1L, 2L), swapGroupOrder(g, 99, up = true).map { it.id })
    }

    @Test fun swap_rewritesSortOrderAsIndexes_evenWithGaps() {
        val g = listOf(group(1, 5), group(2, 9), group(3, 20))
        val result = swapGroupOrder(g, 2, up = true)
        assertEquals(listOf(2L, 1L, 3L), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.sortOrder })
    }

    // ---------- Home sections ----------

    @Test fun sections_followGroupOrder_ungroupedLast() {
        val groups = listOf(group(1, 1), group(2, 0))
        val rows = listOf(row(tracker(10, groupId = 1)), row(tracker(11)), row(tracker(12, groupId = 2)))
        val s = buildSections(groups, rows)
        assertEquals(listOf(2L, 1L, null), s.map { it.group?.id })
        assertEquals(listOf(11L), s.last().rows.map { it.tracker.id })
    }

    @Test fun sections_emptyGroupsAreLeftOut() {
        val s = buildSections(listOf(group(1, 0), group(2, 1)), listOf(row(tracker(10, groupId = 2))))
        assertEquals(listOf(2L), s.map { it.group?.id })
    }

    @Test fun sections_groupThatNoLongerExists_fallsBackToUngrouped() {
        val s = buildSections(listOf(group(1, 0)), listOf(row(tracker(10, groupId = 77))))
        assertEquals(1, s.size)
        assertNull(s[0].group)
    }

    @Test fun sections_noGroups_singleUngroupedSection() {
        val s = buildSections(emptyList(), listOf(row(tracker(1)), row(tracker(2))))
        assertEquals(1, s.size)
        assertNull(s[0].group)
        assertEquals(2, s[0].rows.size)
    }

    @Test fun sections_noTrackers_isEmpty() {
        assertEquals(emptyList<Any>(), buildSections(listOf(group(1, 0)), emptyList()))
    }

    // ---------- start fresh ----------

    @Test fun freshCopy_keepsSettings_resetsHistoryFields() {
        val old = tracker(5, groupId = 3).copy(
            status = TrackerStatus.ARCHIVED, completedDate = d(-1), deadline = d(20), bandPeriod = BandPeriod.DAILY,
            sortOrder = 4,
        )
        val c = freshCopy(old, today)
        assertEquals(0L, c.id)
        assertEquals(old.name, c.name)
        assertEquals(old.unit, c.unit)
        assertEquals(old.type, c.type)
        assertEquals(old.color, c.color)
        assertEquals(3L, c.groupId)
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
