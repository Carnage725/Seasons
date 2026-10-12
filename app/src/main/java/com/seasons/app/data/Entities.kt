package com.seasons.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class TrackerType { GOAL, ONGOING }
enum class BandPeriod { DAILY, WEEKLY, NONE }
enum class TrackerStatus { ACTIVE, ARCHIVED, COMPLETED }

@Entity(tableName = "trackers")
data class Tracker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String,
    val type: TrackerType,
    val color: Int,
    val target: Double? = null,
    val deadline: LocalDate? = null,
    val bandPeriod: BandPeriod = BandPeriod.NONE,
    val startDate: LocalDate,
    val status: TrackerStatus = TrackerStatus.ACTIVE,
    val completedDate: LocalDate? = null,
    val sortOrder: Int = 0,
)

// One row per band change. The band for a date is the row with the latest effectiveFrom <= date.
@Entity(
    tableName = "band_history",
    foreignKeys = [
        ForeignKey(
            entity = Tracker::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["trackerId", "effectiveFrom"], unique = true)],
)
data class BandHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackerId: Long,
    val effectiveFrom: LocalDate,
    val lower: Double,
    val upper: Double,
)

@Entity(
    tableName = "log_entries",
    foreignKeys = [
        ForeignKey(
            entity = Tracker::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["trackerId", "date"])],
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackerId: Long,
    val date: LocalDate,
    val amount: Double,
    val createdAt: Long,
    /** Stopwatch entries only: epoch millis. Both set, or both null (a plain +1, +5 or Custom entry). */
    val startTime: Long? = null,
    val endTime: Long? = null,
) {
    val isTimed: Boolean get() = startTime != null && endTime != null
}

/** The timer that is running now. At most one row, id = 1. Deleting its tracker deletes it too. */
@Entity(
    tableName = "running_timer",
    foreignKeys = [
        ForeignKey(
            entity = Tracker::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackerId")],
)
data class RunningTimer(
    @PrimaryKey val id: Int = 1,
    val trackerId: Long,
    val startTime: Long,
)

// Always exactly one row, id = 1.
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    val seasonStartDate: LocalDate,
    val seasonLength: Int = DEFAULT_SEASON_LENGTH,
    val lastSeasonSummarySeen: Int = 0,
) {
    companion object {
        const val DEFAULT_SEASON_LENGTH = 77
        const val MIN_SEASON_LENGTH = 7
        const val MAX_SEASON_LENGTH = 365
    }
}

@Entity(tableName = "season_summaries")
data class SeasonSummary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seasonNumber: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val length: Int,
    val payloadJson: String,
)
