package com.seasons.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Repository
import com.seasons.app.data.Tracker
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
    val todayTotal: Double,
    val band: Band?,
)

class TrackerViewModel(private val repo: Repository, private val trackerId: Long) : ViewModel() {
    /** Null while loading, or if the tracker does not exist. */
    val state: StateFlow<TrackerUiState?> = combine(
        repo.observeTracker(trackerId),
        repo.observeLogs(trackerId),
        repo.observeBands(trackerId),
    ) { tracker, logs, bands ->
        if (tracker == null) {
            null
        } else {
            val today = LocalDate.now()
            TrackerUiState(
                tracker = tracker,
                logs = logs,
                todayTotal = logs.filter { it.date == today }.sumOf { it.amount },
                band = bandOn(bands.toBands(), today),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun clearError() {
        _error.value = null
    }

    fun addLog(date: LocalDate, amount: Double) = launchSafely { repo.addLog(trackerId, date, amount) }

    fun editLog(entry: LogEntry, date: LocalDate, amount: Double) = launchSafely { repo.editLog(entry, date, amount) }

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
