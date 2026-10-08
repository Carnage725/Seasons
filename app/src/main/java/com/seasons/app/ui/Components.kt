@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.seasons.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.seasons.app.data.BandPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Puts the cursor in the field and opens the keyboard as soon as the dialog shows,
 * so the first tap is not wasted on just focusing it.
 */
@Composable
private fun rememberAutoFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(100) // let the dialog window attach the field first
        runCatching { requester.requestFocus() }
        keyboard?.show()
    }
    return requester
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Material date picker. The picker works in UTC, so dates are converted in UTC to avoid off-by-one days. */
@Composable
fun DatePickerModal(
    initial: LocalDate,
    min: LocalDate?,
    max: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = utcTimeMillis.toUtcDate()
                return (min == null || !d.isBefore(min)) && (max == null || !d.isAfter(max))
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(it.toUtcDate()) }
                    onDismiss()
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun AmountDialog(
    title: String,
    initial: String,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val amount = parseAmount(text)
    val focus = rememberAutoFocus()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Amount") },
                modifier = Modifier.focusRequester(focus),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount > 0,
                onClick = {
                    onConfirm(amount!!)
                    onDismiss()
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Edit one log entry: amount and date. */
@Composable
fun EditLogDialog(
    initialDate: LocalDate,
    initialAmount: Double,
    startDate: LocalDate,
    today: LocalDate,
    onSave: (LocalDate, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val start = formatAmount(initialAmount)
        mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) // selected, so typing replaces it
    }
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val amount = parseAmount(field.text)
    val focus = rememberAutoFocus()

    if (picking) {
        DatePickerModal(
            initial = date,
            min = startDate,
            max = today,
            onPick = { date = it },
            onDismiss = { picking = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry") },
        text = {
            Column {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    label = { Text("Amount") },
                    modifier = Modifier.focusRequester(focus),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = { picking = true }) { Text(formatDate(date, today)) }
                    Spacer(Modifier.width(4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount > 0,
                onClick = {
                    onSave(date, amount!!)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Adds a new band row. Old rows stay, so history is never rewritten. */
@Composable
fun ChangeBandDialog(
    period: BandPeriod,
    initialLower: Double?,
    initialUpper: Double?,
    startDate: LocalDate,
    today: LocalDate,
    onSave: (LocalDate, Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var lower by rememberSaveable { mutableStateOf(initialLower?.let { formatAmount(it) } ?: "") }
    var upper by rememberSaveable { mutableStateOf(initialUpper?.let { formatAmount(it) } ?: "") }
    var date by rememberSaveable { mutableStateOf(today) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val error = validateBandBounds(period, lower, upper)
    val focus = rememberAutoFocus()

    if (picking) {
        DatePickerModal(
            initial = date,
            min = startDate,
            max = null,
            onPick = { date = it },
            onDismiss = { picking = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change band") },
        text = {
            Column {
                Row {
                    OutlinedTextField(
                        value = lower,
                        onValueChange = { lower = it },
                        singleLine = true,
                        label = { Text("Lower") },
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = upper,
                        onValueChange = { upper = it },
                        singleLine = true,
                        label = { Text("Upper") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                Text(
                    if (period == BandPeriod.WEEKLY) "active days per week" else "per day",
                    color = Grey1,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text("Effective from", color = Grey1, style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { picking = true }) { Text(formatDate(date, today)) }
                Text("Earlier days keep their old band.", color = Grey1, style = MaterialTheme.typography.bodySmall)
                if (error != null && (lower.isNotBlank() || upper.isNotBlank())) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Amber)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = {
                    onSave(date, parseAmount(lower)!!, parseAmount(upper)!!)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


/** Back link plus a screen title. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Column {
        Row { TextButton(onClick = onBack) { Text("‹ Back") } }
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

/** 16 dark-friendly colors plus a hex field. */
@Composable
fun ColorPicker(color: Int, onColor: (Int) -> Unit) {
    var hex by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ColorPalette.forEach { c ->
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (c == color) 3.dp else 0.dp, if (c == color) Color.White else Color.Transparent, CircleShape)
                        .clickable { onColor(c) },
                )
            }
        }
        OutlinedTextField(
            value = hex,
            onValueChange = {
                hex = it
                parseHexColor(it)?.let(onColor)
            },
            singleLine = true,
            label = { Text("Or hex, e.g. #FF8800") },
            isError = hex.isNotBlank() && parseHexColor(hex) == null,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Color(color)))
            Spacer(Modifier.width(8.dp))
            Text("Selected color", color = Grey1)
        }
    }
}

/** Create or rename/recolor a group. */
@Composable
fun GroupDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableStateOf(initialColor) }
    val focus = rememberAutoFocus()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Group name") }, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(name, color)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
