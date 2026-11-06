package com.seasons.app

import com.seasons.app.data.LogEntry
import com.seasons.app.ui.dayHeader
import com.seasons.app.ui.groupHistory
import com.seasons.app.ui.monthSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HistoryGroupsTest {
    private val today = LocalDate.of(2026, 11, 5)
    private fun log(id: Long, date: LocalDate, amount: Double, createdAt: Long = id) = LogEntry(id, 1, date, amount, createdAt)

    @Test fun emptyHistory_hasNoGroups() = assertEquals(0, groupHistory(emptyList()).size)

    @Test fun groupsByMonth_newestFirst_withTotalsAndDayCounts() {
        val logs = listOf(
            log(1, LocalDate.of(2026, 9, 30), 4.0),
            log(2, LocalDate.of(2026, 11, 5), 12.0),
            log(3, LocalDate.of(2026, 11, 5), 5.0),
            log(4, LocalDate.of(2026, 11, 1), 2.5),
            log(5, LocalDate.of(2026, 10, 15), 7.0),
        )
        val g = groupHistory(logs)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2026, 9)), g.map { it.month })
        assertEquals("November 2026", g[0].title)
        assertEquals(19.5, g[0].total, 0.0)
        assertEquals(2, g[0].dayCount)
        assertEquals("19.5 pages · 2 days", monthSummary(g[0], "pages"))
        assertEquals("4 pages · 1 day", monthSummary(g[2], "pages"))
    }

    @Test fun daysNewestFirst_entriesNewestFirst_dayTotalIsTheSum() {
        val logs = listOf(
            log(1, LocalDate.of(2026, 11, 5), 12.0, createdAt = 100),
            log(2, LocalDate.of(2026, 11, 5), 5.0, createdAt = 200),
            log(3, LocalDate.of(2026, 11, 1), 2.0),
        )
        val days = groupHistory(logs)[0].days
        assertEquals(listOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1)), days.map { it.date })
        assertEquals(17.0, days[0].total, 0.0)
        assertEquals(listOf(2L, 1L), days[0].entries.map { it.id })
    }

    @Test fun sameYearMonthInDifferentYears_areSeparateGroups() {
        val g = groupHistory(listOf(log(1, LocalDate.of(2026, 11, 5), 1.0), log(2, LocalDate.of(2025, 11, 5), 1.0)))
        assertEquals(listOf("2026-11", "2025-11"), g.map { it.key })
    }

    @Test fun everyEntryAppearsOnce() {
        val logs = (1L..40L).map { log(it, today.minusDays(it * 3), 1.0) }
        assertEquals(logs.map { it.id }.toSet(), groupHistory(logs).flatMap { it.days }.flatMap { it.entries }.map { it.id }.toSet())
        assertEquals(40, groupHistory(logs).sumOf { m -> m.days.sumOf { it.entries.size } })
    }

    @Test fun dayHeader_usesWordsForTodayAndYesterday() {
        assertEquals("Today", dayHeader(today, today))
        assertEquals("Yesterday", dayHeader(today.minusDays(1), today))
        assertEquals("Mon Nov 2", dayHeader(LocalDate.of(2026, 11, 2), today))
    }
}
