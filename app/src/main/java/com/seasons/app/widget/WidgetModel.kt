package com.seasons.app.widget

import com.seasons.app.data.LogEntry
import com.seasons.app.data.Repository
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerGroup
import com.seasons.app.data.TrackerStatus
import com.seasons.app.domain.Band
import com.seasons.app.ui.HomeRow
import com.seasons.app.ui.buildHomeRow
import com.seasons.app.ui.buildTrackerSummary
import com.seasons.app.ui.charts.BarChartModel
import com.seasons.app.ui.charts.buildDailyChart
import com.seasons.app.ui.dailyTotals
import com.seasons.app.ui.mainText
import com.seasons.app.ui.statusText
import com.seasons.app.ui.streakText
import com.seasons.app.ui.toBands
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

// ---------- What a widget is pointed at ----------

sealed interface WidgetTarget {
    data class OfTracker(val id: Long) : WidgetTarget
    data class OfGroup(val id: Long) : WidgetTarget

    fun encode(): String = when (this) {
        is OfTracker -> "tracker:$id"
        is OfGroup -> "group:$id"
    }

    companion object {
        fun decode(text: String?): WidgetTarget? {
            val parts = text?.split(":") ?: return null
            if (parts.size != 2) return null
            val id = parts[1].toLongOrNull() ?: return null
            return when (parts[0]) {
                "tracker" -> OfTracker(id)
                "group" -> OfGroup(id)
                else -> null
            }
        }
    }
}

/**
 * Each widget keeps its own choice in Glance state. Changing that state is what makes Glance redraw the widget,
 * so the choice and a "refreshed" counter both live there.
 */
object WidgetState {
    val TARGET = androidx.datastore.preferences.core.stringPreferencesKey("target")
    val REFRESH = androidx.datastore.preferences.core.longPreferencesKey("refresh")
}

// ---------- Data the widgets draw ----------

data class SingleWidgetData(
    val name: String,
    val color: Int,
    val headline: String,
    val subline: String?,
    val needLine: String?,
    val progress: Float,
    val status: String,
    val metToday: Boolean,
    val streak: String,
    /** Last 14 days, same data as the Daily chart in the app. */
    val chart: BarChartModel,
)

data class WidgetRow(
    val trackerId: Long,
    val name: String,
    val color: Int,
    val main: String,
    val progress: Float,
    val status: String,
    val metToday: Boolean,
    val streak: String,
)

data class GroupWidgetData(val groupName: String, val color: Int, val rows: List<WidgetRow>)

fun buildSingleData(tracker: Tracker, logs: List<LogEntry>, bands: List<Band>, today: LocalDate): SingleWidgetData {
    val summary = buildTrackerSummary(tracker, logs, bands, today)
    val row = buildHomeRow(tracker, logs, bands, today)
    return SingleWidgetData(
        name = tracker.name,
        color = tracker.color,
        headline = summary.headline,
        subline = summary.subline,
        needLine = summary.needLine,
        progress = summary.progress,
        status = row.statusText(),
        metToday = row.metToday,
        streak = row.streakText(),
        chart = buildDailyChart(tracker, logs.dailyTotals(), bands, today),
    )
}

fun HomeRow.toWidgetRow() = WidgetRow(
    trackerId = tracker.id,
    name = tracker.name,
    color = tracker.color,
    main = mainText(),
    progress = progress,
    status = statusText(),
    metToday = metToday,
    streak = streakText(),
)

/** Active trackers of [group], in their usual order. */
fun buildGroupData(
    group: TrackerGroup,
    trackers: List<Tracker>,
    logs: List<LogEntry>,
    bands: List<com.seasons.app.data.BandHistory>,
    today: LocalDate,
): GroupWidgetData {
    val logsBy = logs.groupBy { it.trackerId }
    val bandsBy = bands.groupBy { it.trackerId }
    val rows = trackers
        .filter { it.groupId == group.id && it.status == TrackerStatus.ACTIVE }
        .map { buildHomeRow(it, logsBy[it.id].orEmpty(), bandsBy[it.id].orEmpty().toBands(), today).toWidgetRow() }
    return GroupWidgetData(group.name, group.color, rows)
}

/** Reads the current data. Null if the tracker or group no longer exists. */
object WidgetLoader {
    suspend fun single(repo: Repository, trackerId: Long, today: LocalDate = LocalDate.now()): SingleWidgetData? {
        val tracker = repo.getTracker(trackerId) ?: return null
        val logs = repo.observeLogs(trackerId).first()
        val bands = repo.observeBands(trackerId).first().toBands()
        return buildSingleData(tracker, logs, bands, today)
    }

    suspend fun group(repo: Repository, groupId: Long, today: LocalDate = LocalDate.now()): GroupWidgetData? {
        val group = repo.observeGroups().first().firstOrNull { it.id == groupId } ?: return null
        return buildGroupData(
            group,
            repo.observeTrackers().first(),
            repo.observeAllLogs().first(),
            repo.observeAllBands().first(),
            today,
        )
    }
}

// ---------- Midnight ----------

/** Time from [now] to the next local midnight. Uses the zone's real calendar, so DST days are 23 or 25 hours. */
fun millisUntilNextMidnight(now: ZonedDateTime): Long {
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    return Duration.between(now, nextMidnight).toMillis()
}
