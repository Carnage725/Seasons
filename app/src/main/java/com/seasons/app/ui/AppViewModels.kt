package com.seasons.app.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.seasons.app.SeasonsApp
import com.seasons.app.data.Repository

fun CreationExtras.repository(): Repository = (this[APPLICATION_KEY] as SeasonsApp).repository

sealed interface Screen {
    data object Home : Screen
    data class TrackerForm(val trackerId: Long?) : Screen
    data class TrackerDetail(val trackerId: Long) : Screen
    data object Trophies : Screen
    data object Archive : Screen
    /**
     * The three timer screens carry a token so each opening gets a fresh view model (view models are kept by key,
     * and an old one would show old times).
     */
    /** The running timer was stopped: set start, end and amount, then save or discard. */
    data class TimerStop(val token: Long = System.nanoTime()) : Screen
    /** Fix the start of the running timer. */
    data class TimerEditRunning(val token: Long = System.nanoTime()) : Screen
    data class TimerEditEntry(val logId: Long, val token: Long = System.nanoTime()) : Screen
    data object Settings : Screen
    data object SeasonsHistory : Screen
    data class SeasonSummary(val summaryId: Long) : Screen
}

/** A plain back stack. Lives in a ViewModel so it survives rotation. */
class NavViewModel : ViewModel() {
    val stack = mutableStateListOf<Screen>(Screen.Home)

    fun push(screen: Screen) {
        stack.add(screen)
    }

    /** A widget was tapped: replace the whole stack with Home plus the target screen. */
    fun openLink(trackerId: Long) {
        stack.clear()
        stack.addAll(screensForLink(trackerId))
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}
