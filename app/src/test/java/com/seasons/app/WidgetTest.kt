package com.seasons.app

import com.seasons.app.data.BandHistory
import com.seasons.app.data.BandPeriod
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerGroup
import com.seasons.app.data.TrackerStatus
import com.seasons.app.data.TrackerType
import com.seasons.app.domain.Band
import com.seasons.app.ui.Screen
import com.seasons.app.ui.buildHomeRow
import com.seasons.app.ui.mainText
import com.seasons.app.ui.screensForLink
import com.seasons.app.ui.statusText
import com.seasons.app.ui.streakText
import com.seasons.app.widget.WidgetTarget
import com.seasons.app.widget.buildGroupData
import com.seasons.app.widget.buildSingleData
import com.seasons.app.widget.millisUntilNextMidnight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetTest {
    private val today = LocalDate.of(2026, 10, 7)
    private val start = today.minusDays(30)

    // ---------- midnight ----------

    private fun at(zone: String, y: Int, m: Int, d: Int, h: Int, min: Int, s: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, s, 0, ZoneId.of(zone))

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test fun midnight_lateEvening_isTheTimeLeftInTheDay() {
        assertEquals(90 * minute, millisUntilNextMidnight(at("UTC", 2026, 10, 7, 22, 30)))
    }

    @Test fun midnight_justAfterMidnight_isAlmostAFullDay() {
        assertEquals(24 * hour - 1_000, millisUntilNextMidnight(at("UTC", 2026, 10, 7, 0, 0, 1)))
    }

    @Test fun midnight_exactlyMidnight_waitsForTheNextOne() {
        assertEquals(24 * hour, millisUntilNextMidnight(at("UTC", 2026, 10, 7, 0, 0)))
    }

    @Test fun midnight_oneSecondBefore() {
        assertEquals(1_000L, millisUntilNextMidnight(at("UTC", 2026, 10, 7, 23, 59, 59)))
    }

    @Test fun midnight_springForwardDayHas23Hours() {
        // New York, 2026-03-08: clocks jump 02:00 -> 03:00, so the day is 23 hours long.
        assertEquals(23 * hour, millisUntilNextMidnight(at("America/New_York", 2026, 3, 8, 0, 0)))
        assertEquals(22 * hour, millisUntilNextMidnight(at("America/New_York", 2026, 3, 8, 1, 0)))
    }

    @Test fun midnight_fallBackDayHas25Hours() {
        // New York, 2026-11-01: clocks go back 02:00 -> 01:00, so the day is 25 hours long.
        assertEquals(25 * hour, millisUntilNextMidnight(at("America/New_York", 2026, 11, 1, 0, 0)))
    }

    @Test fun midnight_eveningBeforeSpringForward() {
        // 22:00 on Mar 7 to midnight is 2 hours: the jump happens after midnight.
        assertEquals(2 * hour, millisUntilNextMidnight(at("America/New_York", 2026, 3, 7, 22, 0)))
    }

    @Test fun midnight_halfHourOffsetZone() {
        assertEquals(30 * minute, millisUntilNextMidnight(at("Asia/Kolkata", 2026, 10, 7, 23, 30)))
    }

    // ---------- widget target ----------

    @Test fun target_roundTrips() {
        assertEquals(WidgetTarget.OfTracker(12), WidgetTarget.decode(WidgetTarget.OfTracker(12).encode()))
        assertEquals(WidgetTarget.OfGroup(3), WidgetTarget.decode(WidgetTarget.OfGroup(3).encode()))
    }

    @Test fun target_badInputIsNull() {
        assertNull(WidgetTarget.decode(null))
        assertNull(WidgetTarget.decode(""))
        assertNull(WidgetTarget.decode("tracker"))
        assertNull(WidgetTarget.decode("tracker:abc"))
        assertNull(WidgetTarget.decode("thing:5"))
        assertNull(WidgetTarget.decode("tracker:5:6"))
    }

    // ---------- deep link ----------

    @Test fun link_trackerOpensHomeThenTracker() {
        assertEquals(listOf(Screen.Home, Screen.TrackerDetail(7)), screensForLink(7, null))
    }

    @Test fun link_groupOpensHomeThenGroup() {
        assertEquals(listOf(Screen.Home, Screen.GroupDetail(4)), screensForLink(null, 4))
    }

    @Test fun link_trackerWinsIfBothGiven_andNothingGivesHome() {
        assertEquals(listOf(Screen.Home, Screen.TrackerDetail(7)), screensForLink(7, 4))
        assertEquals(listOf(Screen.Home), screensForLink(null, null))
    }

    // ---------- widget data ----------

    private fun tracker(
        id: Long,
        name: String = "T$id",
        type: TrackerType = TrackerType.GOAL,
        band: BandPeriod = BandPeriod.NONE,
        groupId: Long? = null,
        status: TrackerStatus = TrackerStatus.ACTIVE,
        deadline: LocalDate? = null,
    ) = Tracker(
        id = id, name = name, unit = "pages", type = type, color = 99, groupId = groupId,
        target = if (type == TrackerType.GOAL) 200.0 else null, deadline = deadline,
        bandPeriod = band, startDate = start, status = status,
    )

    private fun log(trackerId: Long, daysAgo: Long, amount: Double) =
        LogEntry(trackerId = trackerId, date = today.minusDays(daysAgo), amount = amount, createdAt = 0)

    @Test fun single_goal_showsDoneLeftAndToday() {
        val d = buildSingleData(tracker(1), listOf(log(1, 0, 24.0), log(1, 3, 100.0)), emptyList(), today)
        assertEquals("124 / 200 pages", d.headline)
        assertEquals("76 left · 62%", d.subline)
        assertEquals(0.62f, d.progress, 1e-6f)
        assertEquals("Met today", d.status)
        assertTrue(d.metToday)
        assertEquals("Streak 1", d.streak)
        assertEquals(99, d.color)
    }

    @Test fun single_carriesTheLast14DaysForTheChart() {
        val d = buildSingleData(tracker(1), listOf(log(1, 0, 24.0), log(1, 3, 10.0), log(1, 20, 99.0)), emptyList(), today)
        assertEquals(14, d.chart.bars.size)
        assertEquals(24.0, d.chart.bars.last().value, 0.0)
        assertEquals(10.0, d.chart.bars[13 - 3].value, 0.0)
        assertEquals(34.0, d.chart.bars.sumOf { it.value }, 0.0) // the log 20 days ago is outside the window
        assertEquals("Today", d.chart.lastLabel)
    }

    @Test fun single_dailyBand_chartHasTheBandStripeAndDimDays() {
        val bands = listOf(Band(start, 3.0, 6.0))
        val t = tracker(2, type = TrackerType.ONGOING, band = BandPeriod.DAILY)
        val d = buildSingleData(t, listOf(log(2, 0, 2.0), log(2, 1, 5.0)), bands, today)
        assertEquals("3–6", d.chart.bandLabel)
        assertTrue(d.chart.bars.last().dim) // 2 < 3
        assertFalse(d.chart.bars[12].dim) // 5 >= 3
        assertEquals(3.0, d.chart.bars.last().lower!!, 0.0)
    }

    @Test fun single_goalWithDeadline_carriesTheNeedLine() {
        val d = buildSingleData(tracker(1, deadline = today.plusDays(9)), listOf(log(1, 0, 100.0)), emptyList(), today)
        assertEquals("Need 10 pages/day · 10 days left", d.needLine)
    }

    @Test fun single_ongoingDailyBand_showsTodayAgainstTheBand() {
        val bands = listOf(Band(start, 3.0, 6.0))
        val t = tracker(2, type = TrackerType.ONGOING, band = BandPeriod.DAILY)
        val d = buildSingleData(t, listOf(log(2, 0, 2.0)), bands, today)
        assertEquals("2 / 3–6 pages", d.headline)
        assertEquals("Not yet", d.status)
        assertFalse(d.metToday)
    }

    @Test fun single_weeklyTracker_streakInWeeks() {
        val bands = listOf(Band(start, 1.0, 5.0))
        val t = tracker(3, type = TrackerType.ONGOING, band = BandPeriod.WEEKLY)
        val d = buildSingleData(t, listOf(log(3, 0, 1.0)), bands, today)
        assertTrue(d.streak.endsWith("wk"))
    }

    @Test fun groupData_onlyThisGroupsActiveTrackers_inOrder() {
        val group = TrackerGroup(id = 5, name = "Study", color = 7)
        val trackers = listOf(
            tracker(1, groupId = 5),
            tracker(2, groupId = 6),
            tracker(3, groupId = 5, status = TrackerStatus.ARCHIVED),
            tracker(4, groupId = null),
            tracker(5, groupId = 5, status = TrackerStatus.COMPLETED),
            tracker(6, groupId = 5),
        )
        val logs = listOf(log(1, 0, 10.0), log(6, 0, 20.0))
        val d = buildGroupData(group, trackers, logs, emptyList<BandHistory>(), today)
        assertEquals("Study", d.groupName)
        assertEquals(7, d.color)
        assertEquals(listOf(1L, 6L), d.rows.map { it.trackerId })
        assertEquals("10 / 200 pages", d.rows[0].main)
        assertEquals("20 / 200 pages", d.rows[1].main)
    }

    @Test fun groupData_emptyGroupHasNoRows() {
        val d = buildGroupData(TrackerGroup(id = 1, name = "Empty", color = 0), emptyList(), emptyList(), emptyList(), today)
        assertTrue(d.rows.isEmpty())
    }

    @Test fun rowTexts_areTheSameOnHomeAndInWidgets() {
        val row = buildHomeRow(tracker(1), listOf(log(1, 0, 5.0)), emptyList(), today)
        assertEquals("5 / 200 pages", row.mainText())
        assertEquals("Met today", row.statusText())
        assertEquals("Streak 1", row.streakText())
        val ongoing = buildHomeRow(tracker(2, type = TrackerType.ONGOING), listOf(log(2, 0, 3.0)), emptyList(), today)
        assertEquals("3 pages today", ongoing.mainText())
    }
}
