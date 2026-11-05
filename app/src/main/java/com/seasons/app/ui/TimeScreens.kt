@file:OptIn(ExperimentalMaterial3Api::class)

package com.seasons.app.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasons.app.domain.adjustMinutes
import com.seasons.app.domain.dateOfEnd
import com.seasons.app.domain.elapsedMinutes
import com.seasons.app.domain.formatMinutes
import com.seasons.app.domain.runningStartError
import com.seasons.app.domain.timeError
import com.seasons.app.domain.withDate
import com.seasons.app.domain.withTime
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------- Shared bits ----------

/** The current time in millis, refreshed every second while [active]. Used for the running clock. */
@Composable
fun rememberNowMillis(active: Boolean): State<Long> = produceState(System.currentTimeMillis(), active) {
    while (active) {
        value = System.currentTimeMillis()
        delay(1000)
    }
}

/** A small stopwatch icon button (drawn here, no icon library). Lit in the tracker color while its timer runs. */
@Composable
fun StopwatchButton(running: Boolean, color: Color, onClick: () -> Unit) {
    val tint = if (running) color else Grey1
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = if (running) "Stop timer" else "Start timer" },
    ) {
        Canvas(Modifier.size(26.dp)) {
            val stroke = 2.dp.toPx()
            val center = Offset(size.width / 2, size.height * 0.58f)
            val radius = size.width * 0.38f
            drawCircle(tint, radius = radius, center = center, style = Stroke(width = stroke))
            // Button on top.
            drawLine(tint, Offset(size.width / 2, size.height * 0.08f), Offset(size.width / 2, center.y - radius), stroke, StrokeCap.Round)
            drawLine(tint, Offset(size.width * 0.38f, size.height * 0.08f), Offset(size.width * 0.62f, size.height * 0.08f), stroke, StrokeCap.Round)
            // Hand.
            drawLine(tint, center, Offset(center.x + radius * 0.5f, center.y - radius * 0.5f), stroke, StrokeCap.Round)
            if (running) drawCircle(tint, radius = 2.dp.toPx(), center = center)
        }
    }
}

// ---------- Edit time screen ----------

private val dateFormat = DateTimeFormatter.ofPattern("MMM d")

/**
 * Start and end of a stopwatch session, with amount. Used when a timer is stopped, when its start is fixed while
 * it runs, and when a saved stopwatch entry is edited.
 */
@Composable
fun TimeEditScreen(vm: TimeEditViewModel, onBack: () -> Unit) {
    val init by vm.init.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    val loaded = init
    if (loaded == null) {
        Scaffold { padding ->
            Column(Modifier.padding(padding).padding(24.dp)) {
                TextButton(onClick = onBack) { Text("‹ Back") }
                if (missing) {
                    Text(
                        if (vm.kind == TimeEditKind.ENTRY) "This entry no longer exists." else "No timer is running.",
                        color = Grey1,
                    )
                }
            }
        }
        return
    }
    TimeEditContent(vm, loaded, error, onBack)
}

