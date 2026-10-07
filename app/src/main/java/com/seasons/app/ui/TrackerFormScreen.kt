@file:OptIn(ExperimentalLayoutApi::class)

package com.seasons.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.seasons.app.data.BandPeriod
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerType
import java.time.LocalDate

/** Loads the tracker when editing, then shows the form. [trackerId] null = create. */
@Composable
fun TrackerFormScreen(
    vm: FormViewModel,
    trackerId: Long?,
    onDone: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    if (trackerId == null) {
        TrackerFormContent(vm, existing = null, onDone = onDone, onDeleted = onDeleted, onBack = onBack)
        return
    }
    val loaded by produceState<Tracker?>(null, trackerId) { value = vm.load(trackerId) }
    val tracker = loaded
    if (tracker == null) {
        Box(Modifier.fillMaxSize().padding(24.dp)) { Text("Loading…", color = Grey1) }
    } else {
        TrackerFormContent(vm, existing = tracker, onDone = onDone, onDeleted = onDeleted, onBack = onBack)
    }
}

@Composable
private fun TrackerFormContent(
    vm: FormViewModel,
    existing: Tracker?,
    onDone: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val today = LocalDate.now()
    val editing = existing != null

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var unit by rememberSaveable { mutableStateOf(existing?.unit ?: "") }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: TrackerType.GOAL) }
    var target by rememberSaveable { mutableStateOf(existing?.target?.let { formatAmount(it) } ?: "") }
    var deadline by rememberSaveable { mutableStateOf(existing?.deadline) }
    var bandPeriod by rememberSaveable { mutableStateOf(existing?.bandPeriod ?: BandPeriod.NONE) }
    var lower by rememberSaveable { mutableStateOf("") }
    var upper by rememberSaveable { mutableStateOf("") }
    var startDate by rememberSaveable { mutableStateOf(existing?.startDate ?: today) }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: ColorPalette[0]) }
    var groupId by rememberSaveable { mutableStateOf(existing?.groupId) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingDeadline by rememberSaveable { mutableStateOf(false) }
    var pickingStart by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${existing.name}\"?") },
            text = { Text("This permanently deletes the tracker and all its logs. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(existing, onDeleted) }) { Text("Delete", color = Amber) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (pickingDeadline) {
        DatePickerModal(
            initial = deadline ?: today,
            min = startDate,
            max = null,
            onPick = { deadline = it },
            onDismiss = { pickingDeadline = false },
        )
    }
    if (pickingStart) {
        DatePickerModal(
            initial = startDate,
            min = null,
            max = today,
            onPick = { startDate = it },
            onDismiss = { pickingStart = false },
        )
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row {
                TextButton(onClick = onBack) { Text("‹ Back") }
            }
            Text(
                if (editing) "Edit tracker" else "New tracker",
                style = MaterialTheme.typography.headlineMedium,
            )

            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = unit, onValueChange = { unit = it }, singleLine = true,
                label = { Text("Unit (pages, topics, laps…)") }, modifier = Modifier.fillMaxWidth(),
            )

            if (!editing) {
                Label("Type")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrackerType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = {
                                type = option
                                if (bandPeriod !in allowedBandPeriods(option)) {
                                    bandPeriod = allowedBandPeriods(option).first()
                                }
                            },
                            label = { Text(if (option == TrackerType.GOAL) "Goal" else "Ongoing") },
                        )
                    }
                }
            }

            if (type == TrackerType.GOAL) {
                OutlinedTextField(
                    value = target, onValueChange = { target = it }, singleLine = true,
                    label = { Text("Target") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Label("Deadline (optional)")
                Row {
                    TextButton(onClick = { pickingDeadline = true }) {
                        Text(deadline?.let { formatDate(it, today) } ?: "No deadline")
                    }
                    if (deadline != null) TextButton(onClick = { deadline = null }) { Text("Clear") }
                }
            }

            if (!editing) {
                Label("Band")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allowedBandPeriods(type).forEach { option ->
                        FilterChip(
                            selected = bandPeriod == option,
                            onClick = { bandPeriod = option },
                            label = {
                                Text(
                                    when (option) {
                                        BandPeriod.NONE -> "None"
                                        BandPeriod.DAILY -> "Daily"
                                        BandPeriod.WEEKLY -> "Weekly"
                                    },
                                )
                            },
                        )
                    }
                }
                if (bandPeriod != BandPeriod.NONE) {
                    val suffix = if (bandPeriod == BandPeriod.DAILY) "per day" else "active days per week"
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = lower, onValueChange = { lower = it }, singleLine = true,
                            label = { Text("Lower") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = upper, onValueChange = { upper = it }, singleLine = true,
                            label = { Text("Upper") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Text(suffix, color = Grey1, style = MaterialTheme.typography.bodySmall)
                }

                Label("Start date")
                Row {
                    TextButton(onClick = { pickingStart = true }) { Text(formatDate(startDate, today)) }
                }
            }

            val groups by vm.groups.collectAsStateWithLifecycle()
            if (groups.isNotEmpty()) {
                Label("Group")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = groupId == null, onClick = { groupId = null }, label = { Text("None") })
                    groups.forEach { g ->
                        FilterChip(selected = groupId == g.id, onClick = { groupId = g.id }, label = { Text(g.name) })
                    }
                }
            }

            Label("Color")
            ColorPicker(color) { color = it }

            error?.let { Text(it, color = Amber) }

            Button(
                onClick = {
                    val input = TrackerFormInput(name, unit, type, target, deadline, bandPeriod, lower, upper, startDate)
                    error = validateTracker(input, today, editing)
                    if (error == null) {
                        if (existing != null) {
                            vm.update(
                                existing.copy(
                                    name = name.trim(),
                                    unit = unit.trim(),
                                    color = color,
                                    groupId = groupId,
                                    target = if (type == TrackerType.GOAL) parseAmount(target) else null,
                                    deadline = if (type == TrackerType.GOAL) deadline else null,
                                ),
                                onDone,
                            )
                        } else {
                            val hasBand = bandPeriod != BandPeriod.NONE
                            vm.create(
                                Tracker(
                                    name = name.trim(),
                                    unit = unit.trim(),
                                    type = type,
                                    color = color,
                                    groupId = groupId,
                                    target = if (type == TrackerType.GOAL) parseAmount(target) else null,
                                    deadline = if (type == TrackerType.GOAL) deadline else null,
                                    bandPeriod = bandPeriod,
                                    startDate = startDate,
                                ),
                                if (hasBand) parseAmount(lower) else null,
                                if (hasBand) parseAmount(upper) else null,
                                onDone,
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (editing) "Save" else "Create") }

            if (editing) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Delete tracker", color = Amber) }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Grey1, style = MaterialTheme.typography.labelLarge)
}
