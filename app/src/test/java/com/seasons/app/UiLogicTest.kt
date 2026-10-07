package com.seasons.app

import com.seasons.app.data.BandPeriod
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerType
import com.seasons.app.domain.Band
import com.seasons.app.ui.TrackerFormInput
import com.seasons.app.ui.buildHomeRow
import com.seasons.app.ui.formatAmount
import com.seasons.app.ui.parseAmount
import com.seasons.app.ui.parseHexColor
import com.seasons.app.ui.validateTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UiLogicTest {
    private val today = LocalDate.of(2026, 10, 7)

    // ---------- formatting ----------

    @Test fun format_dropsTrailingZeros() {
        assertEquals("12", formatAmount(12.0))
        assertEquals("100", formatAmount(100.0))
        assertEquals("0", formatAmount(0.0))
        assertEquals("0.5", formatAmount(0.5))
        assertEquals("12.35", formatAmount(12.345))
        assertEquals("3", formatAmount(2.999))
    }

    @Test fun parseAmount_acceptsDotAndComma() {
        assertEquals(0.5, parseAmount("0.5")!!, 0.0)
        assertEquals(0.5, parseAmount(" 0,5 ")!!, 0.0)
        assertNull(parseAmount(""))
        assertNull(parseAmount("abc"))
        assertNull(parseAmount("NaN"))
    }

    @Test fun parseHex_withAndWithoutHash() {
        assertEquals(0xFFFF8800.toInt(), parseHexColor("#FF8800"))
        assertEquals(0xFFFF8800.toInt(), parseHexColor("ff8800"))
        assertNull(parseHexColor("#FF88"))
        assertNull(parseHexColor("GGGGGG"))
    }

    // ---------- form validation ----------

    private fun input(
        name: String = "Mom Test",
        unit: String = "pages",
        type: TrackerType = TrackerType.GOAL,
        target: String = "200",
        deadline: LocalDate? = null,
        band: BandPeriod = BandPeriod.NONE,
        lower: String = "",
        upper: String = "",
        start: LocalDate = today,
    ) = TrackerFormInput(name, unit, type, target, deadline, band, lower, upper, start)

    @Test fun validate_validGoal() = assertNull(validateTracker(input(), today, false))

    @Test fun validate_requiresNameAndUnit() {
        assertNotNull(validateTracker(input(name = " "), today, false))
        assertNotNull(validateTracker(input(unit = ""), today, false))
    }

    @Test fun validate_goalNeedsPositiveTarget() {
        assertNotNull(validateTracker(input(target = ""), today, false))
        assertNotNull(validateTracker(input(target = "0"), today, false))
        assertNotNull(validateTracker(input(target = "-3"), today, false))
    }

    @Test fun validate_deadlineBeforeStart() {
        assertNotNull(validateTracker(input(deadline = today.minusDays(1)), today, false))
        assertNull(validateTracker(input(deadline = today), today, false))
    }

    @Test fun validate_goalCannotUseWeeklyBand() {
        val i = input(band = BandPeriod.WEEKLY, lower = "3", upper = "5")
        assertNotNull(validateTracker(i, today, false))
    }

    @Test fun validate_ongoingCannotUseNoBand() {
        val i = input(type = TrackerType.ONGOING, band = BandPeriod.NONE, target = "")
        assertNotNull(validateTracker(i, today, false))
    }

    @Test fun validate_ongoingDailyBand_ok_andNoTargetNeeded() {
        val i = input(type = TrackerType.ONGOING, target = "", band = BandPeriod.DAILY, lower = "3", upper = "6")
        assertNull(validateTracker(i, today, false))
    }

    @Test fun validate_bandBounds() {
        val base = input(type = TrackerType.ONGOING, band = BandPeriod.DAILY)
        assertNotNull(validateTracker(base.copy(lower = "", upper = "5"), today, false))
        assertNotNull(validateTracker(base.copy(lower = "5", upper = ""), today, false))
        assertNotNull(validateTracker(base.copy(lower = "6", upper = "5"), today, false))
        assertNotNull(validateTracker(base.copy(lower = "-1", upper = "5"), today, false))
        assertNull(validateTracker(base.copy(lower = "5", upper = "5"), today, false))
    }

    @Test fun validate_weeklyBandMax7() {
        val base = input(type = TrackerType.ONGOING, band = BandPeriod.WEEKLY, target = "")
        assertNotNull(validateTracker(base.copy(lower = "5", upper = "8"), today, false))
        assertNull(validateTracker(base.copy(lower = "5", upper = "7"), today, false))
    }

    @Test fun validate_futureStartDate() {
        assertNotNull(validateTracker(input(start = today.plusDays(1)), today, false))
    }

    @Test fun validate_editingSkipsBandAndStartChecks() {
        val i = input(type = TrackerType.ONGOING, target = "", band = BandPeriod.DAILY, lower = "", upper = "")
        assertNull(validateTracker(i, today, true))
    }

    // ---------- home row ----------

    private fun tracker(
        type: TrackerType = TrackerType.GOAL,
        band: BandPeriod = BandPeriod.NONE,
        target: Double? = 200.0,
    ) = Tracker(
        id = 1, name = "T", unit = "pages", type = type, color = 0, target = target,
        bandPeriod = band, startDate = today.minusDays(30),
    )

    private fun log(daysAgo: Long, amount: Double) =
        LogEntry(trackerId = 1, date = today.minusDays(daysAgo), amount = amount, createdAt = 0)

    @Test fun homeRow_goal_doneProgressAndAdditiveLogs() {
        val row = buildHomeRow(tracker(), listOf(log(0, 10.0), log(0, 5.0), log(3, 85.0)), emptyList(), today)
        assertEquals(100.0, row.done, 0.0)
        assertEquals(15.0, row.todayTotal, 0.0)
        assertEquals(0.5f, row.progress, 0f)
        assertTrue(row.metToday)
    }

    @Test fun homeRow_goal_progressCappedAtOne() {
        val row = buildHomeRow(tracker(), listOf(log(0, 500.0)), emptyList(), today)
        assertEquals(1f, row.progress, 0f)
    }

    @Test fun homeRow_dailyBand_belowLowerNotMet() {
        val bands = listOf(Band(today.minusDays(30), 3.0, 6.0))
        val t = tracker(TrackerType.ONGOING, BandPeriod.DAILY, null)
        val row = buildHomeRow(t, listOf(log(0, 2.0), log(1, 3.0), log(2, 3.0)), bands, today)
        assertFalse(row.metToday)
        assertEquals(2, row.streak) // today not met yet, counts back from yesterday
        assertEquals(2f / 6f, row.progress, 1e-6f)
    }

    @Test fun homeRow_dailyBand_metToday() {
        val bands = listOf(Band(today.minusDays(30), 3.0, 6.0))
        val t = tracker(TrackerType.ONGOING, BandPeriod.DAILY, null)
        val row = buildHomeRow(t, listOf(log(0, 3.0)), bands, today)
        assertTrue(row.metToday)
        assertEquals(1, row.streak)
    }

    @Test fun homeRow_weekly_streakCountsWeeks() {
        val bands = listOf(Band(today.minusDays(30), 2.0, 5.0))
        val t = tracker(TrackerType.ONGOING, BandPeriod.WEEKLY, null)
        // Mon Sep 28 + Tue Sep 29 = last week met. This week unmet so far.
        val logs = listOf(log(9, 1.0), log(8, 1.0))
        val row = buildHomeRow(t, logs, bands, today)
        assertEquals(1, row.streak)
        assertFalse(row.metToday)
    }

    @Test fun homeRow_noLogs() {
        val row = buildHomeRow(tracker(), emptyList(), emptyList(), today)
        assertEquals(0.0, row.done, 0.0)
        assertEquals(0, row.streak)
        assertFalse(row.metToday)
        assertEquals(0f, row.progress, 0f)
    }
}