@Composable
private fun TimeEditContent(vm: TimeEditViewModel, init: TimeEditInit, error: String?, onBack: () -> Unit) {
    val kind = vm.kind
    val zone = ZoneId.systemDefault()
    val color = Color(init.tracker.color)

    var start by rememberSaveable { mutableStateOf(init.start) }
    var end by rememberSaveable { mutableStateOf(init.end ?: init.start) }
    var amountText by rememberSaveable { mutableStateOf(init.amount?.let { formatAmount(it) } ?: "") }
    var pickDateFor by rememberSaveable { mutableStateOf<String?>(null) } // "start" or "end"
    var pickTimeFor by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val hasEnd = kind != TimeEditKind.RUNNING
    val hasAmount = kind != TimeEditKind.RUNNING
    val now = System.currentTimeMillis()
    val timeProblem = if (hasEnd) timeError(start, end, now) else runningStartError(start, now)
    val amount = parseAmount(amountText)
    val amountProblem = if (hasAmount && (amount == null || amount <= 0.0)) "Enter an amount above 0." else null
    val canSave = timeProblem == null && amountProblem == null

    // A stopped timer is not saved until the user decides. Back asks first, so a stray tap cannot lose the time.
    BackHandler(enabled = kind == TimeEditKind.STOP) { confirmDiscard = true }
    val onBackPressed = { if (kind == TimeEditKind.STOP) confirmDiscard = true else onBack() }

    pickDateFor?.let { which ->
        val millis = if (which == "start") start else end
        DatePickerModal(
            initial = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(),
            min = null,
            max = LocalDate.now(),
            onPick = { date -> if (which == "start") start = withDate(start, date, zone) else end = withDate(end, date, zone) },
            onDismiss = { pickDateFor = null },
        )
    }
    pickTimeFor?.let { which ->
        val local = Instant.ofEpochMilli(if (which == "start") start else end).atZone(zone)
        ClockDialog(
            hour = local.hour,
            minute = local.minute,
            onPick = { h, m -> if (which == "start") start = withTime(start, h, m, zone) else end = withTime(end, h, m, zone) },
            onDismiss = { pickTimeFor = null },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this time?") },
            text = { Text("The timer is stopped and nothing is logged.") },
            confirmButton = {
                TextButton(onClick = { vm.discardTimer(onBack) }) { Text("Discard", color = Amber) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("${formatAmount(init.amount ?: 0.0)} ${init.tracker.unit}, ${formatMinutes(elapsedMinutes(init.start, init.end ?: init.start))}") },
            confirmButton = {
                TextButton(onClick = { vm.deleteEntry(onBack) }) { Text("Delete", color = Amber) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (error != null) {
        AlertDialog(
            onDismissRequest = vm::clearError,
            text = { Text(error) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
        )
    }

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBackPressed) { Text("‹ Back") }

            // Header strip: tracker, the range, the length.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(init.tracker.name, color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val endText = if (hasEnd) formatTimeOfDay(end, zone) else "now"
                    Text("${formatTimeOfDay(start, zone)}  -  $endText", color = Color.Black, fontSize = 15.sp)
                }
                val shownEnd = if (hasEnd) end else now
                Text(
                    if (shownEnd > start) formatMinutes(elapsedMinutes(start, shownEnd)) else "–",
                    color = Color.Black, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                )
            }

            TimeBox("Start", start, zone, onDate = { pickDateFor = "start" }, onTime = { pickTimeFor = "start" })
            AdjustRow(
                onAdjust = { start = adjustMinutes(start, it) },
                onNow = { start = System.currentTimeMillis() },
            )
            if (hasEnd) {
                TimeBox("End", end, zone, onDate = { pickDateFor = "end" }, onTime = { pickTimeFor = "end" })
                AdjustRow(
                    onAdjust = { end = adjustMinutes(end, it) },
                    onNow = { end = System.currentTimeMillis() },
                )
            }
            val problem = timeProblem ?: amountProblem.takeIf { amountText.isNotBlank() }
            if (problem != null) Text(problem, color = Amber)
            if (hasEnd && timeProblem == null) {
                Text("Counts for ${formatDate(dateOfEnd(end, zone), LocalDate.now())}", color = Grey1, style = MaterialTheme.typography.bodySmall)
            }

            if (hasAmount) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (${init.tracker.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    vm.save(start, if (hasEnd) end else null, amount, onBack)
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (kind == TimeEditKind.RUNNING) "Save start time" else "Save", fontWeight = FontWeight.Medium) }

            when (kind) {
                TimeEditKind.STOP -> TextButton(onClick = { confirmDiscard = true }) { Text("Discard time", color = Amber) }
                TimeEditKind.ENTRY -> TextButton(onClick = { confirmDelete = true }) { Text("Delete entry", color = Amber) }
                TimeEditKind.RUNNING -> Unit
            }
        }
    }
}

/** Label, then the date (small, grey) and the time (big). Tapping either opens its picker. */
@Composable
private fun TimeBox(label: String, millis: Long, zone: ZoneId, onDate: () -> Unit, onTime: () -> Unit) {
    val local = Instant.ofEpochMilli(millis).atZone(zone)
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Grey1, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                local.format(dateFormat),
                color = Grey1,
                fontSize = 20.sp,
                modifier = Modifier.clickable(onClick = onDate).padding(vertical = 8.dp, horizontal = 4.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                formatTimeOfDay(millis, zone),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onTime).padding(vertical = 4.dp, horizontal = 4.dp),
            )
        }
    }
}

/** -30 -5 -1 +1 +5 +30 Now. All in minutes. */
@Composable
private fun AdjustRow(onAdjust: (Int) -> Unit, onNow: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(-30, -5, -1, 1, 5, 30).forEach { d ->
            OutlinedButton(
                onClick = { onAdjust(d) },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .semantics { contentDescription = if (d < 0) "${-d} minutes earlier" else "$d minutes later" },
                contentPadding = PaddingValues(0.dp),
            ) { Text(if (d > 0) "+$d" else "$d", maxLines = 1, fontSize = 14.sp) }
        }
        OutlinedButton(
            onClick = onNow,
            modifier = Modifier.weight(1.3f).height(44.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Text("Now", maxLines = 1, fontSize = 14.sp) }
    }
}

@Composable
private fun ClockDialog(hour: Int, minute: Int, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onPick(state.hour, state.minute)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
