package com.seasons.app.data

import com.seasons.app.domain.Band
import com.seasons.app.domain.dailyStreaks
import com.seasons.app.domain.seasonEndDate
import com.seasons.app.domain.seasonNumber
import com.seasons.app.domain.seasonStartDate
import com.seasons.app.domain.weeklyStreaks
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

// Season snapshots. Pure (apart from org.json), so the rules are unit-tested.

data class SeasonTrackerStat(
    val trackerId: Long,
    val name: String,
    val unit: String,
    val color: Int,
    val total: Double,
    /** Best streak inside the season. Weeks for weekly-band trackers, otherwise days. */
    val bestStreak: Int,
    val weekly: Boolean,
)

data class SeasonTrophy(
    val trackerId: Long,
    val name: String,
    val unit: String,
    val color: Int,
    val total: Double,
    val completedDate: LocalDate,
)

data class SeasonPayload(
    val daysActive: Int,
    val trackers: List<SeasonTrackerStat>,
    val trophies: List<SeasonTrophy>,
)

data class SeasonWindow(val number: Int, val start: LocalDate, val end: LocalDate)

/** Every season that has fully ended before [today], oldest first. Numbers are the spec's 1-based formula. */
fun finishedWindows(seasonStart: LocalDate, length: Int, today: LocalDate): List<SeasonWindow> {
    if (today.isBefore(seasonStart)) return emptyList()
    val current = seasonNumber(today, seasonStart, length)
    return (1 until current).map { n ->
        SeasonWindow(n, seasonStartDate(n, seasonStart, length), seasonEndDate(n, seasonStart, length))
    }
}

/**
 * The part of the running season that was played before [newStart], or null if there is none.
 * Used when the season length changes: this part is snapshotted before the new start date takes over.
 */
fun partialSeasonWindow(seasonStart: LocalDate, length: Int, newStart: LocalDate, today: LocalDate): SeasonWindow? {
    if (today.isBefore(seasonStart)) return null
    val n = seasonNumber(today, seasonStart, length)
    val start = seasonStartDate(n, seasonStart, length)
    val end = newStart.minusDays(1)
    return if (end.isBefore(start)) null else SeasonWindow(n, start, end)
}

/** Season number shown to the user: how many snapshots came before this epoch, plus the spec's formula number. */
fun seasonBase(summaries: List<SeasonSummary>, seasonStart: LocalDate): Int =
    summaries.count { it.startDate.isBefore(seasonStart) }

/** Error message, or null if the change is allowed. */
fun validateSeasonChange(
    seasonStart: LocalDate,
    newLength: Int,
    newStart: LocalDate,
    seasonLength: Int,
    today: LocalDate,
): String? {
    if (newLength !in Settings.MIN_SEASON_LENGTH..Settings.MAX_SEASON_LENGTH) {
        return "Season length must be ${Settings.MIN_SEASON_LENGTH}-${Settings.MAX_SEASON_LENGTH} days"
    }
    if (newStart.isAfter(today)) return "Start date cannot be in the future"
    if (!today.isBefore(seasonStart)) {
        val currentStart = seasonStartDate(seasonNumber(today, seasonStart, seasonLength), seasonStart, seasonLength)
        if (newStart.isBefore(currentStart)) return "Start date cannot be before this season began ($currentStart)"
    }
    return null
}

/** What goes into a snapshot for the days [start]..[end]. */
fun buildSeasonPayload(
    trackers: List<Tracker>,
    logs: List<LogEntry>,
    bands: List<BandHistory>,
    start: LocalDate,
    end: LocalDate,
    today: LocalDate,
): SeasonPayload {
    val inWindow = logs.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
    val daysActive = inWindow.map { it.date }.distinct().size

    val stats = trackers.mapNotNull { t ->
        val mine = inWindow.filter { it.trackerId == t.id }
        val included = mine.isNotEmpty() || (t.status == TrackerStatus.ACTIVE && !t.startDate.isAfter(end))
        if (!included) return@mapNotNull null

        val totals = mine.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.amount } }
        val trackerBands = bands.filter { it.trackerId == t.id }.map { Band(it.effectiveFrom, it.lower, it.upper) }
        val from = if (t.startDate.isAfter(start)) t.startDate else start
        val to = if (end.isBefore(today)) end else today
        val weekly = t.bandPeriod == BandPeriod.WEEKLY
        val best = when {
            from.isAfter(to) -> 0
            weekly -> weeklyStreaks(totals, trackerBands, from, to).best
            else -> dailyStreaks(totals, trackerBands, t.bandPeriod == BandPeriod.DAILY, from, to).best
        }
        SeasonTrackerStat(t.id, t.name, t.unit, t.color, mine.sumOf { it.amount }, best, weekly)
    }.sortedBy { it.name.lowercase() }

    val trophies = trackers
        .filter { it.status == TrackerStatus.COMPLETED }
        .mapNotNull { t ->
            val done = t.completedDate ?: return@mapNotNull null
            if (done.isBefore(start) || done.isAfter(end)) return@mapNotNull null
            val total = logs.filter { it.trackerId == t.id && !it.date.isAfter(done) }.sumOf { it.amount }
            SeasonTrophy(t.id, t.name, t.unit, t.color, total, done)
        }
        .sortedBy { it.completedDate }

    return SeasonPayload(daysActive, stats, trophies)
}

// ---------- JSON ----------

fun SeasonPayload.toJson(): String {
    val root = JSONObject()
    root.put("daysActive", daysActive)
    root.put(
        "trackers",
        JSONArray().also { arr ->
            trackers.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.trackerId)
                        .put("name", it.name)
                        .put("unit", it.unit)
                        .put("color", it.color)
                        .put("total", it.total)
                        .put("bestStreak", it.bestStreak)
                        .put("weekly", it.weekly),
                )
            }
        },
    )
    root.put(
        "trophies",
        JSONArray().also { arr ->
            trophies.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.trackerId)
                        .put("name", it.name)
                        .put("unit", it.unit)
                        .put("color", it.color)
                        .put("total", it.total)
                        .put("completedDate", it.completedDate.toString()),
                )
            }
        },
    )
    return root.toString()
}

fun parseSeasonPayload(json: String): SeasonPayload {
    val root = JSONObject(json)
    val trackers = root.getJSONArray("trackers").let { arr ->
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SeasonTrackerStat(
                o.getLong("id"), o.getString("name"), o.getString("unit"), o.getInt("color"),
                o.getDouble("total"), o.getInt("bestStreak"), o.getBoolean("weekly"),
            )
        }
    }
    val trophies = root.getJSONArray("trophies").let { arr ->
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SeasonTrophy(
                o.getLong("id"), o.getString("name"), o.getString("unit"), o.getInt("color"),
                o.getDouble("total"), LocalDate.parse(o.getString("completedDate")),
            )
        }
    }
    return SeasonPayload(root.getInt("daysActive"), trackers, trophies)
}

/**
 * Which summary to show next. Summaries are shown oldest first. Seasons with no activity are skipped:
 * [skipTo] is the highest number that can be marked as seen without showing anything.
 */
data class NextSummary(val next: SeasonSummary?, val skipTo: Int?)

fun nextUnseenSummary(summaries: List<SeasonSummary>, lastSeen: Int, activeDays: (SeasonSummary) -> Int): NextSummary {
    val unseen = summaries.filter { it.seasonNumber > lastSeen }.sortedBy { it.seasonNumber }
    var skipTo: Int? = null
    for (s in unseen) {
        if (activeDays(s) > 0) return NextSummary(s, skipTo)
        skipTo = s.seasonNumber
    }
    return NextSummary(null, skipTo)
}
