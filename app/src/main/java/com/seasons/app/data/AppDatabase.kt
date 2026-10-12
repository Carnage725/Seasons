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

/** Version 2: the Groups feature is gone. Trackers, logs, bands, settings and summaries are kept. */
@DeleteTable(tableName = "groups")
@DeleteColumn(tableName = "trackers", columnName = "groupId")
class Migration1To2 : AutoMigrationSpec

@Database(
    entities = [
        Tracker::class,
        BandHistory::class,
        LogEntry::class,
        Settings::class,
        SeasonSummary::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = Migration1To2::class)],
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
            ).build().also { instance = it }
        }
    }
}
