package com.seasons.app.data

import com.seasons.app.domain.elapsedMinutes
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Backup file (JSON) and log export (CSV). Pure apart from org.json, so every rule is unit-tested.

const val BACKUP_APP_NAME = "Seasons"
const val BACKUP_VERSION = 2

data class BackupData(
    val settings: Settings?,
    val trackers: List<Tracker>,
    val bands: List<BandHistory>,
    val logs: List<LogEntry>,
    val summaries: List<SeasonSummary>,
    /** Format 2: the timer that was running when the backup was made, or null. */
    val runningTimer: RunningTimer? = null,
)

/** Thrown for a file that is not a usable backup. The message is shown to the user as is. */
class BackupException(message: String) : Exception(message)

// ---------- JSON out ----------

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)

fun BackupData.toJson(exportedAt: Instant): String {
    val root = JSONObject()
    root.put("app", BACKUP_APP_NAME)
    root.put("version", BACKUP_VERSION)
    root.put("exportedAt", exportedAt.toString())
    root.put(
        "settings",
        settings?.let {
            JSONObject()
                .put("seasonStartDate", it.seasonStartDate.toString())
                .put("seasonLength", it.seasonLength)
                .put("lastSeasonSummarySeen", it.lastSeasonSummarySeen)
        } ?: JSONObject.NULL,
    )
    root.put("trackers", JSONArray().also { arr ->
        trackers.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("unit", it.unit)
                    .put("type", it.type.name)
                    .put("color", it.color)
                    .putNullable("target", it.target)
                    .putNullable("deadline", it.deadline?.toString())
                    .put("bandPeriod", it.bandPeriod.name)
                    .put("startDate", it.startDate.toString())
                    .put("status", it.status.name)
                    .putNullable("completedDate", it.completedDate?.toString())
                    .put("sortOrder", it.sortOrder),
            )
        }
    })
    root.put("bands", JSONArray().also { arr ->
        bands.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("trackerId", it.trackerId)
                    .put("effectiveFrom", it.effectiveFrom.toString())
                    .put("lower", it.lower)
                    .put("upper", it.upper),
            )
        }
    })
    root.put("logs", JSONArray().also { arr ->
        logs.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("trackerId", it.trackerId)
                    .put("date", it.date.toString())
                    .put("amount", it.amount)
                    .put("createdAt", it.createdAt)
                    .putNullable("startTime", it.startTime)
                    .putNullable("endTime", it.endTime),
            )
        }
    })
    root.put(
        "runningTimer",
        runningTimer?.let { JSONObject().put("trackerId", it.trackerId).put("startTime", it.startTime) } ?: JSONObject.NULL,
    )
    root.put("summaries", JSONArray().also { arr ->
        summaries.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("seasonNumber", it.seasonNumber)
                    .put("startDate", it.startDate.toString())
                    .put("endDate", it.endDate.toString())
                    .put("length", it.length)
                    .put("payloadJson", it.payloadJson),
            )
        }
    })
    return root.toString()
}

// ---------- JSON in ----------

/**
 * Reads and fully checks a backup. Nothing is trusted: a file that passes this can be written to the database
 * without breaking a rule the app relies on. Throws [BackupException] with a readable reason otherwise.
 */
fun parseBackup(text: String, now: Long = System.currentTimeMillis()): BackupData {
    val root = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw BackupException("This file is not valid JSON.")
    }
    try {
        return parseRoot(root, now)
    } catch (e: BackupException) {
        throw e
    } catch (e: JSONException) {
        throw BackupException("The backup is damaged or incomplete (${e.message?.take(80)}).")
    }
}

private fun fail(message: String): Nothing = throw BackupException(message)

private inline fun <reified E : Enum<E>> JSONObject.enumValue(key: String, what: String): E {
    val raw = getString(key)
    return enumValues<E>().firstOrNull { it.name == raw } ?: fail("Unknown $what \"$raw\".")
}

/** Old files may hold REPLACED (a tracker restarted with "Start fresh"). That status is gone: it is read as ARCHIVED. */
private fun JSONObject.trackerStatus(): TrackerStatus =
    if (getString("status") == "REPLACED") TrackerStatus.ARCHIVED else enumValue<TrackerStatus>("status", "tracker status")

