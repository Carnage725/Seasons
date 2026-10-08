package com.seasons.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class Repository(private val db: AppDatabase) {
    private val trackers = db.trackerDao()
    private val bands = db.bandDao()
    private val logs = db.logDao()
    private val groups = db.groupDao()
    private val settings = db.settingsDao()
    private val summaries = db.seasonSummaryDao()

    suspend fun getTracker(id: Long) = trackers.get(id)
    suspend fun getLog(id: Long) = logs.get(id)

    fun observeTrackers() = trackers.observeAll()
    fun observeTracker(id: Long) = trackers.observe(id)
    fun observeBands(trackerId: Long) = bands.observeFor(trackerId)
    fun observeAllBands() = bands.observeAll()
    fun observeLogs(trackerId: Long) = logs.observeFor(trackerId)
    fun observeAllLogs() = logs.observeAll()
    fun observeGroups() = groups.observeAll()
    fun observeSettings() = settings.observe()
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
        val tracker = requireNotNull(trackers.get(entry.trackerId)) { "Tracker ${entry.trackerId} not found" }
        validateLog(tracker.startDate, newDate, newAmount, today)
        logs.update(entry.copy(date = newDate, amount = newAmount))
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
            groups = groups.getAll(),
            trackers = trackers.getAll(),
            bands = bands.getAll(),
            logs = logs.getAll(),
            summaries = summaries.getAll(),
        )
    }

    /**
     * Replaces ALL data with [data] (already checked by [parseBackup]), in one transaction.
     * If anything fails, the transaction rolls back and the old data is untouched.
     */
    suspend fun replaceAll(data: BackupData) = db.withTransaction {
        // Children first, so no foreign key is ever broken on the way.
        logs.deleteAll()
        bands.deleteAll()
        trackers.deleteAll()
        groups.deleteAll()
        summaries.deleteAll()
        settings.deleteAll()

        groups.insertAll(data.groups)
        trackers.insertAll(data.trackers)
        bands.insertAll(data.bands)
        logs.insertAll(data.logs)
        summaries.insertAll(data.summaries)
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

    suspend fun addGroup(name: String, color: Int): Long =
        groups.insert(TrackerGroup(name = name, color = color, sortOrder = groups.maxSortOrder() + 1))

    suspend fun updateGroup(group: TrackerGroup) = groups.update(group)

    /** Deletes only the group. Its trackers stay and become ungrouped. */
    suspend fun deleteGroup(group: TrackerGroup) = groups.delete(group)

    /** Moves a group one place up or down. Rewrites sortOrder as 0, 1, 2... */
    suspend fun moveGroup(id: Long, up: Boolean) {
        val before = groups.getAll()
        swapGroupOrder(before, id, up).forEach { g ->
            if (before.first { it.id == g.id }.sortOrder != g.sortOrder) groups.update(g)
        }
    }

    suspend fun archiveTracker(tracker: Tracker) = trackers.update(tracker.copy(status = TrackerStatus.ARCHIVED))

    /** "Continue where you left off": same tracker, all logs kept. Does nothing if it is not archived. */
    suspend fun unarchiveContinue(tracker: Tracker) = db.withTransaction {
        val current = trackers.get(tracker.id)
        if (current?.status == TrackerStatus.ARCHIVED) trackers.update(current.copy(status = TrackerStatus.ACTIVE))
    }

    /**
     * "Start fresh": a new tracker with the same settings, starting [today]. The old one is kept as history
     * (status REPLACED) and leaves the Archive list, so it cannot be restarted twice.
     * Returns the new id, or null if the tracker was not archived (for example a second tap).
     */
    suspend fun startFresh(old: Tracker, today: LocalDate = LocalDate.now()): Long? = db.withTransaction {
        val current = trackers.get(old.id)
        if (current == null || current.status != TrackerStatus.ARCHIVED) return@withTransaction null
        val band = if (current.bandPeriod == BandPeriod.NONE) {
            null
        } else {
            bands.getFor(current.id).filter { !it.effectiveFrom.isAfter(today) }.maxByOrNull { it.effectiveFrom }
        }
        val newId = createTracker(freshCopy(current, today), band?.lower, band?.upper)
        trackers.update(current.copy(status = TrackerStatus.REPLACED))
        newId
    }

    private fun validateLog(startDate: LocalDate, date: LocalDate, amount: Double, today: LocalDate) {
        require(amount > 0) { "Amount must be positive" }
        require(!date.isBefore(startDate)) { "Date is before the tracker start date" }
        require(!date.isAfter(today)) { "Date is in the future" }
    }
}
