package com.seasons.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.seasons.app.ui.FormViewModel
import com.seasons.app.ui.HomeScreen
import com.seasons.app.ui.HomeViewModel
import com.seasons.app.ui.NavViewModel
import com.seasons.app.ui.Screen
import com.seasons.app.ui.SeasonsTheme
import com.seasons.app.ui.TrackerFormScreen
import com.seasons.app.ui.TrackerScreen
import com.seasons.app.ui.TrackerViewModel
import com.seasons.app.ui.repository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeasonsTheme { AppRoot() }
        }
    }
}

@Composable
private fun AppRoot(nav: NavViewModel = viewModel()) {
    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    when (val screen = nav.stack.last()) {
        Screen.Home -> {
            val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(repository()) } })
            HomeScreen(
                vm = vm,
                onOpen = { nav.push(Screen.TrackerDetail(it)) },
                onNew = { nav.push(Screen.TrackerForm(null)) },
            )
        }
        is Screen.TrackerDetail -> {
            val vm: TrackerViewModel = viewModel(
                key = "tracker-${screen.trackerId}",
                factory = viewModelFactory { initializer { TrackerViewModel(repository(), screen.trackerId) } },
            )
            TrackerScreen(
                vm = vm,
                onEdit = { nav.push(Screen.TrackerForm(it)) },
                onBack = nav::pop,
            )
        }
        is Screen.TrackerForm -> {
            val vm: FormViewModel = viewModel(
                key = "form-${screen.trackerId}",
                factory = viewModelFactory { initializer { FormViewModel(repository()) } },
            )
            TrackerFormScreen(
                vm = vm,
                trackerId = screen.trackerId,
                onDone = nav::pop,
                // Form was opened from the tracker screen, so go back past both.
                onDeleted = {
                    nav.pop()
                    nav.pop()
                },
                onBack = nav::pop,
            )
        }
    }
}
