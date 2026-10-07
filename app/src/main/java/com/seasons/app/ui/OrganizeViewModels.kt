package com.seasons.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasons.app.data.Repository
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerGroup
import com.seasons.app.data.TrackerStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class GroupsViewModel(private val repo: Repository) : ViewModel() {
    /** Null while loading. */
    val groups: StateFlow<List<TrackerGroup>?> = repo.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val rows: StateFlow<List<HomeRow>> = repo.observeHomeRows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String, color: Int) {
        viewModelScope.launch { repo.addGroup(name.trim(), color) }
    }

    fun edit(group: TrackerGroup, name: String, color: Int) {
        viewModelScope.launch { repo.updateGroup(group.copy(name = name.trim(), color = color)) }
    }

    fun delete(group: TrackerGroup, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteGroup(group)
            onDone()
        }
    }

    fun move(id: Long, up: Boolean) {
        viewModelScope.launch { repo.moveGroup(id, up) }
    }
}

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

    fun continueWhereLeftOff(tracker: Tracker) {
        viewModelScope.launch { repo.unarchiveContinue(tracker) }
    }

    fun startFresh(tracker: Tracker) {
        viewModelScope.launch { repo.startFresh(tracker) }
    }
}
