package com.seasons.app.ui

// Drag-and-drop ordering for the Home list. Pure, so it is unit-tested.

/** A copy of [list] with the item at [from] moved to position [to]. Out-of-range or equal positions change nothing. */
fun <T> moveItem(list: List<T>, from: Int, to: Int): List<T> {
    if (from == to || from !in list.indices || to !in list.indices) return list
    val result = list.toMutableList()
    result.add(to, result.removeAt(from))
    return result
}

/** The sort order for a tracker created now: one after the highest in use, so it joins the end of the list. */
fun nextSortOrder(existing: List<Int>): Int = (existing.maxOrNull() ?: -1) + 1
