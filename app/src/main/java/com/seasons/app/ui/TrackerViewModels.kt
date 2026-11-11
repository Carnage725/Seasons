package com.seasons.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasons.app.data.BandHistory
import com.seasons.app.data.BandPeriod
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Repository
import com.seasons.app.data.Tracker
import com.seasons.app.data.seasonBase
import com.seasons.app.data.TrackerType
import com.seasons.app.ui.charts.AveragePaceModel
import com.seasons.app.ui.charts.StreakChartModel
import com.seasons.app.ui.charts.buildStreakChart
import com.seasons.app.ui.charts.BarChartModel
import com.seasons.app.ui.charts.OnTrackModel
import com.seasons.app.ui.charts.SeasonModel
import com.seasons.app.ui.charts.buildAveragePace
import com.seasons.app.ui.charts.buildDailyChart
import com.seasons.app.ui.charts.buildOnTrackChart
import com.seasons.app.ui.charts.buildSeasonModel
import com.seasons.app.ui.charts.buildWeeklyChart
import com.seasons.app.domain.Band
import com.seasons.app.domain.bandOn
import com.seasons.app.domain.dailyStreakRuns
import com.seasons.app.domain.weeklyStreakRuns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TrackerUiState(
    val tracker: Tracker,
    val logs: List<LogEntry>,
    val summary: TrackerSummary,
    /** The band in effect today, to prefill "Change band". */
    val band: Band?,
    val charts: ChartsData,
    /** Stopwatch totals, or null when the tracker has no stopwatch entries. */
)

/** Null parts: the season chart needs settings (loading), the On track chart is for goals only. */
data class ChartsData(
    val daily: BarChartModel,
    val weekly: BarChartModel,
    val season: SeasonModel?,
    val onTrack: OnTrackModel?,
    /** Trackers with no target: average per day. */
    val pace: AveragePaceModel?,
    /** The 10 most recent streaks. Null when there is none yet. */
    val streaks: StreakChartModel?,
    /** Time per day and work per hour. Null when the tracker has no stopwatch entries. */
    val time: TimeTabModel?,
)

class TrackerViewModel(private val repo: Repository, private val trackerId: Long) : ViewModel() {
    /** Null while loading, or if the tracker does not exist. */
    val state: StateFlow<TrackerUiState?> = combine(
        repo.observeTracker(trackerId),
        repo.observeLogs(trackerId),
        repo.observeBands(trackerId),
        repo.observeSettings(),
        repo.observeSeasonSummaries(),
    ) { tracker, logs, bands, settings, summaries ->
        if (tracker == null) {
            null
        } else {
            val today = LocalDate.now()
            val bandList = bands.toBands()
            val totals = logs.dailyTotals()
            TrackerUiState(
                tracker = tracker,
                logs = logs,
                summary = buildTrackerSummary(tracker, logs, bandList, today),
                band = bandOn(bandList, today),
                charts = ChartsData(
                    daily = buildDailyChart(tracker, totals, bandList, today),
                    weekly = buildWeeklyChart(tracker, totals, bandList, today),
                    season = settings?.let {
                        buildSeasonModel(
                            tracker, totals, bandList, today, it.seasonStartDate, it.seasonLength,
                            numberBase = seasonBase(summaries, it.seasonStartDate),
                        )
                    },
                    onTrack = if (tracker.type == TrackerType.GOAL) buildOnTrackChart(tracker, totals, today) else null,
                    pace = if (tracker.type == TrackerType.ONGOING) buildAveragePace(tracker, totals, today) else null,
                    time = buildTimeTab(logs, tracker.unit, today),
                    streaks = buildStreakChart(
                        if (tracker.bandPeriod == BandPeriod.WEEKLY) {
                            weeklyStreakRuns(totals, bandList, tracker.startDate, today)
                        } else {
                            dailyStreakRuns(totals, bandList, tracker.bandPeriod == BandPeriod.DAILY, tracker.startDate, today)
                        },
                        today,
                        weekly = tracker.bandPeriod == BandPeriod.WEEKLY,
                    ),
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The running timer (any tracker), or null. */
    val timer: StateFlow<TimerInfo?> = repo.observeTimerInfo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun startTimer() {
        viewModelScope.launch { repo.startTimer(trackerId) }
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun clearError() {
        _error.value = null
    }

    fun addLog(date: LocalDate, amount: Double) = launchSafely { repo.addLog(trackerId, date, amount) }

    fun editLog(entry: LogEntry, date: LocalDate, amount: Double) = launchSafely { repo.editLog(entry, date, amount) }

    fun complete(tracker: Tracker, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.markCompleted(tracker)
            onDone()
        }
    }

    fun archive(tracker: Tracker, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.archiveTracker(tracker)
            onDone()
        }
    }

    fun deleteLog(entry: LogEntry) = launchSafely { repo.deleteLog(entry) }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: IllegalArgumentException) {
                _error.value = e.message
            }
        }
    }
}

class FormViewModel(private val repo: Repository) : ViewModel() {
    suspend fun load(id: Long): Tracker? = repo.getTracker(id)

    suspend fun currentBand(id: Long): BandHistory? = repo.bandOn(id, LocalDate.now())

    fun changeBand(trackerId: Long, effectiveFrom: LocalDate, lower: Double, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.changeBand(trackerId, effectiveFrom, lower)
            onDone()
        }
    }

    fun create(tracker: Tracker, lower: Double?, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.createTracker(tracker, lower)
            onDone()
        }
    }

    fun delete(tracker: Tracker, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteTracker(tracker)
            onDone()
        }
    }

    fun update(tracker: Tracker, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.updateTracker(tracker)
            onDone()
        }
    }
}
