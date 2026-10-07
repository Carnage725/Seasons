package com.seasons.app.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 12 not 12.00, 0.5 not 0.50. At most 2 decimals. */
fun formatAmount(value: Double): String =
    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Accepts "5", "0.5" and "0,5". Null if it is not a finite number. */
fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

fun formatDate(date: LocalDate, today: LocalDate): String = when {
    date == today -> "Today"
    date == today.minusDays(1) -> "Yesterday"
    date.year == today.year -> date.format(DateTimeFormatter.ofPattern("MMM d"))
    else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}
