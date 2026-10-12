package com.seasons.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.seasons.app.domain.dateOfEnd
import com.seasons.app.domain.runningStartError
import com.seasons.app.domain.timeError
import java.time.LocalDate
import java.time.ZoneId

class Repository(private val db: AppDatabase) {
    private val trackers = db.trackerDao()
    private val bands = db.bandDao()
    private val logs = db.logDao()
    private val settings = db.settingsDao()
    private val summaries = db.seasonSummaryDao()
    private val timers = db.runningTimerDao()

    suspend fun getTracker(id: Long) = trackers.get(id)
    suspend fun getLog(id: Long) = logs.get(id)

    fun observeTrackers() = trackers.observeAll()
    fun observeTracker(id: Long) = trackers.observe(id)
    fun observeBands(trackerId: Long) = bands.observeFor(trackerId)
    fun observeAllBands() = bands.observeAll()
    fun observeLogs(trackerId: Long) = logs.observeFor(trackerId)
    fun observeAllLogs() = logs.observeAll()
    fun observeSettings() = settings.observe()
    fun observeRunningTimer() = timers.observe()
    fun observeSeasonSummaries() = summaries.observeAll()

    /** Creates the tracker and, if it has a band, its first band row (effective from startDate). */
    suspend fun createTracker(tracker: Tracker, lower: Double?, upper: Double?): Long {
        val id = trackers.insert(tracker)
        if (tracker.bandPeriod != BandPeriod.NONE && lower != null && upper != null) {
            bands.insert(BandHistory(trackerId = id, effectiveFrom = tracker.startDate, lower = lower, upper = upper))
        }
        return id
    }

    suspend fun updateTracker(tracker: Tracker) = trackers.update(tracker)

    /** Permanent. Also deletes the tracker's logs and band history. */
    suspend fun deleteTracker(tracker: Tracker) = trackers.delete(tracker)

    /** Never rewrites history: adds a row. Same effectiveFrom replaces that one row. */
    suspend fun changeBand(trackerId: Long, effectiveFrom: LocalDate, lower: Double, upper: Double) {
        bands.insert(BandHistory(trackerId = trackerId, effectiveFrom = effectiveFrom, lower = lower, upper = upper))
    }

    /** Backfill allowed: startDate <= date <= today. Returns the new id. */
    suspend fun addLog(trackerId: Long, date: LocalDate, amount: Double, today: LocalDate = LocalDate.now()): Long {
        val tracker = requireNotNull(trackers.get(trackerId)) { "Tracker $trackerId not found" }
        validateLog(tracker.startDate, date, amount, today)
        return logs.insert(
            LogEntry(trackerId = trackerId, date = date, amount = amount, createdAt = System.currentTimeMillis()),
        )
    }

    suspend fun editLog(entry: LogEntry, newDate: LocalDate, newAmount: Double, today: LocalDate = LocalDate.now()) {
        require(!entry.isTimed) { "A stopwatch entry is edited with editTimedLog" }
        val tracker = requireNotNull(trackers.get(entry.trackerId)) { "Tracker ${entry.trackerId} not found" }
        validateLog(tracker.startDate, newDate, newAmount, today)
        logs.update(entry.copy(date = newDate, amount = newAmount))
    }

    // ---------- Stopwatch ----------

    /** Starts the timer for [trackerId]. Returns false, changing nothing, if a timer is already running. */
    suspend fun startTimer(trackerId: Long, now: Long = System.currentTimeMillis()): Boolean = db.withTransaction {
        val tracker = requireNotNull(trackers.get(trackerId)) { "Tracker $trackerId not found" }
        require(tracker.status == TrackerStatus.ACTIVE) { "Only an active tracker can be timed" }
        if (timers.get() != null) return@withTransaction false
        timers.insert(RunningTimer(trackerId = trackerId, startTime = now))
        true
    }

    /** Fixes the start of the running timer (for a timer started late). Not in the future. */
    suspend fun editRunningStart(newStart: Long, now: Long = System.currentTimeMillis()) = db.withTransaction {
        val timer = checkNotNull(timers.get()) { "No timer is running" }
        runningStartError(newStart, now)?.let { throw IllegalArgumentException(it) }
        timers.update(timer.copy(startTime = newStart))
    }

