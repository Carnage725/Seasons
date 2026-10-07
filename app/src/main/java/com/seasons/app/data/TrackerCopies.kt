package com.seasons.app.data

import java.time.LocalDate

/** Settings for "Start fresh": same tracker, new id, starts [today]. A past deadline is dropped. */
fun freshCopy(old: Tracker, today: LocalDate): Tracker = Tracker(
    name = old.name,
    unit = old.unit,
    type = old.type,
    color = old.color,
    groupId = old.groupId,
    target = old.target,
    deadline = old.deadline?.takeIf { !it.isBefore(today) },
    bandPeriod = old.bandPeriod,
    startDate = today,
    status = TrackerStatus.ACTIVE,
    completedDate = null,
    sortOrder = old.sortOrder,
)

/** New order after moving [id] one place. Always returns every group with sortOrder 0, 1, 2... */
fun swapGroupOrder(groups: List<TrackerGroup>, id: Long, up: Boolean): List<TrackerGroup> {
    val sorted = groups.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    val i = sorted.indexOfFirst { it.id == id }
    val j = if (up) i - 1 else i + 1
    val result = sorted.toMutableList()
    if (i >= 0 && j in sorted.indices) {
        result[i] = sorted[j]
        result[j] = sorted[i]
    }
    return result.mapIndexed { index, g -> g.copy(sortOrder = index) }
}
