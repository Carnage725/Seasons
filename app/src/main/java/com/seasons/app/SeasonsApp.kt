package com.seasons.app

import android.app.Application
import androidx.work.ExistingWorkPolicy
import com.seasons.app.data.AppDatabase
import com.seasons.app.data.Repository
import com.seasons.app.widget.WidgetUpdater
import com.seasons.app.widget.scheduleMidnightRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class SeasonsApp : Application() {
    val repository: Repository by lazy { Repository(AppDatabase.get(this)) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()

        // Any log or edit changes the database. A moment later the widgets are redrawn.
        appScope.launch {
            combine(
                repository.observeTrackers(),
                repository.observeAllLogs(),
                repository.observeAllBands(),
                repository.observeGroups(),
            ) { _, _, _, _ -> }
                .debounce(400)
                .collect { WidgetUpdater.updateAll(applicationContext) }
        }

        // Redraw at local midnight so "today" values roll over.
        scheduleMidnightRefresh(this, ExistingWorkPolicy.REPLACE)
    }
}