    /** Throws the running timer away. Nothing is logged. */
    suspend fun discardTimer() = timers.deleteAll()

    /**
     * Stops the timer and logs it: one entry with [amount], [startTime] and [endTime], counted for the local date of
     * the end time. The entry and the timer removal happen in one transaction. Returns the new log id.
     */
    suspend fun finishTimer(
        startTime: Long,
        endTime: Long,
        amount: Double,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = db.withTransaction {
        val timer = checkNotNull(timers.get()) { "No timer is running" }
        val tracker = requireNotNull(trackers.get(timer.trackerId)) { "Tracker ${timer.trackerId} not found" }
        val date = validateTimed(tracker.startDate, startTime, endTime, amount, now, zone)
        val id = logs.insert(
            LogEntry(
                trackerId = tracker.id, date = date, amount = amount, createdAt = now,
                startTime = startTime, endTime = endTime,
            ),
        )
        timers.deleteAll()
        id
    }

    /** Edits a stopwatch entry: any start, end and amount, within the same rules. The date follows the end time. */
    suspend fun editTimedLog(
        entry: LogEntry,
        startTime: Long,
        endTime: Long,
        amount: Double,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        require(entry.isTimed) { "Only a stopwatch entry has times" }
        val tracker = requireNotNull(trackers.get(entry.trackerId)) { "Tracker ${entry.trackerId} not found" }
        val date = validateTimed(tracker.startDate, startTime, endTime, amount, now, zone)
        logs.update(entry.copy(date = date, amount = amount, startTime = startTime, endTime = endTime))
    }

    private fun validateTimed(trackerStart: LocalDate, start: Long, end: Long, amount: Double, now: Long, zone: ZoneId): LocalDate {
        require(amount > 0) { "Amount must be positive" }
        timeError(start, end, now)?.let { throw IllegalArgumentException(it) }
        val date = dateOfEnd(end, zone)
        require(!date.isBefore(trackerStart)) { "Date is before the tracker start date" }
        return date
    }

    suspend fun deleteLog(entry: LogEntry) = logs.delete(entry)

    suspend fun markCompleted(tracker: Tracker, today: LocalDate = LocalDate.now()) {
        trackers.update(tracker.copy(status = TrackerStatus.COMPLETED, completedDate = today))
    }

    suspend fun saveSettings(value: Settings) {
        require(value.seasonLength in Settings.MIN_SEASON_LENGTH..Settings.MAX_SEASON_LENGTH) {
            "Season length must be ${Settings.MIN_SEASON_LENGTH}-${Settings.MAX_SEASON_LENGTH}"
        }
        settings.upsert(value)
    }

    /** First launch: stores the season start and length. Does nothing if settings already exist. */
    suspend fun saveFirstSettings(seasonStart: LocalDate, seasonLength: Int) {
        require(seasonLength in Settings.MIN_SEASON_LENGTH..Settings.MAX_SEASON_LENGTH) {
            "Season length must be ${Settings.MIN_SEASON_LENGTH}-${Settings.MAX_SEASON_LENGTH}"
        }
        settings.insertIfAbsent(Settings(seasonStartDate = seasonStart, seasonLength = seasonLength))
    }

    // ---------- Backup ----------

    /** Everything in the app, for the backup file and the CSV. */
    suspend fun readBackup(): BackupData = db.withTransaction {
        BackupData(
            settings = settings.get(),
            trackers = trackers.getAll(),
            bands = bands.getAll(),
            logs = logs.getAll(),
            summaries = summaries.getAll(),
            runningTimer = timers.get(),
        )
    }

    /**
     * Replaces ALL data with [data] (already checked by [parseBackup]), in one transaction.
     * If anything fails, the transaction rolls back and the old data is untouched.
     */
    suspend fun replaceAll(data: BackupData) = db.withTransaction {
        // Children first, so no foreign key is ever broken on the way.
        timers.deleteAll()
        logs.deleteAll()
        bands.deleteAll()
        trackers.deleteAll()
        summaries.deleteAll()
        settings.deleteAll()

        trackers.insertAll(data.trackers)
        bands.insertAll(data.bands)
        logs.insertAll(data.logs)
        summaries.insertAll(data.summaries)
        data.runningTimer?.let { timers.insert(it) }
        data.settings?.let { settings.upsert(it) }
    }

    private val seasonLock = Mutex()

    /** Saves a frozen snapshot for every season that has ended and has none yet. Safe to call many times. */
    suspend fun closeFinishedSeasons(today: LocalDate = LocalDate.now()) =
        seasonLock.withLock { closeFinishedLocked(today) }

    private suspend fun closeFinishedLocked(today: LocalDate) {
        val s = settings.get() ?: return
        val windows = finishedWindows(s.seasonStartDate, s.seasonLength, today)
        if (windows.isEmpty()) return
        val existing = summaries.getAll()
        val have = existing.map { it.startDate }.toSet()
        val missing = windows.filter { it.start !in have }
        if (missing.isEmpty()) return

        val base = seasonBase(existing, s.seasonStartDate)
        val allTrackers = trackers.getAll()
        val allLogs = logs.getAll()
        val allBands = bands.getAll()
        missing.forEach { w ->
            saveSnapshot(base + w.number, w.start, w.end, s.seasonLength, allTrackers, allLogs, allBands, today)
        }
    }

    private suspend fun saveSnapshot(
        number: Int,
        start: LocalDate,
        end: LocalDate,
        length: Int,
        allTrackers: List<Tracker>,
        allLogs: List<LogEntry>,
        allBands: List<BandHistory>,
        today: LocalDate,
    ) {
        val payload = buildSeasonPayload(allTrackers, allLogs, allBands, start, end, today)
        summaries.insert(
            SeasonSummary(seasonNumber = number, startDate = start, endDate = end, length = length, payloadJson = payload.toJson()),
        )
    }

    /**
     * New season length and start date. The running season is snapshotted first (only the part before
     * [newStart]). Past snapshots are never touched.
     */
    suspend fun changeSeason(newLength: Int, newStart: LocalDate, today: LocalDate = LocalDate.now()) {
        seasonLock.withLock {
            val s = requireNotNull(settings.get()) { "Settings are not set up yet" }
            validateSeasonChange(s.seasonStartDate, newLength, newStart, s.seasonLength, today)?.let {
                throw IllegalArgumentException(it)
            }
            closeFinishedLocked(today)

            val partial = partialSeasonWindow(s.seasonStartDate, s.seasonLength, newStart, today)
            if (partial != null) {
                val existing = summaries.getAll()
                if (existing.none { it.startDate == partial.start }) {
                    val base = seasonBase(existing, s.seasonStartDate)
                    saveSnapshot(
                        base + partial.number, partial.start, partial.end, s.seasonLength,
                        trackers.getAll(), logs.getAll(), bands.getAll(), today,
                    )
                }
            }
            settings.upsert(s.copy(seasonStartDate = newStart, seasonLength = newLength))
        }
    }

    /** Remembers the highest summary the user has seen. Never moves backwards. */
    suspend fun markSummarySeen(number: Int) {
        val s = settings.get() ?: return
        if (number > s.lastSeasonSummarySeen) settings.upsert(s.copy(lastSeasonSummarySeen = number))
    }

    suspend fun saveSeasonSummary(summary: SeasonSummary) = summaries.insert(summary)

    suspend fun archiveTracker(tracker: Tracker) = trackers.update(tracker.copy(status = TrackerStatus.ARCHIVED))

    /** Resume an archived tracker: same tracker, all logs kept. Does nothing if it is not archived. */
    suspend fun unarchive(tracker: Tracker) = db.withTransaction {
        val current = trackers.get(tracker.id)
        if (current?.status == TrackerStatus.ARCHIVED) trackers.update(current.copy(status = TrackerStatus.ACTIVE))
    }

    private fun validateLog(startDate: LocalDate, date: LocalDate, amount: Double, today: LocalDate) {
        require(amount > 0) { "Amount must be positive" }
        require(!date.isBefore(startDate)) { "Date is before the tracker start date" }
        require(!date.isAfter(today)) { "Date is in the future" }
    }
}
