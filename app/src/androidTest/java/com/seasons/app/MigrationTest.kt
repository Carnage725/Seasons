package com.seasons.app

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.seasons.app.data.AppDatabase
import com.seasons.app.data.MIGRATION_2_3
import com.seasons.app.data.Migration1To2
import com.seasons.app.data.Repository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Database migrations, run on the phone. Each one builds the OLD schema with real rows, migrates, and checks the rows.
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(Migration1To2()),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(dbName)
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Long =
        db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getLong(0) }

    @Test fun v1ToV2_keepsEverything_andDropsGroups() {
        helper.createDatabase(dbName, 1).apply {
            // Two groups, three trackers (two in a group, one not), bands, logs, settings, a season summary.
            execSQL("INSERT INTO groups (id, name, color, sortOrder) VALUES (1, 'Study', 5, 0), (2, 'Fit', 6, 1)")
            execSQL(
                "INSERT INTO trackers (id, name, unit, type, color, groupId, target, deadline, bandPeriod, startDate, status, completedDate, sortOrder) VALUES " +
                    "(1, 'Mom Test', 'pages', 'GOAL', 11, 1, 200.0, 20400, 'DAILY', 20300, 'ACTIVE', NULL, 0), " +
                    "(2, 'Run', 'laps', 'ONGOING', 12, 2, NULL, NULL, 'WEEKLY', 20290, 'ARCHIVED', NULL, 1), " +
                    "(3, 'Done', 'x', 'GOAL', 13, NULL, 10.0, NULL, 'NONE', 20310, 'COMPLETED', 20320, 2)",
            )
            execSQL("INSERT INTO band_history (id, trackerId, effectiveFrom, lower, upper) VALUES (1, 1, 20300, 3.0, 6.0), (2, 2, 20290, 4.0, 6.0)")
            execSQL(
                "INSERT INTO log_entries (id, trackerId, date, amount, createdAt) VALUES " +
                    "(1, 1, 20330, 12.5, 1000), (2, 1, 20331, 4.0, 2000), (3, 2, 20331, 1.0, 3000), (4, 3, 20315, 10.0, 4000)",
            )
            execSQL("INSERT INTO settings (id, seasonStartDate, seasonLength, lastSeasonSummarySeen) VALUES (1, 20280, 77, 2)")
            execSQL("INSERT INTO season_summaries (id, seasonNumber, startDate, endDate, length, payloadJson) VALUES (1, 1, 20280, 20356, 77, '{}')")
            close()
        }

        // Runs the migration and checks the result matches schema 2 exactly.
        val db = helper.runMigrationsAndValidate(dbName, 2, true)

        // Every row must survive. The cascade foreign keys must not have wiped the logs and bands.
        assertEquals(3, count(db, "trackers"))
        assertEquals(2, count(db, "band_history"))
        assertEquals(4, count(db, "log_entries"))
        assertEquals(1, count(db, "settings"))
        assertEquals(1, count(db, "season_summaries"))
        db.query("SELECT name, unit, type, color, target, deadline, bandPeriod, startDate, status, completedDate, sortOrder FROM trackers WHERE id = 3").use {
            it.moveToFirst()
            assertEquals("Done", it.getString(0))
            assertEquals("COMPLETED", it.getString(8))
            assertEquals(20320L, it.getLong(9))
            assertEquals(2, it.getInt(10))
        }
        db.query("SELECT sum(amount) FROM log_entries WHERE trackerId = 1").use { it.moveToFirst(); assertEquals(16.5, it.getDouble(0), 0.0) }

        // groups is gone, and so is the groupId column.
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'groups'").use { assertEquals(0, it.count) }
        db.query("SELECT name FROM pragma_table_info('trackers') WHERE name = 'groupId'").use { assertEquals(0, it.count) }
        db.query("PRAGMA foreign_key_check").use { assertEquals("foreign key broken after migration", 0, it.count) }
        db.close()
    }

    @Test fun v1ToV2_realDatabaseOpensWithTheAppsOwnBuilder() = runBlocking {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO groups (id, name, color, sortOrder) VALUES (1, 'Study', 5, 0)")
            execSQL(
                "INSERT INTO trackers (id, name, unit, type, color, groupId, target, deadline, bandPeriod, startDate, status, completedDate, sortOrder) " +
                    "VALUES (1, 'Mom Test', 'pages', 'GOAL', 11, 1, 200.0, NULL, 'NONE', 20300, 'ACTIVE', NULL, 0)",
            )
            execSQL("INSERT INTO log_entries (id, trackerId, date, amount, createdAt) VALUES (1, 1, 20330, 12.5, 1000)")
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).addMigrations(MIGRATION_2_3).build()
        val repo = Repository(db)
        val tracker = repo.getTracker(1)
        assertNotNull(tracker)
        assertEquals("Mom Test", tracker!!.name)
        assertEquals(12.5, repo.observeLogs(1).first().single().amount, 0.0)
        // New writes still work after the migration.
        repo.addLog(1, java.time.LocalDate.ofEpochDay(20330), 1.0, java.time.LocalDate.ofEpochDay(20331))
        assertEquals(2, repo.observeLogs(1).first().size)
        db.close()
    }

    @Test fun v2ToV3_replacedBecomesArchived_nothingElseChanges() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                "INSERT INTO trackers (id, name, unit, type, color, target, deadline, bandPeriod, startDate, status, completedDate, sortOrder) VALUES " +
                    "(1, 'Restarted', 'x', 'ONGOING', 1, NULL, NULL, 'DAILY', 20300, 'REPLACED', NULL, 0), " +
                    "(2, 'Active', 'x', 'GOAL', 2, 10.0, NULL, 'NONE', 20300, 'ACTIVE', NULL, 1), " +
                    "(3, 'Archived', 'x', 'GOAL', 3, 10.0, NULL, 'NONE', 20300, 'ARCHIVED', NULL, 2), " +
                    "(4, 'Trophy', 'x', 'GOAL', 4, 10.0, NULL, 'NONE', 20300, 'COMPLETED', 20310, 3)",
            )
            execSQL("INSERT INTO log_entries (id, trackerId, date, amount, createdAt) VALUES (1, 1, 20330, 2.0, 1), (2, 2, 20330, 3.0, 2)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)
        val statuses = db.query("SELECT id, status FROM trackers ORDER BY id").use {
            buildList { while (it.moveToNext()) add(it.getLong(0) to it.getString(1)) }
        }
        assertEquals(listOf(1L to "ARCHIVED", 2L to "ACTIVE", 3L to "ARCHIVED", 4L to "COMPLETED"), statuses)
        assertEquals(2, count(db, "log_entries"))
        db.close()
    }

    @Test fun v1ToV3_fullChain_opensWithTheAppsBuilder() = runBlocking {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO groups (id, name, color, sortOrder) VALUES (1, 'Study', 5, 0)")
            execSQL(
                "INSERT INTO trackers (id, name, unit, type, color, groupId, target, deadline, bandPeriod, startDate, status, completedDate, sortOrder) VALUES " +
                    "(1, 'Restarted', 'x', 'ONGOING', 1, 1, NULL, NULL, 'DAILY', 20300, 'REPLACED', NULL, 0)",
            )
            execSQL("INSERT INTO log_entries (id, trackerId, date, amount, createdAt) VALUES (1, 1, 20330, 2.0, 1)")
            close()
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).addMigrations(MIGRATION_2_3).build()
        val repo = Repository(db)
        assertEquals("ARCHIVED", repo.getTracker(1)!!.status.name)
        assertEquals(1, repo.observeLogs(1).first().size)
        db.close()
    }

    @Test fun v3ToV4_addsTimeColumnsAndTimerTable_keepsEveryRow() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO trackers (id, name, unit, type, color, target, deadline, bandPeriod, startDate, status, completedDate, sortOrder) VALUES " +
                    "(1, 'Mom Test', 'pages', 'GOAL', 11, 200.0, NULL, 'NONE', 20300, 'ACTIVE', NULL, 0)",
            )
            execSQL("INSERT INTO band_history (id, trackerId, effectiveFrom, lower, upper) VALUES (1, 1, 20300, 3.0, 6.0)")
            execSQL("INSERT INTO log_entries (id, trackerId, date, amount, createdAt) VALUES (1, 1, 20330, 12.5, 1000), (2, 1, 20331, 4.0, 2000)")
            execSQL("INSERT INTO settings (id, seasonStartDate, seasonLength, lastSeasonSummarySeen) VALUES (1, 20280, 77, 2)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true)

        assertEquals(1, count(db, "trackers"))
        assertEquals(1, count(db, "band_history"))
        assertEquals(2, count(db, "log_entries"))
        assertEquals(1, count(db, "settings"))
        assertEquals(0, count(db, "running_timer"))
        // Old logs are plain entries: both time columns are NULL.
        db.query("SELECT amount, startTime, endTime FROM log_entries ORDER BY id").use {
            it.moveToFirst()
            assertEquals(12.5, it.getDouble(0), 0.0)
            assertEquals(true, it.isNull(1))
            assertEquals(true, it.isNull(2))
        }
        db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        db.close()
    }

    @Test fun v1ToV4_fullChain_opensWithTheAppsBuilder_andStopwatchWorks() = runBlocking {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO groups (id, name, color, sortOrder) VALUES (1, 'Study', 5, 0)")
            execSQL(
                "INSERT INTO trackers (id, name, unit, type, color, groupId, target, deadline, bandPeriod, startDate, status, completedDate, sortOrder) VALUES " +
                    "(1, 'Mom Test', 'pages', 'GOAL', 11, 1, 200.0, NULL, 'NONE', 20300, 'ACTIVE', NULL, 0)",
            )
            execSQL("INSERT INTO log_entries (id, trackerId, date, amount, createdAt) VALUES (1, 1, 20330, 12.5, 1000)")
            close()
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).addMigrations(MIGRATION_2_3).build()
        val repo = Repository(db)
        assertEquals(12.5, repo.observeLogs(1).first().single().amount, 0.0)
        val now = java.time.LocalDate.ofEpochDay(20331).atTime(12, 0).atZone(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(true, repo.startTimer(1, now - 600_000))
        repo.finishTimer(now - 600_000, now, 2.0, now, java.time.ZoneId.of("UTC"))
        assertEquals(2, repo.observeLogs(1).first().size)
        db.close()
    }
}
