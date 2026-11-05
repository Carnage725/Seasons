package com.seasons.app.ui

import com.seasons.app.data.BandPeriod
import com.seasons.app.data.TrackerType
import java.time.LocalDate

val ColorPalette: List<Int> = listOf(
    0xFFFF5252, 0xFFFF7043, 0xFFFFA726, 0xFFFFCA28,
    0xFFFFEE58, 0xFFD4E157, 0xFF9CCC65, 0xFF66BB6A,
    0xFF26A69A, 0xFF26C6DA, 0xFF29B6F6, 0xFF42A5F5,
    0xFF7E57C2, 0xFFAB47BC, 0xFFEC407A, 0xFFBCAAA4,
).map { it.toInt() }

/** "#RRGGBB" or "RRGGBB" to opaque ARGB. Null if invalid. */
fun parseHexColor(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6) return null
    val rgb = hex.toIntOrNull(16) ?: return null
    return (0xFF000000L or rgb.toLong()).toInt()
}

fun allowedBandPeriods(type: TrackerType): List<BandPeriod> = when (type) {
    TrackerType.GOAL -> listOf(BandPeriod.NONE, BandPeriod.DAILY)
    TrackerType.ONGOING -> listOf(BandPeriod.DAILY, BandPeriod.WEEKLY)
}

data class TrackerFormInput(
    val name: String,
    val unit: String,
    val type: TrackerType,
    val target: String,
    val deadline: LocalDate?,
    val bandPeriod: BandPeriod,
    val lower: String,
    val startDate: LocalDate,
)

/** Returns an error message, or null if the input is fine. When [editing], band and start date are not checked. */
fun validateTracker(input: TrackerFormInput, today: LocalDate, editing: Boolean): String? {
    if (input.name.isBlank()) return "Name is required"
    if (input.unit.isBlank()) return "Unit is required"

    if (input.type == TrackerType.GOAL) {
        val target = parseAmount(input.target)
        if (target == null || target <= 0) return "Target must be more than 0"
        if (input.deadline != null && input.deadline.isBefore(input.startDate)) {
            return "Deadline is before the start date"
        }
    }

    if (editing) return null

    if (input.startDate.isAfter(today)) return "Start date cannot be in the future"
    if (input.bandPeriod !in allowedBandPeriods(input.type)) {
        return "A ${input.type.name.lowercase()} cannot use a ${input.bandPeriod.name.lowercase()} band"
    }
    return validateBandBounds(input.bandPeriod, input.lower)
}

/** Returns an error message, or null. NONE has nothing to check. */
fun validateBandBounds(period: BandPeriod, minimumText: String): String? {
    if (period == BandPeriod.NONE) return null
    val minimum = parseAmount(minimumText) ?: return "Enter a minimum"
    if (minimum < 0) return "Minimum cannot be negative"
    if (period == BandPeriod.WEEKLY && minimum > 7) return "Active days per week cannot be more than 7"
    return null
}
