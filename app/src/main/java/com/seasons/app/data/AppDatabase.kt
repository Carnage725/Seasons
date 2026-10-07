package com.seasons.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Tracker::class,
        BandHistory::class,
        LogEntry::class,
        TrackerGroup::class,
        Settings::class,
        SeasonSummary::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao
    abstract fun bandDao(): BandDao
    abstract fun logDao(): LogDao
    abstract fun groupDao(): GroupDao
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
