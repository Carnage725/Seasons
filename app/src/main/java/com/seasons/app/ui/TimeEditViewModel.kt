package com.seasons.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Repository
import com.seasons.app.data.Tracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the Edit time screen is doing. STOP: the running timer was stopped. RUNNING: fix its start. ENTRY: edit a saved entry. */
enum class TimeEditKind { STOP, RUNNING, ENTRY }

/** What the screen starts with. [end] is null while a timer is still running. */
data class TimeEditInit(val tracker: Tracker, val start: Long, val end: Long?, val amount: Double?, val entry: LogEntry?)

class TimeEditViewModel(
    private val repo: Repository,
    val kind: TimeEditKind,
    private val logId: Long?,
) : ViewModel() {
    private val _init = MutableStateFlow<TimeEditInit?>(null)

    /** Null while loading. */
    val init: StateFlow<TimeEditInit?> = _init

    private val _missing = MutableStateFlow(false)

    /** True when there is nothing to edit (the timer already stopped, or the entry was deleted). */
    val missing: StateFlow<Boolean> = _missing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            val loaded = when (kind) {
                TimeEditKind.STOP, TimeEditKind.RUNNING -> {
                    val timer = repo.observeRunningTimer().first()
                    val tracker = timer?.let { repo.getTracker(it.trackerId) }
                    if (timer == null || tracker == null) {
                        null
                    } else {
                        // Stop: the end is "now" when the screen opens. The user can move it.
                        val end = if (kind == TimeEditKind.STOP) System.currentTimeMillis() else null
                        TimeEditInit(tracker, timer.startTime, end, null, null)
                    }
                }
                TimeEditKind.ENTRY -> {
                    val entry = logId?.let { repo.getLog(it) }
                    val tracker = entry?.let { repo.getTracker(it.trackerId) }
                    if (entry == null || tracker == null || !entry.isTimed) {
                        null
                    } else {
                        TimeEditInit(tracker, entry.startTime!!, entry.endTime!!, entry.amount, entry)
                    }
                }
            }
            if (loaded == null) _missing.value = true else _init.value = loaded
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun save(start: Long, end: Long?, amount: Double?, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                when (kind) {
                    TimeEditKind.STOP -> repo.finishTimer(start, requireNotNull(end), requireNotNull(amount))
                    TimeEditKind.RUNNING -> repo.editRunningStart(start)
                    TimeEditKind.ENTRY -> repo.editTimedLog(requireNotNull(_init.value?.entry), start, requireNotNull(end), requireNotNull(amount))
                }
                onDone()
            } catch (e: IllegalArgumentException) {
                _error.value = e.message
            } catch (e: IllegalStateException) {
                _error.value = e.message
            }
        }
    }

    /** Stop screen: throw the time away. Nothing is logged. */
    fun discardTimer(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.discardTimer()
            onDone()
        }
    }

    fun deleteEntry(onDone: () -> Unit) {
        viewModelScope.launch {
            _init.value?.entry?.let { repo.deleteLog(it) }
            onDone()
        }
    }
}