private fun JSONObject.dateValue(key: String): LocalDate = try {
    LocalDate.parse(getString(key))
} catch (e: java.time.format.DateTimeParseException) {
    fail("Bad date in \"$key\": ${getString(key)}.")
}

private fun JSONObject.nullableDate(key: String): LocalDate? = if (isNull(key)) null else dateValue(key)

private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)

private fun JSONObject.nullableDouble(key: String): Double? = if (isNull(key)) null else getDouble(key)

private fun <T> JSONArray.objects(map: (JSONObject) -> T): List<T> = (0 until length()).map { map(getJSONObject(it)) }

private fun requireUniqueIds(what: String, ids: List<Long>) {
    if (ids.any { it <= 0 }) fail("A $what has an invalid id.")
    if (ids.toSet().size != ids.size) fail("Two ${what}s share the same id.")
}

private fun parseRoot(root: JSONObject, now: Long): BackupData {
    if (root.optString("app") != BACKUP_APP_NAME) fail("This is not a Seasons backup.")
    val version = root.optInt("version", 0)
    if (version > BACKUP_VERSION) fail("This backup was made by a newer version of the app (format $version).")
    if (version < 1) fail("This backup has no valid format version.")

    val settings = if (root.isNull("settings")) {
        null
    } else {
        val o = root.getJSONObject("settings")
        val length = o.getInt("seasonLength")
        if (length !in Settings.MIN_SEASON_LENGTH..Settings.MAX_SEASON_LENGTH) {
            fail("Season length $length is outside ${Settings.MIN_SEASON_LENGTH}-${Settings.MAX_SEASON_LENGTH}.")
        }
        val seen = o.getInt("lastSeasonSummarySeen")
        if (seen < 0) fail("Settings have an invalid season marker.")
        Settings(seasonStartDate = o.dateValue("seasonStartDate"), seasonLength = length, lastSeasonSummarySeen = seen)
    }

    // Format 1 files also hold "groups" and a tracker "groupId". The Groups feature is gone, so they are ignored.

    val trackers = root.getJSONArray("trackers").objects {
        Tracker(
            id = it.getLong("id"),
            name = it.getString("name"),
            unit = it.getString("unit"),
            type = it.enumValue<TrackerType>("type", "tracker type"),
            color = it.getInt("color"),
            target = it.nullableDouble("target"),
            deadline = it.nullableDate("deadline"),
            bandPeriod = it.enumValue<BandPeriod>("bandPeriod", "band period"),
            startDate = it.dateValue("startDate"),
            status = it.trackerStatus(),
            completedDate = it.nullableDate("completedDate"),
            sortOrder = it.getInt("sortOrder"),
        )
    }
    requireUniqueIds("tracker", trackers.map { it.id })
    trackers.forEach { t ->
        if (t.name.isBlank() || t.unit.isBlank()) fail("A tracker has no name or unit.")
        if (t.target != null && (!t.target.isFinite() || t.target <= 0.0)) fail("Tracker \"${t.name}\" has an invalid target.")
        if (t.type == TrackerType.GOAL && t.target == null) fail("Goal \"${t.name}\" has no target.")
    }
    val trackerIds = trackers.map { it.id }.toSet()

    val bands = root.getJSONArray("bands").objects {
        BandHistory(
            id = it.getLong("id"),
            trackerId = it.getLong("trackerId"),
            effectiveFrom = it.dateValue("effectiveFrom"),
            lower = it.getDouble("lower"),
            upper = it.getDouble("upper"),
        )
    }
    requireUniqueIds("band row", bands.map { it.id })
    bands.forEach { b ->
        if (b.trackerId !in trackerIds) fail("A band row points to a tracker that is not in the file.")
        if (!b.lower.isFinite() || !b.upper.isFinite() || b.lower < 0.0 || b.upper < b.lower) fail("A band row has invalid bounds.")
    }
    if (bands.map { it.trackerId to it.effectiveFrom }.toSet().size != bands.size) {
        fail("A tracker has two band rows for the same date.")
    }

    val logs = root.getJSONArray("logs").objects {
        LogEntry(
            id = it.getLong("id"),
            trackerId = it.getLong("trackerId"),
            date = it.dateValue("date"),
            amount = it.getDouble("amount"),
            createdAt = it.getLong("createdAt"),
            // Format 1 files have no time fields: every entry is a plain one.
            startTime = it.nullableLong("startTime"),
            endTime = it.nullableLong("endTime"),
        )
    }
    requireUniqueIds("log entry", logs.map { it.id })
    logs.forEach { l ->
        if (l.trackerId !in trackerIds) fail("A log entry points to a tracker that is not in the file.")
        if (!l.amount.isFinite() || l.amount <= 0.0) fail("A log entry has an amount that is not positive.")
        if ((l.startTime == null) != (l.endTime == null)) fail("A log entry has a start time without an end time, or the other way round.")
        if (l.startTime != null && l.endTime != null && l.endTime <= l.startTime) fail("A log entry ends before it starts.")
    }

    val runningTimer = if (!root.has("runningTimer") || root.isNull("runningTimer")) {
        null
    } else {
        val o = root.getJSONObject("runningTimer")
        RunningTimer(trackerId = o.getLong("trackerId"), startTime = o.getLong("startTime")).also { t ->
            if (t.trackerId !in trackerIds) fail("The running timer points to a tracker that is not in the file.")
            if (t.startTime > now) fail("The running timer starts in the future.")
        }
    }

    val summaries = root.getJSONArray("summaries").objects {
        SeasonSummary(
            id = it.getLong("id"),
            seasonNumber = it.getInt("seasonNumber"),
            startDate = it.dateValue("startDate"),
            endDate = it.dateValue("endDate"),
            length = it.getInt("length"),
            payloadJson = it.getString("payloadJson"),
        )
    }
    requireUniqueIds("season summary", summaries.map { it.id })
    summaries.forEach { s ->
        if (s.seasonNumber <= 0) fail("A season summary has an invalid number.")
        if (s.endDate.isBefore(s.startDate)) fail("A season summary ends before it starts.")
        if (s.length !in Settings.MIN_SEASON_LENGTH..Settings.MAX_SEASON_LENGTH) fail("A season summary has an invalid length.")
        try {
            parseSeasonPayload(s.payloadJson)
        } catch (e: Exception) {
            fail("Season ${s.seasonNumber} has a damaged summary.")
        }
    }
    if (summaries.map { it.startDate }.toSet().size != summaries.size) fail("Two season summaries start on the same date.")

    return BackupData(settings, trackers, bands, logs, summaries, runningTimer)
}

