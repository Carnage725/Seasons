package com.seasons.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.seasons.app.data.BandPeriod
import com.seasons.app.data.LogEntry
import com.seasons.app.data.TrackerStatus
import com.seasons.app.ui.charts.BarChart
import com.seasons.app.ui.charts.ChartTabs
import com.seasons.app.ui.charts.OnTrackChart
import com.seasons.app.ui.charts.describeBarChart
import com.seasons.app.ui.charts.describeOnTrack
import com.seasons.app.ui.charts.SeasonHeatmap
import java.time.LocalDate

/** Header, status line, today/quick-log, streaks, history. Charts come in Phase 4. */
@Composable
fun TrackerScreen(
    vm: TrackerViewModel,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val ui = state
    val today = LocalDate.now()

    var logDate by rememberSaveable { mutableStateOf(today) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var changingBand by rememberSaveable { mutableStateOf(false) }
    var confirmArchive by rememberSaveable { mutableStateOf(false) }

    if (ui == null) {
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            TextButton(onClick = onBack) { Text("‹ Back") }
        }
        return
    }

    val t = ui.tracker
    val s = ui.summary
    val color = Color(t.color)
    val editingEntry = ui.logs.firstOrNull { it.id == editingId }
    val deletingEntry = ui.logs.firstOrNull { it.id == deletingId }

    if (pickingDate) {
        DatePickerModal(
            initial = logDate,
            min = t.startDate,
            max = today,
            onPick = { logDate = it },
            onDismiss = { pickingDate = false },
        )
    }
    if (customOpen) {
        AmountDialog(
            title = "Add ${t.unit}",
            initial = "",
            onConfirm = {
                vm.addLog(logDate, it)
                logDate = today
            },
            onDismiss = { customOpen = false },
        )
    }
    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Archive \"${t.name}\"?") },
            text = { Text("It leaves Home. Nothing is deleted. Bring it back from Archive on Home.") },
            confirmButton = {
                TextButton(onClick = { vm.archive(ui.tracker, onBack) }) { Text("Archive") }
            },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancel") } },
        )
    }
    if (changingBand) {
        ChangeBandDialog(
            period = t.bandPeriod,
            initialLower = ui.band?.lower,
            initialUpper = ui.band?.upper,
            startDate = t.startDate,
            today = today,
            onSave = { date, lower, upper -> vm.changeBand(date, lower, upper) },
            onDismiss = { changingBand = false },
        )
    }
    if (editingEntry != null) {
        EditLogDialog(
            initialDate = editingEntry.date,
            initialAmount = editingEntry.amount,
            startDate = t.startDate,
            today = today,
            onSave = { date, amount -> vm.editLog(editingEntry, date, amount) },
            onDismiss = { editingId = null },
        )
    }
    if (deletingEntry != null) {
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Delete this entry?") },
            text = {
                Text("${formatAmount(deletingEntry.amount)} ${t.unit} on ${formatDate(deletingEntry.date, today)}")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteLog(deletingEntry)
                        deletingId = null
                    },
                ) { Text("Delete", color = Amber) }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("Cancel") } },
        )
    }
    if (error != null) {
        AlertDialog(
            onDismissRequest = vm::clearError,
            text = { Text(error ?: "") },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
        )
    }

    Scaffold { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹ Back") }
                    Spacer(Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("Menu") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit tracker") },
                                onClick = {
                                    menuOpen = false
                                    onEdit(t.id)
                                },
                            )
                            if (t.status == TrackerStatus.ACTIVE) {
                                DropdownMenuItem(
                                    text = { Text("Archive") },
                                    onClick = {
                                        menuOpen = false
                                        confirmArchive = true
                                    },
                                )
                            }
                            if (t.bandPeriod != BandPeriod.NONE) {
                                DropdownMenuItem(
                                    text = { Text("Change band") },
                                    onClick = {
                                        menuOpen = false
                                        changingBand = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // 1. Header: the most important number is the biggest thing on screen.
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(t.name, color = Grey1, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(s.headline, fontSize = 40.sp, fontWeight = FontWeight.Medium, lineHeight = 46.sp)
                    s.subline?.let { Text(it, color = Grey1, style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = color,
                        trackColor = Grey3,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
            // Goal reached: celebrate and offer the trophy shelf.
            if (t.status == TrackerStatus.ACTIVE && s.goalReached) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Goal reached", color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("You hit ${formatAmount(t.target ?: 0.0)} ${t.unit}. Well done.", color = Grey1)
                        Button(
                            onClick = { vm.complete(ui.tracker, onBack) },
                            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("Move to trophy shelf", fontWeight = FontWeight.Medium) }
                    }
                }
            }
            // 2. Needed pace (goals with a deadline)
            s.needLine?.let { line -> item { Text(line, color = Grey1) } }
            // 3. Today: quick log
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.todayLine, color = Grey1)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        LogButton("+1", color, Modifier.weight(1f)) {
                            vm.addLog(logDate, 1.0)
                            logDate = today
                        }
                        LogButton("+5", color, Modifier.weight(1f)) {
                            vm.addLog(logDate, 5.0)
                            logDate = today
                        }
                        OutlinedButton(
                            onClick = { customOpen = true },
                            modifier = Modifier.weight(1f).height(52.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            Text("Custom", maxLines = 1)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Logging for", color = Grey1)
                        TextButton(onClick = { pickingDate = true }) { Text(formatDate(logDate, today)) }
                    }
                }
            }
            // 4. Streaks
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    StreakStat("Current", s.streaks.current, s.streaks.unit)
                    StreakStat("Best", s.streaks.best, s.streaks.unit)
                    StreakStat("Average", s.streaks.average, s.streaks.unit)
                }
            }
            // 5. Charts
            item { ChartsSection(ui, color, today) }
            // 6. History
            item {
                Text("History", color = Grey1, style = MaterialTheme.typography.labelLarge)
            }
            if (ui.logs.isEmpty()) {
                item { Text("Nothing logged yet.", color = Grey1) }
            }
            items(ui.logs, key = { it.id }) { entry ->
                HistoryRow(entry, t.unit, today, onEdit = { editingId = entry.id }, onDelete = { deletingId = entry.id })
            }
        }
    }
}

