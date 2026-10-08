package com.seasons.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seasons.app.data.AppDatabase
import com.seasons.app.data.BandPeriod
import com.seasons.app.data.Repository
import com.seasons.app.data.Settings
import com.seasons.app.data.Tracker
import com.seasons.app.data.parseSeasonPayload
import com.seasons.app.data.TrackerStatus
import com.seasons.app.data.TrackerType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

// Runs on the phone against a real (in-memory) Room database.
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private val today = LocalDate.of(2026, 10, 7)

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = Repository(db)
    }

    @After fun tearDown() = db.close()

    private fun goal(start: LocalDate = today.minusDays(10)) = Tracker(
        name = "Book", unit = "pages", type = TrackerType.GOAL, color = 0, target = 200.0, startDate = start,
    )

    private fun ongoing() = Tracker(
        name = "Run", unit = "laps", type = TrackerType.ONGOING, color = 0,
        bandPeriod = BandPeriod.DAILY, startDate = today.minusDays(10),
    )

    private fun expectFailure(block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun create_goalWithoutBand_hasNoBandRows() = runBlocking {
        val id = repo.createTracker(goal(), null, null)
        assertTrue(repo.observeBands(id).first().isEmpty())
        assertEquals("Book", repo.getTracker(id)!!.name)
    }

    @Test fun create_withBand_insertsFirstBandRowAtStartDate() = runBlocking {
        val t = ongoing()
        val id = repo.createTracker(t, 3.0, 6.0)
        val bands = repo.observeBands(id).first()
        assertEquals(1, bands.size)
        assertEquals(t.startDate, bands[0].effectiveFrom)
        assertEquals(3.0, bands[0].lower, 0.0)
        assertEquals(6.0, bands[0].upper, 0.0)
    }

    @Test fun changeBand_addsRow_andSameDateReplaces() = runBlocking {
        val id = repo.createTracker(ongoing(), 3.0, 6.0)
        repo.changeBand(id, today, 5.0, 8.0)
        assertEquals(2, repo.observeBands(id).first().size)
        repo.changeBand(id, today, 6.0, 9.0)
        val bands = repo.observeBands(id).first()
        assertEquals(2, bands.size)
        assertEquals(6.0, bands.last().lower, 0.0)
        assertEquals(3.0, bands.first().lower, 0.0) // history untouched
    }

    @Test fun addLog_isAdditive_andOrderedNewestFirst() = runBlocking {
        val id = repo.createTracker(goal(), null, null)
        repo.addLog(id, today, 5.0, today)
        repo.addLog(id, today, 2.0, today)
        repo.addLog(id, today.minusDays(1), 1.0, today)
        val logs = repo.observeLogs(id).first()
        assertEquals(3, logs.size)
        assertEquals(today.minusDays(1), logs.last().date)
        assertEquals(7.0, logs.filter { it.date == today }.sumOf { it.amount }, 0.0)
    }

    @Test fun addLog_rejectsBadInput() = runBlocking {
        val id = repo.createTracker(goal(), null, null)
        expectFailure { repo.addLog(id, today, 0.0, today) }
        expectFailure { repo.addLog(id, today, -1.0, today) }
        expectFailure { repo.addLog(id, today.plusDays(1), 1.0, today) }
        expectFailure { repo.addLog(id, today.minusDays(11), 1.0, today) } // before startDate
        assertTrue(repo.observeLogs(id).first().isEmpty())
    }

    @Test fun addLog_backfillOnStartDateAndTodayAllowed() = runBlocking {
        val t = goal()
        val id = repo.createTracker(t, null, null)
        repo.addLog(id, t.startDate, 1.0, today)
        repo.addLog(id, today, 1.0, today)
        assertEquals(2, repo.observeLogs(id).first().size)
    }

    @Test fun editLog_changesAmountAndDate_andValidates() = runBlocking {
        val id = repo.createTracker(goal(), null, null)
        val entryId = repo.addLog(id, today, 5.0, today)
        val entry = repo.getLog(entryId)!!
        repo.editLog(entry, today.minusDays(2), 9.0, today)
        val edited = repo.getLog(entryId)!!
        assertEquals(today.minusDays(2), edited.date)
        assertEquals(9.0, edited.amount, 0.0)
        expectFailure { repo.editLog(edited, today.plusDays(1), 9.0, today) }
        expectFailure { repo.editLog(edited, today, 0.0, today) }
    }

    @Test fun deleteLog_removesOnlyThatEntry() = runBlocking {
        val id = repo.createTracker(goal(), null, null)
        val a = repo.addLog(id, today, 5.0, today)
        repo.addLog(id, today, 2.0, today)
        repo.deleteLog(repo.getLog(a)!!)
        val logs = repo.observeLogs(id).first()
        assertEquals(1, logs.size)
        assertEquals(2.0, logs[0].amount, 0.0)
    }

    @Test fun deleteTracker_cascadesLogsAndBands_andLeavesOthers() = runBlocking {
        val id = repo.createTracker(ongoing(), 3.0, 6.0)
        val other = repo.createTracker(goal(), null, null)
        repo.addLog(id, today, 4.0, today)
        repo.addLog(other, today, 1.0, today)

        repo.deleteTracker(repo.getTracker(id)!!)

        assertNull(repo.getTracker(id))
        assertTrue(repo.observeLogs(id).first().isEmpty())
        assertTrue(repo.observeBands(id).first().isEmpty())
        assertNotNull(repo.getTracker(other))
        assertEquals(1, repo.observeLogs(other).first().size)
    }

    @Test fun markCompleted_setsStatusAndDate() = runBlocking {
        val id = repo.createTracker(goal(), null, null)
        repo.markCompleted(repo.getTracker(id)!!, today)
        val t = repo.getTracker(id)!!
        assertEquals(TrackerStatus.COMPLETED, t.status)
        assertEquals(today, t.completedDate)
    }

    @Test fun settings_roundTrip_andLengthValidation() = runBlocking {
        assertNull(repo.observeSettings().first())
        repo.saveSettings(Settings(seasonStartDate = today, seasonLength = 90))
        assertEquals(90, repo.observeSettings().first()!!.seasonLength)
        expectFailure { repo.saveSettings(Settings(seasonStartDate = today, seasonLength = 6)) }
        expectFailure { repo.saveSettings(Settings(seasonStartDate = today, seasonLength = 366)) }
        assertEquals(90, repo.observeSettings().first()!!.seasonLength)
    }

    // ---------- groups ----------

    @Test fun groups_addGetIncreasingSortOrder_andMoveIsSaved() = runBlocking {
        val a = repo.addGroup("A", 1)
        val b = repo.addGroup("B", 2)
        val c = repo.addGroup("C", 3)
        assertEquals(listOf(a, b, c), repo.observeGroups().first().map { it.id })

        repo.moveGroup(c, up = true)
        assertEquals(listOf(a, c, b), repo.observeGroups().first().map { it.id })
        repo.moveGroup(a, up = true) // already first: no change
        assertEquals(listOf(a, c, b), repo.observeGroups().first().map { it.id })
    }

    @Test fun groups_renameRecolor() = runBlocking {
        val id = repo.addGroup("Old", 1)
        repo.updateGroup(repo.observeGroups().first().first { it.id == id }.copy(name = "New", color = 9))
        val g = repo.observeGroups().first().first { it.id == id }
        assertEquals("New", g.name)
        assertEquals(9, g.color)
    }

    @Test fun groups_trackerAssignment_andDeleteGroupKeepsTrackers() = runBlocking {
        val g = repo.addGroup("Study", 1)
        val keep = repo.createTracker(goal().copy(groupId = g), null, null)
        val other = repo.createTracker(goal(), null, null)
        repo.addLog(keep, today, 3.0, today)
        assertEquals(g, repo.getTracker(keep)!!.groupId)
        assertNull(repo.getTracker(other)!!.groupId)

        repo.deleteGroup(repo.observeGroups().first().first { it.id == g })

        assertTrue(repo.observeGroups().first().isEmpty())
        assertNotNull(repo.getTracker(keep))
        assertNull(repo.getTracker(keep)!!.groupId)
        assertEquals(1, repo.observeLogs(keep).first().size)
    }

    // ---------- archive ----------

    @Test fun archive_thenContinue_keepsEverything() = runBlocking {
        val id = repo.createTracker(ongoing(), 3.0, 6.0)
        repo.addLog(id, today, 4.0, today)
        repo.archiveTracker(repo.getTracker(id)!!)
        assertEquals(TrackerStatus.ARCHIVED, repo.getTracker(id)!!.status)

        repo.unarchiveContinue(repo.getTracker(id)!!)

        val t = repo.getTracker(id)!!
        assertEquals(TrackerStatus.ACTIVE, t.status)
        assertEquals(1, repo.observeLogs(id).first().size)
        assertEquals(1, repo.observeBands(id).first().size)
    }

    @Test fun archive_startFresh_makesNewTrackerAndKeepsOldAsHistory() = runBlocking {
        val g = repo.addGroup("Fitness", 1)
        val id = repo.createTracker(ongoing().copy(groupId = g), 3.0, 6.0)
        repo.changeBand(id, today.minusDays(2), 5.0, 8.0) // latest band at "today"
        repo.addLog(id, today, 4.0, today)
        repo.archiveTracker(repo.getTracker(id)!!)

        val newId = repo.startFresh(repo.getTracker(id)!!, today)!!

        assertTrue(newId != id)
        val fresh = repo.getTracker(newId)!!
        assertEquals(TrackerStatus.ACTIVE, fresh.status)
        assertEquals(today, fresh.startDate)
        assertEquals("Run", fresh.name)
        assertEquals(g, fresh.groupId)
        assertTrue(repo.observeLogs(newId).first().isEmpty())
        val bands = repo.observeBands(newId).first()
        assertEquals(1, bands.size)
        assertEquals(5.0, bands[0].lower, 0.0)
        assertEquals(8.0, bands[0].upper, 0.0)
        assertEquals(today, bands[0].effectiveFrom)

        // The old one is kept as history but leaves the Archive list.
        val old = repo.getTracker(id)!!
        assertEquals(TrackerStatus.REPLACED, old.status)
        assertEquals(1, repo.observeLogs(id).first().size)
    }

    @Test fun startFresh_twice_createsOnlyOneNewTracker() = runBlocking {
        val id = repo.createTracker(goal(), null, null)
        repo.archiveTracker(repo.getTracker(id)!!)
        val stale = repo.getTracker(id)!! // what a second tap would still be holding

        val first = repo.startFresh(stale, today)
        val second = repo.startFresh(stale, today)

        assertNotNull(first)
        assertNull(second)
        val all = repo.observeTrackers().first()
        assertEquals(2, all.size) // the original (REPLACED) and one fresh copy
        assertEquals(1, all.count { it.status == TrackerStatus.ACTIVE })
        assertEquals(0, all.count { it.status == TrackerStatus.ARCHIVED })
    }

    @Test fun unarchiveContinue_ignoresATrackerThatIsNotArchived() = runBlocking {
        val id = repo.createTracker(goal(), null, null)
        repo.archiveTracker(repo.getTracker(id)!!)
        val stale = repo.getTracker(id)!!
        repo.startFresh(stale, today) // old becomes REPLACED
        repo.unarchiveContinue(stale)
        assertEquals(TrackerStatus.REPLACED, repo.getTracker(id)!!.status)
    }

    @Test fun startFresh_goalWithoutBand_hasNoBandRows() = runBlocking {
        val id = repo.createTracker(goal(), null, null)
        repo.archiveTracker(repo.getTracker(id)!!)
        val newId = repo.startFresh(repo.getTracker(id)!!, today)!!
        assertTrue(repo.observeBands(newId).first().isEmpty())
        assertEquals(200.0, repo.getTracker(newId)!!.target!!, 0.0)
    }

    // ---------- seasons ----------

    @Test fun firstSettings_saved_ignoredIfAlreadyThere_andValidated() = runBlocking {
        repo.saveFirstSettings(today.minusDays(3), 30)
        repo.saveFirstSettings(today, 90) // already set up: ignored
        val s = repo.observeSettings().first()!!
        assertEquals(today.minusDays(3), s.seasonStartDate)
        assertEquals(30, s.seasonLength)
        expectFailure { repo.saveFirstSettings(today, 5) }
    }

    @Test fun closeFinishedSeasons_forSeveralLengths_isIdempotent() = runBlocking {
        for (len in listOf(7, 30, 77, 90)) {
            db.clearAllTables()
            val seasonStart = today.minusDays(2L * len + 3)
            repo.saveFirstSettings(seasonStart, len)
            val id = repo.createTracker(goal(start = seasonStart), null, null)
            repo.addLog(id, seasonStart.plusDays(1), 5.0, today) // inside season 1
            repo.addLog(id, seasonStart.plusDays(len + 2L), 7.0, today) // inside season 2

            repo.closeFinishedSeasons(today)
            repo.closeFinishedSeasons(today) // second call must not add anything

            val list = repo.observeSeasonSummaries().first().sortedBy { it.seasonNumber }
            assertEquals("len $len", 2, list.size)
            assertEquals(listOf(1, 2), list.map { it.seasonNumber })
            assertEquals(seasonStart, list[0].startDate)
            assertEquals(seasonStart.plusDays(len - 1L), list[0].endDate)
            assertEquals(len, list[0].length)
            val first = parseSeasonPayload(list[0].payloadJson)
            val second = parseSeasonPayload(list[1].payloadJson)
            assertEquals(1, first.daysActive)
            assertEquals(5.0, first.trackers.single().total, 0.0)
            assertEquals(7.0, second.trackers.single().total, 0.0)
        }
    }

    @Test fun closeFinishedSeasons_snapshotIsFrozen() = runBlocking {
        val seasonStart = today.minusDays(20)
        repo.saveFirstSettings(seasonStart, 7)
        val id = repo.createTracker(goal(start = seasonStart), null, null)
        repo.addLog(id, seasonStart, 5.0, today)
        repo.closeFinishedSeasons(today)
        val before = repo.observeSeasonSummaries().first().first { it.startDate == seasonStart }.payloadJson

        repo.addLog(id, seasonStart.plusDays(1), 50.0, today) // backfill after the season ended
        repo.closeFinishedSeasons(today)

        assertEquals(before, repo.observeSeasonSummaries().first().first { it.startDate == seasonStart }.payloadJson)
    }

    @Test fun closeFinishedSeasons_noSettings_doesNothing() = runBlocking {
        repo.closeFinishedSeasons(today)
        assertTrue(repo.observeSeasonSummaries().first().isEmpty())
    }

    @Test fun changeSeason_midSeason_snapshotsPartial_thenNumberingContinues() = runBlocking {
        val seasonStart = today.minusDays(20) // day 21 of season 1, length 77
        repo.saveFirstSettings(seasonStart, 77)
        val id = repo.createTracker(goal(start = seasonStart), null, null)
        repo.addLog(id, seasonStart.plusDays(2), 4.0, today)
        repo.addLog(id, today, 9.0, today) // belongs to the new season

        repo.changeSeason(30, today, today)

        val s = repo.observeSettings().first()!!
        assertEquals(today, s.seasonStartDate)
        assertEquals(30, s.seasonLength)
        val partial = repo.observeSeasonSummaries().first().single()
        assertEquals(1, partial.seasonNumber)
        assertEquals(seasonStart, partial.startDate)
        assertEquals(today.minusDays(1), partial.endDate)
        assertEquals(77, partial.length)
        val payload = parseSeasonPayload(partial.payloadJson)
        assertEquals(4.0, payload.trackers.single().total, 0.0) // today's 9 is not in it
        assertEquals(1, payload.daysActive)

        // 35 days later the first 30-day season has ended: it becomes season 2.
        val later = today.plusDays(35)
        repo.closeFinishedSeasons(later)
        val all = repo.observeSeasonSummaries().first().sortedBy { it.seasonNumber }
        assertEquals(listOf(1, 2), all.map { it.seasonNumber })
        assertEquals(today, all[1].startDate)
        assertEquals(30, all[1].length)
        assertEquals(partial.payloadJson, all[0].payloadJson) // past snapshot untouched
    }

    @Test fun changeSeason_onTheFirstDayOfASeason_hasNothingToSnapshot() = runBlocking {
        repo.saveFirstSettings(today, 77)
        repo.changeSeason(90, today, today)
        assertTrue(repo.observeSeasonSummaries().first().isEmpty())
        assertEquals(90, repo.observeSettings().first()!!.seasonLength)
    }

    @Test fun changeSeason_closesSeasonsThatAlreadyEndedFirst() = runBlocking {
        val seasonStart = today.minusDays(40) // length 7: seasons 1-5 are over, season 6 started 5 days ago... day 6 of season 6
        repo.saveFirstSettings(seasonStart, 7)
        repo.changeSeason(30, today, today)
        val all = repo.observeSeasonSummaries().first().sortedBy { it.seasonNumber }
        // today = day 41 -> season 6, day 6. Seasons 1-5 finished + 1 partial (6).
        assertEquals(listOf(1, 2, 3, 4, 5, 6), all.map { it.seasonNumber })
        assertEquals(today.minusDays(1), all.last().endDate)
    }

    @Test fun changeSeason_rejectsBadInput() = runBlocking {
        repo.saveFirstSettings(today.minusDays(20), 77)
        expectFailure { repo.changeSeason(6, today, today) }
        expectFailure { repo.changeSeason(30, today.plusDays(1), today) }
        expectFailure { repo.changeSeason(30, today.minusDays(21), today) } // before the running season began
        assertEquals(77, repo.observeSettings().first()!!.seasonLength)
        assertTrue(repo.observeSeasonSummaries().first().isEmpty())
    }

    @Test fun markSummarySeen_neverMovesBackwards() = runBlocking {
        repo.saveFirstSettings(today, 77)
        repo.markSummarySeen(3)
        repo.markSummarySeen(1)
        assertEquals(3, repo.observeSettings().first()!!.lastSeasonSummarySeen)
    }
}
