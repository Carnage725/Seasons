package com.seasons.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasons.app.data.Tracker
import java.time.LocalDate

// ---------- Trophy shelf ----------

@Composable
fun TrophiesScreen(vm: TrophiesViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    Scaffold { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { ScreenHeader("Trophy shelf", onBack) }
            val list = cards
            if (list != null && list.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("No finished goals yet. Reach a goal's target to move it here.", color = Grey1)
                }
            }
            items(list.orEmpty(), key = { it.tracker.id }) { card ->
                TrophyCardView(card, today, onClick = { onOpen(card.tracker.id) })
            }
        }
    }
}

@Composable
private fun TrophyCardView(card: TrophyCard, today: LocalDate, onClick: () -> Unit) {
    val t = card.tracker
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppSurface)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color(t.color)))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t.name, style = MaterialTheme.typography.titleMedium)
            Text("${formatAmount(card.total)} ${t.unit}", fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(
                if (card.daysTaken == 1) "1 day" else "${card.daysTaken} days",
                color = Grey1,
            )
            Text(
                "${com.seasons.app.ui.charts.shortDate(t.startDate, today)} → ${com.seasons.app.ui.charts.shortDate(card.finished, today)}",
                color = Grey1,
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Best streak ${card.bestStreak}", color = Grey1, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------- Archive ----------

@Composable
fun ArchiveScreen(vm: ArchiveViewModel, onBack: () -> Unit) {
    val trackers by vm.trackers.collectAsStateWithLifecycle()
    var pickedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val picked: Tracker? = trackers?.firstOrNull { it.id == pickedId }

    if (picked != null) {
        AlertDialog(
            onDismissRequest = { pickedId = null },
            title = { Text("Unarchive \"${picked.name}\"") },
            text = {
                Text(
                    "Continue where you left off keeps all logs. " +
                        "Start fresh makes a new tracker with the same settings and leaves this one archived.",
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        vm.continueWhereLeftOff(picked)
                        pickedId = null
                    }) { Text("Continue where you left off") }
                    TextButton(onClick = {
                        vm.startFresh(picked)
                        pickedId = null
                    }) { Text("Start fresh") }
                    TextButton(onClick = { pickedId = null }) { Text("Cancel", color = Grey1) }
                }
            },
        )
    }

    Scaffold { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScreenHeader("Archive", onBack) }
            val list = trackers
            if (list != null && list.isEmpty()) {
                item { Text("Nothing archived.", color = Grey1) }
            }
            items(list.orEmpty(), key = { it.id }) { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { pickedId = t.id }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(t.color)))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t.name, style = MaterialTheme.typography.titleMedium)
                        Text("Started ${formatDate(t.startDate, LocalDate.now())}", color = Grey1)
                    }
                }
            }
        }
    }
}