@Composable
private fun ChartsSection(ui: TrackerUiState, color: Color, today: LocalDate) {
    val tabs = buildList {
        add("Daily")
        add("Weekly")
        add("Season")
        if (ui.charts.onTrack != null) add("On track")
    }
    var selected by rememberSaveable { mutableStateOf(0) }
    val index = selected.coerceIn(0, tabs.lastIndex)

    Column {
        ChartTabs(tabs, index, onSelect = { selected = it })
        Spacer(Modifier.height(12.dp))
        when (tabs[index]) {
            "Daily" -> BarChart(ui.charts.daily, color, describeBarChart("Daily chart, last 14 days", ui.charts.daily, ui.tracker.unit))
            "Weekly" -> BarChart(ui.charts.weekly, color, describeBarChart("Weekly chart, last 11 weeks", ui.charts.weekly, ui.tracker.unit))
            "Season" -> {
                val season = ui.charts.season
                if (season == null) {
                    Text("Season has not started yet.", color = Grey1)
                } else {
                    SeasonHeatmap(season, color, ui.tracker.unit, today)
                }
            }
            else -> OnTrackChart(ui.charts.onTrack!!, color, describeOnTrack(ui.charts.onTrack, ui.tracker.unit))
        }
    }
}

@Composable
private fun StreakStat(label: String, value: String, unit: String) {
    Column {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Text(label, color = Grey1, style = MaterialTheme.typography.bodySmall)
        Text(unit, color = Grey1, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LogButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.Black),
    ) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun HistoryRow(entry: LogEntry, unit: String, today: LocalDate, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${formatAmount(entry.amount)} $unit")
            Text(formatDate(entry.date, today), color = Grey1, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onEdit) { Text("Edit") }
        TextButton(onClick = onDelete) { Text("Delete", color = Amber) }
    }
}
