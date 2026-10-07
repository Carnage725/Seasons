package com.seasons.app.data

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

    /** Creates the default settings row (season starts today, 77 days) if there is none yet. */
    suspend fun ensureSettings(today: LocalDate = LocalDate.now()) {
        settings.insertIfAbsent(Settings(seasonStartDate = today))
    }

    suspend fun saveSeasonSummary(summary: SeasonSummary) = summaries.insert(summary)

    suspend fun addGroup(group: TrackerGroup) = groups.insert(group)
    suspend fun updateGroup(group: TrackerGroup) = groups.update(group)

    private fun validateLog(startDate: LocalDate, date: LocalDate, amount: Double, today: LocalDate) {
        require(amount > 0) { "Amount must be positive" }
        require(!date.isBefore(startDate)) { "Date is before the tracker start date" }
        require(!date.isAfter(today)) { "Date is in the future" }
    }
}
