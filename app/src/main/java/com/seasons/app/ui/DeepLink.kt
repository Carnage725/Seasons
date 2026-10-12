package com.seasons.app.ui

const val EXTRA_TRACKER_ID = "com.seasons.app.TRACKER_ID"

/** The screens a widget tap should open. Back always goes to Home. */
fun screensForLink(trackerId: Long): List<Screen> = listOf(Screen.Home, Screen.TrackerDetail(trackerId))
