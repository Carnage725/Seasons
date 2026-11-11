@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.seasons.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasons.app.data.BandPeriod
import com.seasons.app.data.TrackerType
import com.seasons.app.domain.formatClock

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onTrophies: () -> Unit,
    onArchive: () -> Unit,
    onSeasons: () -> Unit,
    onSettings: () -> Unit,
    onStopTimer: () -> Unit,
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val needsSetup by vm.needsSetup.collectAsStateWithLifecycle()
    val timerInfo by vm.timer.collectAsStateWithLifecycle()
    val now by rememberNowMillis(timerInfo != null)
    var askStop by rememberSaveable { mutableStateOf(false) }
    val running = timerInfo
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
    if (needsSetup) SetupDialog(onSave = vm::saveSetup)
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNew, containerColor = Grey3, contentColor = TextMain) {
                Text("+", fontSize = 24.sp)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val list = rows
            // Long press a card, then drag it. While dragging (and until the database catches up) the order lives here.
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val haptic = LocalHapticFeedback.current
            val gapPx = with(LocalDensity.current) { 24.dp.toPx() }
            var order by remember { mutableStateOf<List<Long>?>(null) }
            var draggedId by remember { mutableStateOf<Long?>(null) }
            var dragOffset by remember { mutableStateOf(0f) }
            val shown = remember(list, order) {
                val ids = order
                if (list == null || ids == null) list else list.sortedBy { r -> ids.indexOf(r.tracker.id).let { if (it < 0) Int.MAX_VALUE else it } }
            }
            val shownIds by rememberUpdatedState(shown?.map { it.tracker.id }.orEmpty())
            LaunchedEffect(list, draggedId) {
                if (draggedId == null && order != null && list?.map { it.tracker.id } == order) order = null
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                it.key is Long && pos.y >= it.offset && pos.y < it.offset + it.size
                            }
                            if (hit != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggedId = hit.key as Long
                                dragOffset = 0f
                                order = shownIds
                            }
                        },
                        onDrag = { change, amount ->
                            val id = draggedId
                            val ids = order
                            if (id != null && ids != null) {
                                change.consume()
                                dragOffset += amount.y
                                val infos = listState.layoutInfo.visibleItemsInfo
                                val me = infos.firstOrNull { it.key == id }
                                // Only act when the layout has caught up with the last swap (the header is item 0).
                                if (me != null && me.index == ids.indexOf(id) + 1) {
                                    val center = me.offset + dragOffset + me.size / 2f
                                    val target = infos.firstOrNull {
                                        it.key is Long && it.key != id && center >= it.offset && center < it.offset + it.size
                                    }
                                    if (target != null) {
                                        val from = ids.indexOf(id)
                                        val to = ids.indexOf(target.key as Long)
                                        order = moveItem(ids, from, to)
                                        // The card's layout position jumped by the neighbour's height; keep it under the finger.
                                        dragOffset += if (to > from) -(target.size + gapPx) else (target.size + gapPx)
                                    }
                                    // Near the top or bottom edge: scroll, and keep the card under the finger.
                                    val top = me.offset + dragOffset
                                    val bottom = top + me.size
                                    val layout = listState.layoutInfo
                                    val step = when {
                                        top < layout.viewportStartOffset + 80f -> -24f
                                        bottom > layout.viewportEndOffset - 80f -> 24f
                                        else -> 0f
                                    }
                                    if (step != 0f) scope.launch { dragOffset += listState.scrollBy(step) }
                                }
                            }
                        },
                        onDragEnd = {
                            order?.let { vm.saveOrder(it) }
                            draggedId = null
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            draggedId = null
                            dragOffset = 0f
                            order = null
                        },
                    )
                },
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Seasons", style = MaterialTheme.typography.headlineMedium, color = Grey1, modifier = Modifier.weight(1f))
                        var menuOpen by rememberSaveable { mutableStateOf(false) }
                        Box {
                            MoreButton(onClick = { menuOpen = true })
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                listOf(
                                    "Trophies" to onTrophies,
                                    "Archive" to onArchive,
                                    "Seasons" to onSeasons,
                                    "Settings" to onSettings,
                                ).forEach { (label, action) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        menuOpen = false
                                        action()
                                    })
                                }
                            }
                        }
                    }
                }
                if (list != null && list.isEmpty()) {
                    item { Text("No trackers yet.\nTap + to add one.", color = Grey1) }
                }
                items(shown.orEmpty(), key = { it.tracker.id }) { row ->
                    val id = row.tracker.id
                    val dragging = id == draggedId
                    TrackerRow(
                        row,
                        modifier = if (dragging) {
                            Modifier.zIndex(1f).graphicsLayer {
                                translationY = dragOffset
                                alpha = 0.9f
                                scaleX = 1.02f
                                scaleY = 1.02f
                            }
                        } else {
                            Modifier.animateItem()
                        },
                        onClick = { onOpen(id) },
                        timerStart = running?.timer?.takeIf { it.trackerId == id }?.startTime,
                        now = now,
                        onTimer = {
                            when {
                                running == null -> vm.startTimer(id)
                                running.timer.trackerId == id -> onStopTimer()
                                else -> askStop = true
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun TrackerRow(
    row: HomeRow,
    onClick: () -> Unit,
    timerStart: Long?,
    now: Long,
    onTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = row.tracker
    val color = Color(t.color)
    val main = row.mainText()

    // Long press and drag moves the card (handled by the list). Editing a running timer's start is on the tracker screen.
    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            // The name gets the width. Status and streak stack on the right so a large system font cannot squeeze the name.
            Text(t.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(row.statusText(), color = if (row.metToday) color else Grey1, style = MaterialTheme.typography.bodyMedium)
                Text(row.streakText(), color = Grey1, style = MaterialTheme.typography.bodySmall)
            }
            StopwatchButton(running = timerStart != null, color = color, onClick = onTimer)
        }
        if (timerStart != null) {
            Text(
                "Running ${formatClock(now - timerStart)}",
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 20.dp),
            )
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
