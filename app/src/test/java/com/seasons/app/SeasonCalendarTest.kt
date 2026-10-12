package com.seasons.app

import com.seasons.app.ui.buildSeasonCalendar
import com.seasons.app.ui.currentSeasonWindow
import com.seasons.app.ui.seasonProgressText
import com.seasons.app.ui.weeks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class SeasonCalendarTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    // ---------- the current season ----------

    @Test fun window_firstSeason() {
        val w = currentSeasonWindow(d(2026, 9, 12), 77, d(2026, 10, 12), numberBase = 0)
        assertEquals(1, w.number)
        assertEquals(d(2026, 9, 12), w.start)
        assertEquals(d(2026, 11, 27), w.end) // 77 days, both ends counted
        assertEquals(77, w.length)
    }

    @Test fun window_laterSeason_movesForwardByWholeSeasons() {
        val w = currentSeasonWindow(d(2026, 1, 1), 30, d(2026, 3, 5), numberBase = 0)
        assertEquals(3, w.number)
        assertEquals(d(2026, 3, 2), w.start)
        assertEquals(d(2026, 3, 31), w.end)
    }

    @Test fun window_lastAndFirstDayBelongToTheRightSeason() {
        val start = d(2026, 1, 1)
        assertEquals(1, currentSeasonWindow(start, 30, d(2026, 1, 30), 0).number)
        assertEquals(2, currentSeasonWindow(start, 30, d(2026, 1, 31), 0).number)
    }

    @Test fun window_numberBaseCarriesOnAfterALengthChange() {
        val w = currentSeasonWindow(d(2026, 10, 1), 7, d(2026, 10, 3), numberBase = 3)
        assertEquals(4, w.number)
    }

    @Test fun window_beforeTheFirstSeasonStarts_isThatFirstSeason() {
        val w = currentSeasonWindow(d(2026, 11, 1), 77, d(2026, 10, 12), numberBase = 0)
        assertEquals(1, w.number)
        assertEquals(d(2026, 11, 1), w.start)
    }

    // ---------- the calendar ----------

    @Test fun calendar_startsOnTheStartDay_endsOnTheEndDay() {
        val c = buildSeasonCalendar(d(2026, 9, 12), d(2026, 11, 27))
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), c.map { it.month })
        assertEquals(d(2026, 9, 12), c.first().days.first())
        assertEquals(d(2026, 9, 30), c.first().days.last())
        assertEquals(d(2026, 10, 1), c[1].days.first())
        assertEquals(d(2026, 10, 31), c[1].days.last())
        assertEquals(d(2026, 11, 1), c.last().days.first())
        assertEquals(d(2026, 11, 27), c.last().days.last())
    }

    @Test fun calendar_everyDayOnce_forSeveralLengthsAndStarts() {
        for (len in listOf(7, 30, 77, 90, 365)) {
            for (start in listOf(d(2026, 1, 1), d(2026, 2, 28), d(2026, 9, 12), d(2026, 12, 31), d(2028, 2, 29))) {
                val end = start.plusDays(len - 1L)
                val all = buildSeasonCalendar(start, end).flatMap { it.days }
                assertEquals("length $len from $start", len, all.size)
                assertEquals(start, all.first())
                assertEquals(end, all.last())
                assertEquals(all.sorted(), all) // in order
                assertEquals(all.toSet().size, all.size) // no repeats
            }
        }
    }

    @Test fun calendar_oneBlockPerMonth_acrossAYearEnd() {
        val c = buildSeasonCalendar(d(2026, 12, 20), d(2027, 1, 10))
        assertEquals(listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 1)), c.map { it.month })
    }

    @Test fun calendar_sevenDaySeasonInsideOneMonth_isOneBlock() {
        val c = buildSeasonCalendar(d(2026, 10, 5), d(2026, 10, 11))
        assertEquals(1, c.size)
        assertEquals(7, c[0].days.size)
    }

    @Test fun calendar_leapDay() {
        val c = buildSeasonCalendar(d(2028, 2, 20), d(2028, 3, 5))
        assertEquals(10, c[0].days.size) // Feb 20..29
        assertEquals(d(2028, 2, 29), c[0].days.last())
    }

    @Test fun calendar_oneDaySeasonIsAllowedByTheBuilder() {
        val c = buildSeasonCalendar(d(2026, 10, 5), d(2026, 10, 5))
        assertEquals(1, c.single().days.size)
    }

    // ---------- Monday is the first day of the week ----------

    @Test fun leadingBlanks_mondayIsZero_sundayIsSix() {
        assertEquals(DayOfWeek.MONDAY, d(2026, 10, 12).dayOfWeek)
        assertEquals(0, buildSeasonCalendar(d(2026, 10, 12), d(2026, 10, 20))[0].leadingBlanks)
        assertEquals(DayOfWeek.SUNDAY, d(2026, 10, 11).dayOfWeek)
        assertEquals(6, buildSeasonCalendar(d(2026, 10, 11), d(2026, 10, 20))[0].leadingBlanks)
        assertEquals(DayOfWeek.SATURDAY, d(2026, 9, 12).dayOfWeek)
        assertEquals(5, buildSeasonCalendar(d(2026, 9, 12), d(2026, 9, 20))[0].leadingBlanks)
    }

    @Test fun weeks_everyRowHasSevenCells_andEveryDayIsUnderItsWeekday() {
        for (start in listOf(d(2026, 9, 12), d(2026, 10, 1), d(2026, 10, 12), d(2026, 11, 29))) {
            buildSeasonCalendar(start, start.plusDays(76)).forEach { block ->
                val rows = block.weeks()
                rows.forEach { assertEquals(7, it.size) }
                assertEquals(block.days, rows.flatten().filterNotNull())
                rows.forEach { row ->
                    row.forEachIndexed { col, date -> if (date != null) assertEquals(col + 1, date.dayOfWeek.value) }
                }
            }
        }
    }

    @Test fun weeks_blanksOnlyAtTheEnds_ofTheFirstAndLastRow() {
        val block = buildSeasonCalendar(d(2026, 9, 12), d(2026, 9, 30))[0] // Sat 12th .. Wed 30th
        val rows = block.weeks()
        assertEquals(List(5) { null } + listOf(d(2026, 9, 12), d(2026, 9, 13)), rows.first())
        assertEquals(listOf(d(2026, 9, 28), d(2026, 9, 29), d(2026, 9, 30), null, null, null, null), rows.last())
        assertEquals(4, rows.size)
        rows.drop(1).dropLast(1).forEach { row -> assertEquals(7, row.count { it != null }) }
        assertNull(rows.first()[0])
    }

    @Test fun weeks_aMondayStartingFullWeek_hasNoBlanks() {
        val rows = buildSeasonCalendar(d(2026, 10, 5), d(2026, 10, 11))[0].weeks()
        assertEquals(1, rows.size)
        assertEquals(7, rows[0].count { it != null })
    }

    // ---------- progress text ----------

    @Test fun progress_midSeason() {
        val w = currentSeasonWindow(d(2026, 9, 12), 77, d(2026, 10, 12), 0)
        assertEquals("Day 31 of 77 · 46 days left", seasonProgressText(w, d(2026, 10, 12)))
    }

    @Test fun progress_firstDayLastDayAndOneLeft() {
        val w = currentSeasonWindow(d(2026, 9, 12), 77, d(2026, 9, 12), 0)
        assertEquals("Day 1 of 77 · 76 days left", seasonProgressText(w, d(2026, 9, 12)))
        assertEquals("Day 76 of 77 · 1 day left", seasonProgressText(w, d(2026, 11, 26)))
        assertEquals("Day 77 of 77 · last day", seasonProgressText(w, d(2026, 11, 27)))
    }

    @Test fun progress_beforeStartAndAfterEnd() {
        val w = currentSeasonWindow(d(2026, 11, 1), 77, d(2026, 10, 12), 0)
        assertEquals("Starts in 20 days", seasonProgressText(w, d(2026, 10, 12)))
        assertEquals("Starts in 1 day", seasonProgressText(w, d(2026, 10, 31)))
        assertEquals("Ended", seasonProgressText(w, d(2027, 1, 20)))
    }
}
