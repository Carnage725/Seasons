package com.seasons.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasons.app.data.Repository
import com.seasons.app.data.SeasonPayload
import com.seasons.app.data.SeasonSummary
import com.seasons.app.data.Settings
import com.seasons.app.data.nextUnseenSummary
import com.seasons.app.data.parseSeasonPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A stored snapshot with its payload decoded. */
data class SeasonItem(val summary: SeasonSummary, val payload: SeasonPayload)

private fun SeasonSummary.decode() = SeasonItem(this, parseSeasonPayload(payloadJson))

/** Settings, season snapshots and the season-change action. Used by Settings, history and summary screens. */
class SeasonsViewModel(private val repo: Repository) : ViewModel() {
    /** Null while loading, or before first-launch setup. */
    val settings: StateFlow<Settings?> = repo.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Newest first. Null while loading. */
    val items: StateFlow<List<SeasonItem>?> = repo.observeSeasonSummaries()
        .map { list -> list.map { it.decode() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun clearError() {
        _error.value = null
    }

    fun changeSeason(newLength: Int, newStart: LocalDate) {
        viewModelScope.launch {
            try {
                repo.changeSeason(newLength, newStart)
            } catch (e: IllegalArgumentException) {
                _error.value = e.message
            }
        }
    }

    fun markSeen(number: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.markSummarySeen(number)
            onDone()
        }
    }
}

/** What Home needs to know about seasons: first-launch setup and the next unseen summary. */
class HomeSeasonState(private val repo: Repository, scope: kotlinx.coroutines.CoroutineScope) {
    /** True once we know there are no settings, so the setup dialog must show. Starts false to avoid a flash. */
    val needsSetup: StateFlow<Boolean> = repo.observeSettings().map { it == null }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    val pendingSummary: StateFlow<SeasonSummary?> = combine(
        repo.observeSettings().filterNotNull(),
        repo.observeSeasonSummaries(),
    ) { settings, summaries ->
        val result = nextUnseenSummary(summaries, settings.lastSeasonSummarySeen) {
            parseSeasonPayload(it.payloadJson).daysActive
        }
        // Seasons with nothing in them are never shown. Move the "seen" marker past them.
        result.skipTo?.let { if (it > settings.lastSeasonSummarySeen) repo.markSummarySeen(it) }
        result.next
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun closeFinishedSeasonsWhenReady() {
        repo.observeSettings().filterNotNull().first()
        repo.closeFinishedSeasons()
    }

    suspend fun saveSetup(start: LocalDate, length: Int) = repo.saveFirstSettings(start, length)
}
