package com.seasons.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasons.app.data.Repository
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TrophiesViewModel(repo: Repository) : ViewModel() {
    /** Null while loading. Newest finish first. */
    val cards: StateFlow<List<TrophyCard>?> = combine(
        repo.observeTrackers(),
        repo.observeAllBands(),
        repo.observeAllLogs(),
    ) { trackers, bands, logs ->
        val today = LocalDate.now()
        val logsBy = logs.groupBy { it.trackerId }
        val bandsBy = bands.groupBy { it.trackerId }
        trackers.filter { it.status == TrackerStatus.COMPLETED }
            .map { buildTrophy(it, logsBy[it.id].orEmpty(), bandsBy[it.id].orEmpty(), today) }
            .sortedByDescending { it.finished }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

class ArchiveViewModel(private val repo: Repository) : ViewModel() {
    /** Null while loading. */
    val trackers: StateFlow<List<Tracker>?> = repo.observeTrackers()
        .map { all -> all.filter { it.status == TrackerStatus.ARCHIVED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun unarchive(tracker: Tracker) {
        viewModelScope.launch { repo.unarchive(tracker) }
    }
}
