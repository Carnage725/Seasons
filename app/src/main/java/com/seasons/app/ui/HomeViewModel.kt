package com.seasons.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasons.app.data.BandHistory
import com.seasons.app.data.BandPeriod
import com.seasons.app.data.LogEntry
import com.seasons.app.data.Repository
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerStatus
import com.seasons.app.data.TrackerType
import com.seasons.app.domain.Band
import com.seasons.app.domain.bandOn
import com.seasons.app.domain.dailyStreaks
import com.seasons.app.domain.weeklyStreaks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeRow(
    val tracker: Tracker,
    val done: Double,
    val todayTotal: Double,
    val band: Band?,
    val metToday: Boolean,
    val streak: Int,
    val progress: Float,
)

/** "124 / 200 pages" for a goal, "5 laps today" for an ongoing tracker. Shared by Home, widgets and previews. */
fun HomeRow.mainText(): String =
    if (tracker.type == TrackerType.GOAL) {
        "${formatAmount(done)} / ${formatAmount(tracker.target ?: 0.0)} ${tracker.unit}"
    } else {
        "${formatAmount(todayTotal)} ${tracker.unit} today"
    }

fun HomeRow.statusText(): String = if (metToday) "Met today" else "Not yet"

fun HomeRow.streakText(): String = "Streak $streak" + if (tracker.bandPeriod == BandPeriod.WEEKLY) " wk" else ""

fun List<LogEntry>.dailyTotals(): Map<LocalDate, Double> =
    groupBy { it.date }.mapValues { (_, entries) -> entries.sumOf { it.amount } }

fun List<BandHistory>.toBands(): List<Band> = map { Band(it.effectiveFrom, it.lower, it.upper) }

fun buildHomeRow(tracker: Tracker, logs: List<LogEntry>, bands: List<Band>, today: LocalDate): HomeRow {
    val totals = logs.dailyTotals()
    val todayTotal = totals[today] ?: 0.0
    val band = if (tracker.bandPeriod == BandPeriod.NONE) null else bandOn(bands, today)
    val dailyBand = if (tracker.bandPeriod == BandPeriod.DAILY) band else null

    val met = if (dailyBand != null) todayTotal >= dailyBand.lower else todayTotal > 0.0
    val streak = if (tracker.bandPeriod == BandPeriod.WEEKLY) {
        weeklyStreaks(totals, bands, tracker.startDate, today).current
    } else {
        dailyStreaks(totals, bands, tracker.bandPeriod == BandPeriod.DAILY, tracker.startDate, today).current
    }

    val done = totals.values.sum()
    val progress = when {
        tracker.type == TrackerType.GOAL && (tracker.target ?: 0.0) > 0.0 -> done / tracker.target!!
        dailyBand != null && dailyBand.upper > 0.0 -> todayTotal / dailyBand.upper
        else -> 0.0
    }.coerceIn(0.0, 1.0).toFloat()

    return HomeRow(tracker, done, todayTotal, band, met, streak, progress)
}

/** Active trackers as Home rows. Shared by Home and the group screens. */
fun Repository.observeHomeRows(): Flow<List<HomeRow>> = combine(
    observeTrackers(),
    observeAllBands(),
    observeAllLogs(),
) { trackers, bands, logs ->
    val today = LocalDate.now()
    val logsByTracker = logs.groupBy { it.trackerId }
    val bandsByTracker = bands.groupBy { it.trackerId }
    trackers.filter { it.status == TrackerStatus.ACTIVE }.map {
        buildHomeRow(it, logsByTracker[it.id].orEmpty(), bandsByTracker[it.id].orEmpty().toBands(), today)
    }
}

class HomeViewModel(repo: Repository) : ViewModel() {
    private val seasons = HomeSeasonState(repo, viewModelScope)

    /** True when first-launch setup is still needed. */
    val needsSetup: StateFlow<Boolean> = seasons.needsSetup

    /** The next season summary the user has not seen yet, or null. */
    val pendingSummary: StateFlow<com.seasons.app.data.SeasonSummary?> = seasons.pendingSummary

    init {
        // Once settings exist, save a snapshot for every season that has ended since the last open.
        viewModelScope.launch { seasons.closeFinishedSeasonsWhenReady() }
    }

    private val shownSummaries = mutableSetOf<Long>()

    /**
     * True the first time it is asked about a summary. Stops a stale value from opening the same summary
     * again when you come back from it, before the database has caught up.
     */
    fun claimSummary(id: Long): Boolean = shownSummaries.add(id)

    fun saveSetup(start: java.time.LocalDate, length: Int) {
        viewModelScope.launch { seasons.saveSetup(start, length) }
    }

    /** Null while loading. */
    val sections: StateFlow<List<HomeSection>?> = combine(
        repo.observeHomeRows(),
        repo.observeGroups(),
    ) { rows, groups -> buildSections(groups, rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
