package com.seasons.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {
    @Query("SELECT * FROM trackers ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Tracker>>

    @Query("SELECT * FROM trackers WHERE id = :id")
    fun observe(id: Long): Flow<Tracker?>

    @Query("SELECT * FROM trackers WHERE id = :id")
    suspend fun get(id: Long): Tracker?

    @Insert
    suspend fun insert(tracker: Tracker): Long

    @Update
    suspend fun update(tracker: Tracker)

    // Logs and band rows go with it (ON DELETE CASCADE).
    @Delete
    suspend fun delete(tracker: Tracker)
}

@Dao
interface BandDao {
    @Query("SELECT * FROM band_history WHERE trackerId = :trackerId ORDER BY effectiveFrom")
    fun observeFor(trackerId: Long): Flow<List<BandHistory>>

    @Query("SELECT * FROM band_history WHERE trackerId = :trackerId ORDER BY effectiveFrom")
    suspend fun getFor(trackerId: Long): List<BandHistory>

    @Query("SELECT * FROM band_history ORDER BY trackerId, effectiveFrom")
    fun observeAll(): Flow<List<BandHistory>>

    // Same tracker + same effectiveFrom replaces the old row (unique index).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(band: BandHistory): Long
}

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entries WHERE trackerId = :trackerId ORDER BY date DESC, createdAt DESC")
    fun observeFor(trackerId: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries")
    fun observeAll(): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE id = :id")
    suspend fun get(id: Long): LogEntry?

    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Update
    suspend fun update(entry: LogEntry)

    @Delete
    suspend fun delete(entry: LogEntry)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<TrackerGroup>>

    @Query("SELECT * FROM groups ORDER BY sortOrder, id")
    suspend fun getAll(): List<TrackerGroup>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM groups")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(group: TrackerGroup): Long

    @Update
    suspend fun update(group: TrackerGroup)

    // Trackers in the group stay; their groupId becomes null (ON DELETE SET NULL).
    @Delete
    suspend fun delete(group: TrackerGroup)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<Settings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: Settings)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(settings: Settings)
}

@Dao
interface SeasonSummaryDao {
    @Query("SELECT * FROM season_summaries ORDER BY startDate DESC")
    fun observeAll(): Flow<List<SeasonSummary>>

    @Insert
    suspend fun insert(summary: SeasonSummary): Long
}
