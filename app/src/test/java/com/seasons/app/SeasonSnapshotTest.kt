package com.seasons.app

import com.seasons.app.data.BandHistory
import com.seasons.app.data.BandPeriod
import com.seasons.app.data.LogEntry
import com.seasons.app.data.SeasonPayload
import com.seasons.app.data.SeasonSummary
import com.seasons.app.data.SeasonTrackerStat
import com.seasons.app.data.SeasonTrophy
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerStatus
import com.seasons.app.data.TrackerType
import com.seasons.app.data.buildSeasonPayload
import com.seasons.app.data.finishedWindows
import com.seasons.app.data.nextUnseenSummary
import com.seasons.app.data.parseSeasonPayload
import com.seasons.app.data.partialSeasonWindow
import com.seasons.app.data.seasonBase
import com.seasons.app.data.toJson
import com.seasons.app.data.validateSeasonChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SeasonSnapshotTest {
    private val start = LocalDate.of(2026, 1, 1)

    // ---------- finished seasons, several lengths ----------

    @Test fun finishedWindows_forSeveralLengths() {
        for (len in listOf(7, 30, 77, 90)) {
            // 2 full seasons plus 3 days into the third.
            val today = start.plusDays(2L * len + 3)
            val w = finishedWindows(start, len, today)
            assertEquals("len $len count", 2, w.size)
            assertEquals(listOf(1, 2), w.map { it.number })
            assertEquals(start, w[0].start)
            assertEquals(start.plusDays(len - 1L), w[0].end)
            assertEquals(start.plusDays(len.toLong()), w[1].start)
            assertEquals(start.plusDays(2L * len - 1), w[1].end)
        }
    }

    @Test fun finishedWindows_lastDayOfSeasonIsNotFinished_nextDayIs() {
        val len = 30
        assertEquals(0, finishedWindows(start, len, start.plusDays(len - 1L)).size)
        assertEquals(1, finishedWindows(start, len, start.plusDays(len.toLong())).size)
    }

    @Test fun finishedWindows_notStartedYet_isEmpty() {
        assertTrue(finishedWindows(start, 77, start.minusDays(1)).isEmpty())
    }

    // ---------- length change mid-season ----------

    @Test fun partialWindow_isTheDaysBeforeTheNewStart() {
        val today = start.plusDays(20) // day 21 of season 1 (length 77)
        val w = partialSeasonWindow(start, 77, today, today)!!
        assertEquals(1, w.number)
        assertEquals(start, w.start)
        assertEquals(today.minusDays(1), w.end)
        assertEquals(start.plusDays(8), partialSeasonWindow(start, 77, start.plusDays(9), today)!!.end)
    }

    @Test fun partialWindow_noneWhenNewStartIsTheSeasonStart() {
        assertNull(partialSeasonWindow(start, 77, start, start.plusDays(20)))
    }

    @Test fun partialWindow_usesTheRunningSeason() {
        // Day 5 of season 2 (length 30): partial runs from season 2's start.
        val today = start.plusDays(34)
        val w = partialSeasonWindow(start, 30, today, today)!!
        assertEquals(2, w.number)
        assertEquals(start.plusDays(30), w.start)
        assertEquals(today.minusDays(1), w.end)
    }

    @Test fun validateChange_rules() {
        val today = start.plusDays(34) // season 2 began start+30
        val seasonStart = start
        assertNull(validateSeasonChange(seasonStart, 90, today, 30, today))
        assertNull(validateSeasonChange(seasonStart, 7, start.plusDays(30), 30, today)) // exactly the running season start
        assertNotNull(validateSeasonChange(seasonStart, 6, today, 30, today)) // too short
        assertNotNull(validateSeasonChange(seasonStart, 366, today, 30, today)) // too long
        assertNotNull(validateSeasonChange(seasonStart, 30, today.plusDays(1), 30, today)) // future
        assertNotNull(validateSeasonChange(seasonStart, 30, start.plusDays(29), 30, today)) // before running season began
    }

    // ---------- season numbers ----------

    private fun summary(number: Int, startDate: LocalDate) = SeasonSummary(
        id = number.toLong(), seasonNumber = number, startDate = startDate, endDate = startDate.plusDays(6),
        length = 7, payloadJson = "{}",
    )

    @Test fun seasonBase_countsSnapshotsBeforeTheCurrentStart() {
        val list = listOf(summary(1, start), summary(2, start.plusDays(7)), summary(3, start.plusDays(14)))
        assertEquals(3, seasonBase(list, start.plusDays(21)))
        assertEquals(1, seasonBase(list, start.plusDays(7))) // only the first began before
        assertEquals(0, seasonBase(emptyList(), start))
    }

    // ---------- payload ----------

    private fun tracker(
        id: Long,
        name: String = "T$id",
        status: TrackerStatus = TrackerStatus.ACTIVE,
        startDate: LocalDate = start,
        band: BandPeriod = BandPeriod.NONE,
        completed: LocalDate? = null,
        type: TrackerType = TrackerType.ONGOING,
    ) = Tracker(
        id = id, name = name, unit = "pages", type = type, color = 42, target = if (type == TrackerType.GOAL) 100.0 else null,
        bandPeriod = band, startDate = startDate, status = status, completedDate = completed,
    )

    private fun log(trackerId: Long, offset: Int, amount: Double) =
        LogEntry(trackerId = trackerId, date = start.plusDays(offset.toLong()), amount = amount, createdAt = 0)

    private val windowEnd = start.plusDays(9) // season is days 0..9
    private val today = start.plusDays(40)

    @Test fun payload_totalsOnlyCountLogsInsideTheWindow() {
        val logs = listOf(log(1, -2, 100.0), log(1, 0, 5.0), log(1, 9, 3.0), log(1, 10, 100.0))
        val p = buildSeasonPayload(listOf(tracker(1)), logs, emptyList(), start, windowEnd, today)
        assertEquals(8.0, p.trackers.single().total, 0.0)
    }

    @Test fun payload_daysActiveCountsDistinctDaysAcrossTrackers() {
        val logs = listOf(log(1, 0, 1.0), log(2, 0, 1.0), log(1, 1, 1.0), log(2, 5, 1.0), log(2, 20, 1.0))
        val p = buildSeasonPayload(listOf(tracker(1), tracker(2)), logs, emptyList(), start, windowEnd, today)
        assertEquals(3, p.daysActive) // days 0, 1, 5
    }

    @Test fun payload_bestStreakIsClippedToTheSeason() {
        // Logged every day from day -5 to day 12, but the season is days 0..9: best streak is 10, not 18.
        val logs = (-5..12).map { log(1, it, 1.0) }
        val p = buildSeasonPayload(listOf(tracker(1, startDate = start.minusDays(10))), logs, emptyList(), start, windowEnd, today)
        assertEquals(10, p.trackers.single().bestStreak)
    }

    @Test fun payload_bestStreakUsesTheDailyBand() {
        val bands = listOf(BandHistory(trackerId = 1, effectiveFrom = start, lower = 3.0, upper = 6.0))
        // days 0-2 meet it, day 3 has 2 (below lower), days 4-5 meet it -> best 3
        val logs = listOf(log(1, 0, 3.0), log(1, 1, 4.0), log(1, 2, 9.0), log(1, 3, 2.0), log(1, 4, 3.0), log(1, 5, 3.0))
        val p = buildSeasonPayload(listOf(tracker(1, band = BandPeriod.DAILY)), logs, bands, start, windowEnd, today)
        assertEquals(3, p.trackers.single().bestStreak)
    }

    @Test fun payload_weeklyTrackerStreakIsInWeeks() {
        // 2026-01-01 is a Thursday. Weekly band 2: week of Dec 29 (Thu Jan 1, Fri Jan 2), week of Jan 5 (Mon, Tue).
        val bands = listOf(BandHistory(trackerId = 1, effectiveFrom = start, lower = 2.0, upper = 5.0))
        val logs = listOf(log(1, 0, 1.0), log(1, 1, 1.0), log(1, 4, 1.0), log(1, 5, 1.0))
        val p = buildSeasonPayload(listOf(tracker(1, band = BandPeriod.WEEKLY)), logs, bands, start, windowEnd, today)
        val stat = p.trackers.single()
        assertTrue(stat.weekly)
        assertEquals(2, stat.bestStreak)
    }

    @Test fun payload_trackerInclusionRules() {
        val active = tracker(1, "Active")
        val activeNotStartedYet = tracker(2, "Later", startDate = windowEnd.plusDays(1))
        val archivedWithLogs = tracker(3, "Archived with logs", status = TrackerStatus.ARCHIVED)
        val archivedNoLogs = tracker(4, "Archived quiet", status = TrackerStatus.ARCHIVED)
        val logs = listOf(log(3, 2, 4.0))
        val p = buildSeasonPayload(
            listOf(active, activeNotStartedYet, archivedWithLogs, archivedNoLogs), logs, emptyList(), start, windowEnd, today,
        )
        assertEquals(listOf("Active", "Archived with logs"), p.trackers.map { it.name })
        assertEquals(0.0, p.trackers[0].total, 0.0)
        assertEquals(0, p.trackers[0].bestStreak)
    }

    @Test fun payload_trophiesWonInsideTheSeasonOnly() {
        val inside = tracker(1, "Inside", TrackerStatus.COMPLETED, completed = start.plusDays(4), type = TrackerType.GOAL)
        val before = tracker(2, "Before", TrackerStatus.COMPLETED, completed = start.minusDays(1), type = TrackerType.GOAL)
        val after = tracker(3, "After", TrackerStatus.COMPLETED, completed = start.plusDays(12), type = TrackerType.GOAL)
        val logs = listOf(log(1, 1, 60.0), log(1, 4, 40.0), log(1, 8, 99.0)) // the day-8 log came after the finish
        val p = buildSeasonPayload(listOf(inside, before, after), logs, emptyList(), start, windowEnd, today)
        val trophy = p.trophies.single()
        assertEquals("Inside", trophy.name)
        assertEquals(100.0, trophy.total, 0.0)
        assertEquals(start.plusDays(4), trophy.completedDate)
    }

    @Test fun payload_emptySeason() {
        val p = buildSeasonPayload(emptyList(), emptyList(), emptyList(), start, windowEnd, today)
        assertEquals(0, p.daysActive)
        assertTrue(p.trackers.isEmpty() && p.trophies.isEmpty())
    }

    // ---------- JSON ----------

    @Test fun json_roundTrip_withQuotesUnicodeAndDecimals() {
        val original = SeasonPayload(
            daysActive = 12,
            trackers = listOf(
                SeasonTrackerStat(1, "The \"Mom\" Test \\ café", "pages", -16711936, 12.5, 4, false),
                SeasonTrackerStat(2, "Gym", "sessions", 7, 3.0, 2, true),
            ),
            trophies = listOf(SeasonTrophy(9, "Book", "pages", 123, 200.0, LocalDate.of(2026, 3, 4))),
        )
        assertEquals(original, parseSeasonPayload(original.toJson()))
    }

    @Test fun json_emptyPayload_roundTrips() {
        val empty = SeasonPayload(0, emptyList(), emptyList())
        assertEquals(empty, parseSeasonPayload(empty.toJson()))
    }

    // ---------- which summary to show ----------

    private fun withDays(vararg daysByNumber: Pair<Int, Int>): Pair<List<SeasonSummary>, (SeasonSummary) -> Int> {
        val map = daysByNumber.toMap()
        return daysByNumber.map { summary(it.first, start.plusDays(7L * it.first)) } to { s -> map.getValue(s.seasonNumber) }
    }

    @Test fun nextUnseen_showsOldestUnseenWithActivity() {
        val (list, days) = withDays(1 to 3, 2 to 5, 3 to 1)
        val r = nextUnseenSummary(list, lastSeen = 1, activeDays = days)
        assertEquals(2, r.next!!.seasonNumber)
        assertNull(r.skipTo)
    }

    @Test fun nextUnseen_skipsEmptySeasonsAndReportsHowFarToSkip() {
        val (list, days) = withDays(1 to 0, 2 to 0, 3 to 4)
        val r = nextUnseenSummary(list, lastSeen = 0, activeDays = days)
        assertEquals(3, r.next!!.seasonNumber)
        assertEquals(2, r.skipTo)
    }

    @Test fun nextUnseen_allEmpty_showsNothing_skipsToTheLast() {
        val (list, days) = withDays(1 to 0, 2 to 0)
        val r = nextUnseenSummary(list, lastSeen = 0, activeDays = days)
        assertNull(r.next)
        assertEquals(2, r.skipTo)
    }

    @Test fun nextUnseen_nothingUnseen() {
        val (list, days) = withDays(1 to 5, 2 to 5)
        val r = nextUnseenSummary(list, lastSeen = 2, activeDays = days)
        assertNull(r.next)
        assertNull(r.skipTo)
    }
}
