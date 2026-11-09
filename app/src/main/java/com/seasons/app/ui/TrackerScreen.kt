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
import androidx.compose.runtime.remember
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
import com.seasons.app.data.LogEntry
import com.seasons.app.data.TrackerStatus
import com.seasons.app.data.TrackerType
import com.seasons.app.ui.charts.BarChart
import com.seasons.app.domain.formatMinutes
import com.seasons.app.ui.charts.AveragePaceChart
import com.seasons.app.ui.charts.BarChartModel
import com.seasons.app.domain.formatClock
import com.seasons.app.ui.charts.ChartTabs
import com.seasons.app.ui.charts.StreakChart
import com.seasons.app.ui.charts.describeStreaks
import com.seasons.app.ui.charts.OnTrackChart
import com.seasons.app.ui.charts.describeBarChart
import com.seasons.app.ui.charts.describeAveragePace
import com.seasons.app.ui.charts.describeOnTrack
import com.seasons.app.ui.charts.SeasonHeatmap
import java.time.LocalDate
import java.time.YearMonth

/** Header, status line, today/quick-log, streaks, history. Charts come in Phase 4. */
@Composable
fun TrackerScreen(
    vm: TrackerViewModel,
    onEdit: (Long) -> Unit,
    onStopTimer: () -> Unit,
    onEditRunning: () -> Unit,
    onEditTime: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val timerInfo by vm.timer.collectAsStateWithLifecycle()
    val now by rememberNowMillis(timerInfo != null)
    var askStop by rememberSaveable { mutableStateOf(false) }
    val ui = state
    val today = LocalDate.now()

    var logDate by rememberSaveable { mutableStateOf(today) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    // History starts closed every time. Months are kept as one comma-separated text so it survives rotation.
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var openMonthsText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
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
    val months = remember(ui.logs) { groupHistory(ui.logs) }
    val openMonths = remember(openMonthsText) { openMonthsText.split(",").filter { it.isNotEmpty() }.toSet() }
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
    val running = timerInfo
    val thisRunning = running?.timer?.takeIf { it.trackerId == t.id }
    if (askStop && running != null) {
        AlertDialog(
            onDismissRequest = { askStop = false },
            title = { Text("Stop \"${running.trackerName}\" first?") },
            text = { Text("Only one timer runs at a time. Stopping it opens its time so you can save it.") },
            confirmButton = {
                TextButton(onClick = {
                    askStop = false
                    onStopTimer()
                }) { Text("Stop it") }
            },
            dismissButton = { TextButton(onClick = { askStop = false }) { Text("Cancel") } },
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
                        MoreButton(onClick = { menuOpen = true })
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
                        Text(t.name, color = Grey1, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (t.status == TrackerStatus.ACTIVE) {
                            StopwatchButton(
                                running = thisRunning != null,
                                color = color,
                                onClick = {
                                    when {
                                        running == null -> vm.startTimer()
                                        thisRunning != null -> onStopTimer()
                                        else -> askStop = true
                                    }
                                },
                            )
                        }
                    }
                    if (thisRunning != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Running ${formatClock(now - thisRunning.startTime)}", color = color)
                            TextButton(onClick = onEditRunning) { Text("Edit start") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(s.headline, fontSize = 40.sp, fontWeight = FontWeight.Medium, lineHeight = 46.sp)
                    s.detailLines.forEach { Text(it, color = Grey1, style = MaterialTheme.typography.titleMedium) }
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
            ui.timeLine?.let { line -> item { Text(line, color = Grey1) } }
            // 4. Streaks
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    StreakStat("Current streak", s.streaks.current)
                    StreakStat("Avg streak", s.streaks.average)
                    if (t.type == TrackerType.ONGOING) {
                        StreakStat("Total ${t.unit}", formatAmount(ui.logs.sumOf { it.amount }))
                    }
                }
            }
            // 5. Charts
            item { ChartsSection(ui, color, today) }
            // 6. Recent streaks
            ui.charts.streaks?.let { streaks ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (streaks.weekly) "Recent streaks (weeks)" else "Recent streaks (days)",
                            color = Grey1,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        StreakChart(streaks, color, describeStreaks(streaks))
                    }
                }
            }
            // 7. History
            // Closed by default, always. Inside: one section per month, this month open.
            item(key = "history-header") {
                CollapseHeader(
                    title = "History",
                    detail = if (ui.logs.size == 1) "1 entry" else "${ui.logs.size} entries",
                    open = historyOpen,
                    onClick = { historyOpen = !historyOpen },
                    color = Grey1,
                )
            }
            if (historyOpen) {
                if (ui.logs.isEmpty()) {
                    item(key = "history-empty") { Text("Nothing logged yet.", color = Grey1) }
                }
                months.forEach { month ->
                    val monthOpen = month.key in openMonths
                    item(key = "month-${month.key}") {
                        CollapseHeader(
                            title = month.title,
                            detail = monthSummary(month, t.unit),
                            open = monthOpen,
                            onClick = {
                                openMonthsText = (if (monthOpen) openMonths - month.key else openMonths + month.key).joinToString(",")
                            },
                            color = TextMain,
                        )
                    }
                    if (monthOpen) {
                        month.days.forEach { day ->
                            item(key = "day-${day.date}") {
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Text(dayHeader(day.date, today), color = Grey1, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text("${formatAmount(day.total)} ${t.unit}", color = Grey1, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            items(day.entries, key = { "entry-${it.id}" }) { entry ->
                                HistoryRow(
                                    entry, t.unit,
                                    onEdit = { if (entry.isTimed) onEditTime(entry.id) else editingId = entry.id },
                                    onDelete = { deletingId = entry.id },
                                )
                            }
                        }
                    }
                }
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
        // A goal with a deadline is judged against a needed pace. Without one, the same chart is just "Pace".
        if (ui.charts.onTrack != null) add(if (ui.tracker.deadline != null) "On track" else "Pace")
        if (ui.charts.pace != null) add("Pace")
        if (ui.charts.time != null) add("Time")
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
            "Time" -> {
                val time = ui.charts.time
                if (time != null) TimeTab(time, ui.charts.daily, color, ui.tracker.unit)
            }
            else -> {
                val onTrack = ui.charts.onTrack
                val pace = ui.charts.pace
                if (onTrack != null) {
                    OnTrackChart(onTrack, color, describeOnTrack(onTrack, ui.tracker.unit))
                } else if (pace != null) {
                    AveragePaceChart(pace, color, describeAveragePace(pace, ui.tracker.unit))
                } else {
                    Text("Nothing to show yet.", color = Grey1)
                }
            }
        }
    }
}

/** Time per day on top, amount per day below. Same 14 days, each chart on its own scale. */
@Composable
private fun TimeTab(time: TimeTabModel, amount: BarChartModel, color: Color, unit: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Time per day", color = Grey1, style = MaterialTheme.typography.labelLarge)
        BarChart(time.minutes, Grey1, describeTimeChart(time.minutes), valueText = { formatMinutes(it.toLong()) })
        Text("$unit per day", color = Grey1, style = MaterialTheme.typography.labelLarge)
        BarChart(amount, color, describeBarChart("Amount chart, last 14 days", amount, unit))
        time.perHour?.let { Text(it, fontWeight = FontWeight.Medium) }
        Text("Last 14 days. Only stopwatch entries count toward the hourly average.", color = Grey1, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StreakStat(label: String, value: String) {
    Column {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Text(label, color = Grey1, style = MaterialTheme.typography.bodySmall)
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

/** A tappable section title with a count or total on the right and a small arrow. */
@Composable
private fun CollapseHeader(title: String, detail: String, open: Boolean, onClick: () -> Unit, color: Color) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (open) "▾" else "▸", color = Grey1, modifier = Modifier.width(20.dp))
        Text(title, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(detail, color = Grey1, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HistoryRow(entry: LogEntry, unit: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${formatAmount(entry.amount)} $unit")
            entry.timeText()?.let { Text(it, color = Grey1, style = MaterialTheme.typography.bodySmall) }
        }
        TextButton(onClick = onEdit) { Text("Edit") }
        TextButton(onClick = onDelete) { Text("Delete", color = Amber) }
    }
}
