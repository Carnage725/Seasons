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

        val newId = repo.startFresh(repo.getTracker(id)!!, today)

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

        val old = repo.getTracker(id)!!
        assertEquals(TrackerStatus.ARCHIVED, old.status)
        assertEquals(1, repo.observeLogs(id).first().size)
    }

    @Test fun startFresh_goalWithoutBand_hasNoBandRows() = runBlocking {
        val id = repo.createTracker(goal(), null, null)
        val newId = repo.startFresh(repo.getTracker(id)!!, today)
        assertTrue(repo.observeBands(newId).first().isEmpty())
        assertEquals(200.0, repo.getTracker(newId)!!.target!!, 0.0)
    }
}
