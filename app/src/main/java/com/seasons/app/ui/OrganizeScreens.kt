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

// ---------- Groups list ----------

@Composable
fun GroupsScreen(vm: GroupsViewModel, onOpenGroup: (Long) -> Unit, onBack: () -> Unit) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    if (creating) {
        GroupDialog(
            title = "New group",
            initialName = "",
            initialColor = ColorPalette[0],
            onSave = { name, color -> vm.add(name, color) },
            onDismiss = { creating = false },
        )
    }

    Scaffold { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScreenHeader("Groups", onBack) }
            item { TextButton(onClick = { creating = true }) { Text("New group") } }
            val list = groups
            if (list != null && list.isEmpty()) {
                item { Text("No groups yet.", color = Grey1) }
            }
            list?.forEachIndexed { index, g ->
                item(key = g.id) {
                    val count = rows.count { it.tracker.groupId == g.id }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).clickable { onOpenGroup(g.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(g.color)))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(g.name, style = MaterialTheme.typography.titleMedium)
                                Text(if (count == 1) "1 tracker" else "$count trackers", color = Grey1)
                            }
                        }
                        TextButton(enabled = index > 0, onClick = { vm.move(g.id, up = true) }) { Text("Up") }
                        TextButton(enabled = index < list.lastIndex, onClick = { vm.move(g.id, up = false) }) { Text("Down") }
                    }
                }
            }
        }
    }
}

// ---------- Group detail ----------

@Composable
fun GroupDetailScreen(
    vm: GroupsViewModel,
    groupId: Long,
    onOpenTracker: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    val group = groups?.firstOrNull { it.id == groupId }
    if (group == null) {
        Box(Modifier.fillMaxSize().padding(24.dp)) { TextButton(onClick = onBack) { Text("‹ Back") } }
        return
    }
    val members = rows.filter { it.tracker.groupId == groupId }

    if (editing) {
        GroupDialog(
            title = "Edit group",
            initialName = group.name,
            initialColor = group.color,
            onSave = { name, color -> vm.edit(group, name, color) },
            onDismiss = { editing = false },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete \"${group.name}\"?") },
            text = { Text("The group is deleted. Its trackers are kept and become ungrouped.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(group, onBack) }) { Text("Delete", color = Amber) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }

    Scaffold { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹ Back") }
                    Spacer(Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("Menu") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Edit group") }, onClick = {
                                menuOpen = false
                                editing = true
                            })
                            DropdownMenuItem(text = { Text("Delete group", color = Amber) }, onClick = {
                                menuOpen = false
                                deleting = true
                            })
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(group.color)))
                    Spacer(Modifier.width(10.dp))
                    Text(group.name, style = MaterialTheme.typography.headlineMedium)
                }
            }
            if (members.isEmpty()) {
                item {
                    Text("No trackers in this group. Pick it when you create or edit a tracker.", color = Grey1)
                }
            }
            items(members, key = { it.tracker.id }) { row ->
                TrackerRow(row, onClick = { onOpenTracker(row.tracker.id) })
            }
        }
    }
}

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
