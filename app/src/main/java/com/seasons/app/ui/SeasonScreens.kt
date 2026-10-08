@file:OptIn(ExperimentalLayoutApi::class)

package com.seasons.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasons.app.data.SeasonTrackerStat
import com.seasons.app.data.Settings
import com.seasons.app.data.seasonBase
import com.seasons.app.data.validateSeasonChange
import com.seasons.app.domain.seasonDay
import com.seasons.app.domain.seasonNumber
import com.seasons.app.ui.charts.shortDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun range(start: LocalDate, end: LocalDate, today: LocalDate) =
    "${shortDate(start, today)} – ${shortDate(end, today)}"

// ---------- First launch ----------

/** Cannot be dismissed: the app needs a season start and length. Defaults are filled in. */
@Composable
fun SetupDialog(onSave: (LocalDate, Int) -> Unit) {
    val today = LocalDate.now()
    var start by rememberSaveable { mutableStateOf(today) }
    var lengthText by rememberSaveable { mutableStateOf(Settings.DEFAULT_SEASON_LENGTH.toString()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val length = lengthText.trim().toIntOrNull()
    val valid = length != null && length in Settings.MIN_SEASON_LENGTH..Settings.MAX_SEASON_LENGTH

    if (picking) {
        DatePickerModal(initial = start, min = null, max = today, onPick = { start = it }, onDismiss = { picking = false })
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Set up your seasons") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("A season is a block of days to look back on. You can change this later in Settings.", color = Grey1)
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    label = { Text("Season length in days") },
                    isError = !valid,
                    supportingText = { Text("${Settings.MIN_SEASON_LENGTH} to ${Settings.MAX_SEASON_LENGTH}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("First season starts", color = Grey1, style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { picking = true }) { Text(formatDate(start, today)) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(start, length!!) }) { Text("Start") }
        },
    )
}

// ---------- Settings ----------

@Composable
fun SettingsScreen(vm: SeasonsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var changing by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val s = settings

    if (changing && s != null) {
        ChangeSeasonDialog(s, today, onSave = { length, start -> vm.changeSeason(length, start) }, onDismiss = { changing = false })
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
            item { ScreenHeader("Settings", onBack) }
            if (s != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Season", color = Grey1, style = MaterialTheme.typography.labelLarge)
                        val base = seasonBase(items.orEmpty().map { it.summary }, s.seasonStartDate)
                        if (today.isBefore(s.seasonStartDate)) {
                            Text("Starts ${formatDate(s.seasonStartDate, today)}", fontSize = 24.sp)
                        } else {
                            val n = base + seasonNumber(today, s.seasonStartDate, s.seasonLength)
                            val day = seasonDay(today, s.seasonStartDate, s.seasonLength)
                            Text("Season $n · day $day/${s.seasonLength}", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Season length", color = Grey1, style = MaterialTheme.typography.labelLarge)
                        Text("${s.seasonLength} days")
                        Spacer(Modifier.height(8.dp))
                        Text("Counting started", color = Grey1, style = MaterialTheme.typography.labelLarge)
                        Text(formatDate(s.seasonStartDate, today))
                    }
                }
                item {
                    OutlinedButton(onClick = { changing = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Change season")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeSeasonDialog(
    current: Settings,
    today: LocalDate,
    onSave: (Int, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var lengthText by rememberSaveable { mutableStateOf(current.seasonLength.toString()) }
    var start by rememberSaveable { mutableStateOf(today) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val length = lengthText.trim().toIntOrNull()
    val error = if (length == null) {
        "Enter a number of days"
    } else {
        validateSeasonChange(current.seasonStartDate, length, start, current.seasonLength, today)
    }
    val runningStart = if (today.isBefore(current.seasonStartDate)) null else {
        com.seasons.app.domain.seasonStartDate(
            seasonNumber(today, current.seasonStartDate, current.seasonLength), current.seasonStartDate, current.seasonLength,
        )
    }

    if (picking) {
        DatePickerModal(initial = start, min = runningStart, max = today, onPick = { start = it }, onDismiss = { picking = false })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change season") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    label = { Text("New season length in days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("New length starts on", color = Grey1, style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { picking = true }) { Text(formatDate(start, today)) }
                Text(
                    "The season running now is saved as a summary, up to the day before this date. Past summaries stay as they are.",
                    color = Grey1,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (error != null && lengthText.isNotBlank()) Text(error, color = Amber)
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = {
                    onSave(length!!, start)
                    onDismiss()
                },
            ) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------- History ----------

@Composable
fun SeasonsHistoryScreen(vm: SeasonsViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    Scaffold { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScreenHeader("Seasons", onBack) }
            val list = items
            if (list != null && list.isEmpty()) {
                item { Text("No finished seasons yet. A summary is saved when a season ends.", color = Grey1) }
            }
            items(list.orEmpty(), key = { it.summary.id }) { item ->
                val s = item.summary
                Column(Modifier.fillMaxWidth().clickable { onOpen(s.id) }.padding(vertical = 4.dp)) {
                    Text("Season ${s.seasonNumber}", style = MaterialTheme.typography.titleMedium)
                    Text(range(s.startDate, s.endDate, today), color = Grey1)
                    val days = ChronoUnit.DAYS.between(s.startDate, s.endDate).toInt() + 1
                    Text("${item.payload.daysActive} of $days days active", color = Grey1)
                }
            }
        }
    }
}

// ---------- One summary ----------

/** Closing this screen (button or system back) marks the summary as seen. */
@Composable
fun SeasonSummaryScreen(vm: SeasonsViewModel, summaryId: Long, onClose: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val item = items?.firstOrNull { it.summary.id == summaryId }
    val today = LocalDate.now()

    if (item == null) {
        Box(Modifier.fillMaxSize().padding(24.dp)) { TextButton(onClick = onClose) { Text("‹ Back") } }
        BackHandler(onBack = onClose)
        return
    }
    val s = item.summary
    val p = item.payload
    val close = { vm.markSeen(s.seasonNumber, onClose) }
    BackHandler(onBack = { close() })

    val days = ChronoUnit.DAYS.between(s.startDate, s.endDate).toInt() + 1

    Scaffold { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Column {
                    Row { TextButton(onClick = { close() }) { Text("‹ Back") } }
                    Text("Season ${s.seasonNumber}", style = MaterialTheme.typography.titleMedium, color = Grey1)
                    Text(range(s.startDate, s.endDate, today), color = Grey1)
                }
            }
            // The most important number: how many days you showed up.
            item {
                Column {
                    Text("${p.daysActive}", fontSize = 56.sp, fontWeight = FontWeight.Medium, lineHeight = 60.sp)
                    Text("of $days days active", color = Grey1, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (p.trackers.isEmpty()) {
                item { Text("Nothing was tracked this season.", color = Grey1) }
            } else {
                item { Text("Trackers", color = Grey1, style = MaterialTheme.typography.labelLarge) }
                items(p.trackers, key = { it.trackerId }) { t -> TrackerStatRow(t) }
            }
            if (p.trophies.isNotEmpty()) {
                item { Text("Trophies won", color = Grey1, style = MaterialTheme.typography.labelLarge) }
                items(p.trophies, key = { "trophy-${it.trackerId}" }) { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(t.color)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text("Finished ${shortDate(t.completedDate, today)}", color = Grey1, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${formatAmount(t.total)} ${t.unit}")
                    }
                }
            }
            item {
                Button(onClick = { close() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun TrackerStatRow(t: SeasonTrackerStat) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(t.color)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Best streak ${t.bestStreak} " + (if (t.weekly) "week" else "day") + (if (t.bestStreak == 1) "" else "s"),
                color = Grey1,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text("${formatAmount(t.total)} ${t.unit}", fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}
