package com.seasons.app.ui

const val EXTRA_TRACKER_ID = "com.seasons.app.TRACKER_ID"
const val EXTRA_GROUP_ID = "com.seasons.app.GROUP_ID"

/** The screens a widget tap should open. Back always goes to Home. Nothing to open gives just Home. */
fun screensForLink(trackerId: Long?, groupId: Long?): List<Screen> = when {
    trackerId != null -> listOf(Screen.Home, Screen.TrackerDetail(trackerId))
    groupId != null -> listOf(Screen.Home, Screen.GroupDetail(groupId))
    else -> listOf(Screen.Home)
}
