package com.seasons.app.data

import java.time.LocalDate

/** Settings for "Start fresh": same tracker, new id, starts [today]. A past deadline is dropped. */
fun freshCopy(old: Tracker, today: LocalDate): Tracker = Tracker(
    name = old.name,
    unit = old.unit,
    type = old.type,
    color = old.color,
    target = old.target,
    deadline = old.deadline?.takeIf { !it.isBefore(today) },
    bandPeriod = old.bandPeriod,
    startDate = today,
    status = TrackerStatus.ACTIVE,
    completedDate = null,
    sortOrder = old.sortOrder,
)
