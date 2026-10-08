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
    data object Groups : Screen
    data class GroupDetail(val groupId: Long) : Screen
    data object Trophies : Screen
    data object Archive : Screen
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

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}
