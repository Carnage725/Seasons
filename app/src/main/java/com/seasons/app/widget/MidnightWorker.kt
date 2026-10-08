package com.seasons.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val UNIQUE_NAME = "midnight-widget-refresh"

/** Runs just after local midnight: redraws the widgets so "today" rolls over, then books the next midnight. */
class MidnightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.updateAll(applicationContext)
        scheduleMidnightRefresh(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

/**
 * Books one refresh for the next local midnight (plus a few seconds, so it is surely past it).
 * The app also calls this on every start with REPLACE, which re-plans it after a timezone change.
 */
fun scheduleMidnightRefresh(context: Context, policy: ExistingWorkPolicy) {
    val delay = millisUntilNextMidnight(ZonedDateTime.now()) + 5_000
    val request = OneTimeWorkRequestBuilder<MidnightWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request)
}
