package com.seasons.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 2: the Groups feature is gone. Trackers, logs, bands, settings and summaries are kept. */
@DeleteTable(tableName = "groups")
@DeleteColumn(tableName = "trackers", columnName = "groupId")
class Migration1To2 : AutoMigrationSpec

/** Version 5: removes the stopwatch. Logged amounts, dates and everything else are kept. */
@DeleteTable(tableName = "running_timer")
@DeleteColumn.Entries(
    DeleteColumn(tableName = "log_entries", columnName = "startTime"),
    DeleteColumn(tableName = "log_entries", columnName = "endTime"),
)
class Migration4To5 : AutoMigrationSpec

/** Version 6: bands have only a minimum. The maximum ("upper") is dropped. Minimums, dates and everything else are kept. */
@DeleteColumn(tableName = "band_history", columnName = "upper")
class Migration5To6 : AutoMigrationSpec

/** Version 3: the REPLACED status is gone. Those trackers (restarted with "Start fresh") become plain archived ones. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE trackers SET status = 'ARCHIVED' WHERE status = 'REPLACED'")
    }
}

@Database(
    entities = [
        Tracker::class,
        BandHistory::class,
        LogEntry::class,
        Settings::class,
        SeasonSummary::class,
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = Migration1To2::class),
        // Version 4: stopwatch. Two nullable columns on log_entries and the running_timer table. Nothing is changed or lost.
        AutoMigration(from = 3, to = 4),
        // Version 5: the stopwatch is gone again. The time columns and the timer table are dropped. Amounts stay.
        AutoMigration(from = 4, to = 5, spec = Migration4To5::class),
        // Version 6: band maximum dropped.
        AutoMigration(from = 5, to = 6, spec = Migration5To6::class),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao
    abstract fun bandDao(): BandDao
    abstract fun logDao(): LogDao
    abstract fun settingsDao(): SettingsDao
    abstract fun seasonSummaryDao(): SeasonSummaryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "seasons.db",
            ).addMigrations(MIGRATION_2_3).build().also { instance = it }
        }
    }
}
