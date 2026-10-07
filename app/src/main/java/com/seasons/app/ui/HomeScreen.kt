package com.seasons.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
fun HomeScreen(vm: HomeViewModel, onOpen: (Long) -> Unit, onNew: () -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Text("+", fontSize = 24.sp) }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val list = rows
            when {
                list == null -> Unit
                list.isEmpty() -> Text(
                    "No trackers yet.\nTap + to add one.",
                    color = Grey1,
                    modifier = Modifier.padding(24.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    item {
                        Text("Seasons", style = MaterialTheme.typography.headlineMedium, color = Grey1)
                    }
                    items(list, key = { it.tracker.id }) { row ->
                        TrackerRow(row, onClick = { onOpen(row.tracker.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerRow(row: HomeRow, onClick: () -> Unit) {
    val t = row.tracker
    val color = Color(t.color)
    val main = if (t.type == TrackerType.GOAL) {
        "${formatAmount(row.done)} / ${formatAmount(t.target ?: 0.0)} ${t.unit}"
    } else {
        "${formatAmount(row.todayTotal)} ${t.unit} today"
    }
    val weekly = t.bandPeriod == BandPeriod.WEEKLY

    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Text(t.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (row.metToday) "Met today" else "Not yet", color = if (row.metToday) color else Grey1)
            Spacer(Modifier.width(12.dp))
            Text("Streak ${row.streak}${if (weekly) " wk" else ""}", color = Grey1)
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
