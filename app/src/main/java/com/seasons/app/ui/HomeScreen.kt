@file:OptIn(ExperimentalLayoutApi::class)

package com.seasons.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasons.app.data.BandPeriod
import com.seasons.app.data.TrackerType

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onGroups: () -> Unit,
    onTrophies: () -> Unit,
    onArchive: () -> Unit,
    onSeasons: () -> Unit,
    onSettings: () -> Unit,
) {
    val sections by vm.sections.collectAsStateWithLifecycle()
    val needsSetup by vm.needsSetup.collectAsStateWithLifecycle()
    if (needsSetup) SetupDialog(onSave = vm::saveSetup)
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNew, containerColor = Grey3, contentColor = TextMain) {
                Text("+", fontSize = 24.sp)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val list = sections
            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    Column {
                        Text("Seasons", style = MaterialTheme.typography.headlineMedium, color = Grey1)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onGroups) { Text("Groups") }
                            TextButton(onClick = onTrophies) { Text("Trophies") }
                            TextButton(onClick = onArchive) { Text("Archive") }
                            TextButton(onClick = onSeasons) { Text("Seasons") }
                            TextButton(onClick = onSettings) { Text("Settings") }
                        }
                    }
                }
                if (list != null && list.isEmpty()) {
                    item { Text("No trackers yet.\nTap + to add one.", color = Grey1) }
                }
                // Headers only when at least one group exists.
                val showHeaders = list?.any { it.group != null } == true
                list?.forEach { section ->
                    if (showHeaders) {
                        item(key = "header-${section.group?.id ?: "none"}") {
                            SectionHeader(section.group)
                        }
                    }
                    items(section.rows, key = { it.tracker.id }) { row ->
                        TrackerRow(row, onClick = { onOpen(row.tracker.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(group: com.seasons.app.data.TrackerGroup?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (group != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(group.color)))
            Spacer(Modifier.width(8.dp))
        }
        Text(group?.name ?: "Ungrouped", color = Grey1, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun TrackerRow(row: HomeRow, onClick: () -> Unit) {
    val t = row.tracker
    val color = Color(t.color)
    val main = row.mainText()

    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Text(t.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(row.statusText(), color = if (row.metToday) color else Grey1)
            Spacer(Modifier.width(12.dp))
            Text(row.streakText(), color = Grey1)
        }
        Spacer(Modifier.height(6.dp))
        Text(main, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { row.progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = color,
            trackColor = Grey3,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}
