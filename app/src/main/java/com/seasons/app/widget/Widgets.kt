package com.seasons.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.ContentScale
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.seasons.app.MainActivity
import com.seasons.app.SeasonsApp
import com.seasons.app.ui.AppBackground
import com.seasons.app.ui.EXTRA_GROUP_ID
import com.seasons.app.ui.EXTRA_TRACKER_ID
import com.seasons.app.ui.Grey1
import com.seasons.app.ui.Grey3
import com.seasons.app.ui.TextMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Same rules as the app: dark, grey by default, only the tracker color carries data.
// Glance has no Canvas, so a "dot" is a small colored box and the bar is Glance's own progress bar.

private fun c(color: Color) = ColorProvider(color)
private fun c(argb: Int) = ColorProvider(Color(argb))

private fun repo(context: Context) = (context.applicationContext as SeasonsApp).repository

private fun openTracker(context: Context, id: Long): Action = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        putExtra(EXTRA_TRACKER_ID, id)
        data = Uri.parse("seasons://tracker/$id") // makes each target a different PendingIntent
    },
)

private fun openGroup(context: Context, id: Long): Action = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        putExtra(EXTRA_GROUP_ID, id)
        data = Uri.parse("seasons://group/$id")
    },
)

private fun openApp(context: Context): Action = actionStartActivity(Intent(context, MainActivity::class.java))

@Composable
private fun Dot(argb: Int) {
    Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(c(argb))) {}
}

// ---------- Single tracker ----------

class TrackerWidget : GlanceAppWidget() {
    // Exact: the content is rebuilt for every size, so the widget can be resized freely.
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Reading the state here is what makes Glance redraw when the choice or the refresh counter changes.
            val prefs = currentState<Preferences>()
            val target = WidgetTarget.decode(prefs[WidgetState.TARGET]) as? WidgetTarget.OfTracker
            val refresh = prefs[WidgetState.REFRESH] ?: 0L
            val loaded by produceState<Loaded<SingleWidgetData>>(Loaded.Loading, target, refresh) {
                value = Loaded.Done(target?.let { withContext(Dispatchers.IO) { WidgetLoader.single(repo(context), it.id) } })
            }
            if (loaded is Loaded.Done) TrackerContent(context, target, (loaded as Loaded.Done<SingleWidgetData>).value)
            else Box(GlanceModifier.fillMaxSize().background(c(AppBackground)).cornerRadius(16.dp)) {}
        }
    }

    @Composable
    private fun TrackerContent(context: Context, target: WidgetTarget.OfTracker?, data: SingleWidgetData?) {
        val size: DpSize = LocalSize.current
        val roomy = size.height >= 150.dp
        val medium = size.height >= 100.dp
        val click = if (target != null) openTracker(context, target.id) else openApp(context)

        Column(
            GlanceModifier.fillMaxSize().background(c(AppBackground)).cornerRadius(16.dp).padding(12.dp).clickable(click),
        ) {
            if (data == null) {
                Text(
                    if (target == null) "Tap to choose a tracker" else "Tracker not found",
                    style = TextStyle(color = c(Grey1), fontSize = 13.sp),
                )
                return@Column
            }
            val showSubline = medium && data.subline != null
            val showNeed = roomy && data.needLine != null
            val showStatus = size.height >= 80.dp
            val headlineSp = if (roomy) 30.sp else if (medium) 24.sp else 18.sp

            // What is left for the chart after the text lines, so the picture is drawn at exactly the size it is shown.
            val used = 24f + 16f + 4f + headlineSp.value * 1.25f +
                (if (showSubline) 18f else 0f) + (if (showNeed) 18f else 0f) +
                5f + (if (showStatus) 6f + 16f else 0f) + 30f // widget text takes more room than its font size suggests
            val chartHeight = size.height.value - used
            val chartWidth = size.width.value - 24f
            val showChart = chartHeight >= 56f && chartWidth >= 90f

            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(data.color)
                Spacer(GlanceModifier.width(6.dp))
                Text(data.name, maxLines = 1, style = TextStyle(color = c(Grey1), fontSize = 12.sp))
            }
            Spacer(GlanceModifier.height(4.dp))
            // The most important number is the biggest thing on the widget.
            Text(
                data.headline,
                maxLines = if (showChart) 1 else 2,
                style = TextStyle(color = c(TextMain), fontSize = headlineSp, fontWeight = FontWeight.Medium),
            )
            if (showSubline) {
                Text(data.subline!!, maxLines = 1, style = TextStyle(color = c(Grey1), fontSize = 13.sp))
            }
            if (showNeed) {
                Spacer(GlanceModifier.height(2.dp))
                Text(data.needLine!!, maxLines = 1, style = TextStyle(color = c(Grey1), fontSize = 12.sp))
            }
            if (showChart) {
                val density = context.resources.displayMetrics.density
                val bitmap = remember(data.chart, data.color, chartWidth, chartHeight) {
                    renderBarChartBitmap(
                        data.chart, data.color,
                        (chartWidth * density).toInt(), (chartHeight * density).toInt(), density,
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "Last 14 days",
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().height(chartHeight.dp),
                )
                Spacer(GlanceModifier.height(8.dp))
            } else {
                Spacer(GlanceModifier.defaultWeight())
            }
            LinearProgressIndicator(
                progress = data.progress,
                modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                color = c(data.color),
                backgroundColor = c(Grey3),
            )
            if (showStatus) {
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        data.status,
                        style = TextStyle(color = if (data.metToday) c(data.color) else c(Grey1), fontSize = 12.sp),
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(data.streak, style = TextStyle(color = c(Grey1), fontSize = 12.sp))
                }
            }
        }
    }
}

class TrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrackerWidget()
}

// ---------- Group ----------

class GroupWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val target = WidgetTarget.decode(prefs[WidgetState.TARGET]) as? WidgetTarget.OfGroup
            val refresh = prefs[WidgetState.REFRESH] ?: 0L
            val loaded by produceState<Loaded<GroupWidgetData>>(Loaded.Loading, target, refresh) {
                value = Loaded.Done(target?.let { withContext(Dispatchers.IO) { WidgetLoader.group(repo(context), it.id) } })
            }
            if (loaded is Loaded.Done) GroupContent(context, target, (loaded as Loaded.Done<GroupWidgetData>).value)
            else Box(GlanceModifier.fillMaxSize().background(c(AppBackground)).cornerRadius(16.dp)) {}
        }
    }

    @Composable
    private fun GroupContent(context: Context, target: WidgetTarget.OfGroup?, data: GroupWidgetData?) {
        Column(GlanceModifier.fillMaxSize().background(c(AppBackground)).cornerRadius(16.dp).padding(12.dp)) {
            if (data == null || target == null) {
                Text(
                    if (target == null) "Tap to choose a group" else "Group not found",
                    modifier = GlanceModifier.clickable(openApp(context)),
                    style = TextStyle(color = c(Grey1), fontSize = 13.sp),
                )
                return@Column
            }
            Row(
                GlanceModifier.fillMaxWidth().clickable(openGroup(context, target.id)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(data.color)
                Spacer(GlanceModifier.width(6.dp))
                Text(data.groupName, maxLines = 1, style = TextStyle(color = c(Grey1), fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
            Spacer(GlanceModifier.height(6.dp))
            if (data.rows.isEmpty()) {
                Text("No active trackers in this group", style = TextStyle(color = c(Grey1), fontSize = 12.sp))
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(data.rows, itemId = { it.trackerId }) { row -> GroupRow(context, row) }
                }
            }
        }
    }

    @Composable
    private fun GroupRow(context: Context, row: WidgetRow) {
        Column(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(openTracker(context, row.trackerId))) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Dot(row.color)
                Spacer(GlanceModifier.width(6.dp))
                Text(row.name, maxLines = 1, style = TextStyle(color = c(TextMain), fontSize = 14.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    row.status,
                    style = TextStyle(color = if (row.metToday) c(row.color) else c(Grey1), fontSize = 11.sp),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(row.streak, style = TextStyle(color = c(Grey1), fontSize = 11.sp))
            }
            Text(row.main, maxLines = 1, style = TextStyle(color = c(Grey1), fontSize = 12.sp))
            Spacer(GlanceModifier.height(3.dp))
            LinearProgressIndicator(
                progress = row.progress,
                modifier = GlanceModifier.fillMaxWidth().height(3.dp),
                color = c(row.color),
                backgroundColor = c(Grey3),
            )
        }
    }
}

class GroupWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GroupWidget()
}

/** While the data loads. Done(null) means "nothing to show" (no choice made, or the tracker or group is gone). */
sealed interface Loaded<out T> {
    data object Loading : Loaded<Nothing>
    data class Done<T>(val value: T?) : Loaded<T>
}

object WidgetUpdater {
    /**
     * Redraws every placed widget from the current data. Bumping the refresh counter in each widget's state is
     * what forces Glance to rebuild it, including after midnight when nothing else has changed.
     */
    suspend fun updateAll(context: Context) {
        refresh(context, TrackerWidget())
        refresh(context, GroupWidget())
    }

    private suspend fun refresh(context: Context, widget: GlanceAppWidget) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(widget.javaClass).forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply { this[WidgetState.REFRESH] = System.currentTimeMillis() }
            }
            widget.update(context, id)
        }
    }
}
