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
    version = 3,
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
            ).addMigrations(MIGRATION_2_3).build().also { instance = it }
        }
    }
}
