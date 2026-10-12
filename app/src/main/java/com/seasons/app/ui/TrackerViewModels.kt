package com.seasons.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Repository
import com.seasons.app.data.Tracker
import com.seasons.app.data.seasonBase
import com.seasons.app.data.TrackerType
import com.seasons.app.ui.charts.BarChartModel
import com.seasons.app.ui.charts.OnTrackModel
import com.seasons.app.ui.charts.SeasonModel
import com.seasons.app.ui.charts.TimeStripModel
import com.seasons.app.ui.charts.buildDailyChart
import com.seasons.app.ui.charts.buildTimeStrip
import com.seasons.app.ui.charts.buildOnTrackChart
import com.seasons.app.ui.charts.buildSeasonModel
import com.seasons.app.ui.charts.buildWeeklyChart
import com.seasons.app.domain.Band
import com.seasons.app.domain.bandOn
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
    val timeLine: String?,
)

/** Null parts: the season chart needs settings (loading), the On track chart is for goals only. */
data class ChartsData(
    val daily: BarChartModel,
    /** Stopwatch minutes under the Daily chart, or null when there are none. */
    val dailyTime: TimeStripModel?,
    val weekly: BarChartModel,
    val season: SeasonModel?,
    val onTrack: OnTrackModel?,
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
                timeLine = buildTimeLine(logs, today),
                charts = ChartsData(
                    daily = buildDailyChart(tracker, totals, bandList, today),
                    dailyTime = buildTimeStrip(logs, today),
                    weekly = buildWeeklyChart(tracker, totals, bandList, today),
                    season = settings?.let {
                        buildSeasonModel(
                            tracker, totals, bandList, today, it.seasonStartDate, it.seasonLength,
                            numberBase = seasonBase(summaries, it.seasonStartDate),
                        )
                    },
                    onTrack = if (tracker.type == TrackerType.GOAL) buildOnTrackChart(tracker, totals, today) else null,
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

    fun changeBand(effectiveFrom: LocalDate, lower: Double, upper: Double) =
        launchSafely { repo.changeBand(trackerId, effectiveFrom, lower, upper) }

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

    fun create(tracker: Tracker, lower: Double?, upper: Double?, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.createTracker(tracker, lower, upper)
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