// ---------- CSV ----------

/** A spreadsheet runs a cell that starts with one of these as a formula. A leading apostrophe stops that. */
private val FORMULA_STARTS = charArrayOf('=', '+', '-', '@', '\t', '\r')

internal fun csvField(raw: String): String {
    val safe = if (raw.isNotEmpty() && raw[0] in FORMULA_STARTS) "'$raw" else raw
    val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
}

private fun plainNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

/** Local date and time to the minute, for the CSV: 2026-10-05T13:19. */
private fun csvTime(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES).toString()

/**
 * One row per log entry, oldest first. Lines end with CRLF, as RFC 4180 says.
 * start, end and minutes are empty for plain entries (no stopwatch). Minutes are whole, rounded down.
 */
fun logsToCsv(trackers: List<Tracker>, logs: List<LogEntry>, zone: ZoneId = ZoneId.systemDefault()): String {
    val trackerById = trackers.associateBy { it.id }
    val sb = StringBuilder("date,tracker,amount,unit,start,end,minutes,logged_at\r\n")
    logs.sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id })).forEach { l ->
        val t = trackerById[l.trackerId] ?: return@forEach
        val start = l.startTime
        val end = l.endTime
        val timed = start != null && end != null
        sb.append(l.date.toString()).append(',')
            .append(csvField(t.name)).append(',')
            .append(plainNumber(l.amount)).append(',')
            .append(csvField(t.unit)).append(',')
            .append(if (timed) csvTime(start!!, zone) else "").append(',')
            .append(if (timed) csvTime(end!!, zone) else "").append(',')
            .append(if (timed) elapsedMinutes(start!!, end!!).toString() else "").append(',')
            .append(Instant.ofEpochMilli(l.createdAt).toString())
            .append("\r\n")
    }
    return sb.toString()
}
