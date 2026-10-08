package com.seasons.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.seasons.app.SeasonsApp
import com.seasons.app.data.TrackerStatus
import com.seasons.app.ui.Grey1
import com.seasons.app.ui.SeasonsTheme
import kotlinx.coroutines.launch

/** Opens when a widget is placed (and when it is reconfigured). Picks the tracker or group the widget shows. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        // Backing out must not add a widget that has nothing to show.
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className.orEmpty()
        val forGroup = provider.endsWith("GroupWidgetReceiver")
        val repo = (application as SeasonsApp).repository

        fun choose(target: WidgetTarget) {
            lifecycleScope.launch {
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply { this[WidgetState.TARGET] = target.encode() }
                }
                if (forGroup) GroupWidget().update(this@WidgetConfigActivity, glanceId)
                else TrackerWidget().update(this@WidgetConfigActivity, glanceId)
                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                finish()
            }
        }

        setContent {
            SeasonsTheme {
                Scaffold { padding ->
                    LazyColumn(
                        Modifier.padding(padding),
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                if (forGroup) "Choose a group" else "Choose a tracker",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                        if (forGroup) groupChoices(repo, ::choose) else trackerChoices(repo, ::choose)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.trackerChoices(
    repo: com.seasons.app.data.Repository,
    choose: (WidgetTarget) -> Unit,
) {
    item {
        val trackers by remember { repo.observeTrackers() }.collectAsStateWithLifecycle(initialValue = null)
        val active = trackers.orEmpty().filter { it.status == TrackerStatus.ACTIVE }
        androidx.compose.foundation.layout.Column {
            if (trackers != null && active.isEmpty()) Text("No trackers yet. Create one in the app first.", color = Grey1)
            active.forEach { t ->
                Choice(t.name, t.color) { choose(WidgetTarget.OfTracker(t.id)) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.groupChoices(
    repo: com.seasons.app.data.Repository,
    choose: (WidgetTarget) -> Unit,
) {
    item {
        val groups by remember { repo.observeGroups() }.collectAsStateWithLifecycle(initialValue = null)
        androidx.compose.foundation.layout.Column {
            if (groups != null && groups.orEmpty().isEmpty()) Text("No groups yet. Create one in the app first.", color = Grey1)
            groups.orEmpty().forEach { g ->
                Choice(g.name, g.color) { choose(WidgetTarget.OfGroup(g.id)) }
            }
        }
    }
}

@Composable
private fun Choice(name: String, color: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(color)))
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.titleMedium)
    }
}
