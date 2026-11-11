package com.seasons.app

import com.seasons.app.ui.moveItem
import com.seasons.app.ui.nextSortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderLogicTest {
    @Test fun moveItem_down() = assertEquals(listOf(2L, 3L, 1L, 4L), moveItem(listOf(1L, 2L, 3L, 4L), 0, 2))

    @Test fun moveItem_up() = assertEquals(listOf(1L, 4L, 2L, 3L), moveItem(listOf(1L, 2L, 3L, 4L), 3, 1))

    @Test fun moveItem_toTheEnds() {
        assertEquals(listOf(4L, 1L, 2L, 3L), moveItem(listOf(1L, 2L, 3L, 4L), 3, 0))
        assertEquals(listOf(2L, 3L, 4L, 1L), moveItem(listOf(1L, 2L, 3L, 4L), 0, 3))
    }

    @Test fun moveItem_samePositionOrOutOfRange_changesNothing() {
        val list = listOf(1L, 2L, 3L)
        assertEquals(list, moveItem(list, 1, 1))
        assertEquals(list, moveItem(list, -1, 1))
        assertEquals(list, moveItem(list, 0, 3))
        assertEquals(emptyList<Long>(), moveItem(emptyList<Long>(), 0, 0))
    }

    @Test fun moveItem_doesNotChangeTheOriginalList() {
        val list = listOf(1L, 2L, 3L)
        moveItem(list, 0, 2)
        assertEquals(listOf(1L, 2L, 3L), list)
    }

    @Test fun nextSortOrder_isOneAfterTheHighest_orZeroWhenEmpty() {
        assertEquals(0, nextSortOrder(emptyList()))
        assertEquals(1, nextSortOrder(listOf(0)))
        assertEquals(6, nextSortOrder(listOf(3, 5, 0)))
        assertEquals(1, nextSortOrder(listOf(0, 0, 0))) // old trackers that all had 0
    }
}
