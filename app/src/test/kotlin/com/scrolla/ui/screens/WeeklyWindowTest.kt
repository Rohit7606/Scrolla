package com.scrolla.ui.screens

import com.scrolla.room.DailyTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pins the fix for the recap's thirteen-day "week" (PREMIUM_CHECKLIST P2.9).
 *
 * The bug was not in the arithmetic — the sum was correct for the rows it was
 * given. It was in believing that seven rows are seven days.
 */
class WeeklyWindowTest {

    private val today = LocalDate.of(2026, 9, 4)

    private fun row(day: String, km: Float) =
        DailyTotal(day = day, totalCm = km * 100_000f, totalKm = km, lastUpdated = 0L)

    /**
     * The exact rows pulled off the Xiaomi on 2026-09-04, which is what put this
     * bug on screen: five days from late August plus one row for today. Summed
     * unfiltered they read 595 m; only 1 m of it happened this week.
     */
    private val deviceRows = listOf(
        row("2026-09-04", 0.0010174f),
        row("2026-08-27", 0.0263288f),
        row("2026-08-26", 0.205179f),
        row("2026-08-25", 0.1040641f),
        row("2026-08-24", 0.1062977f),
        row("2026-08-23", 0.1516044f)
    )

    @Test
    fun `the device rows that produced 595m keep only today`() {
        val kept = WeeklyWindow.rowsWithin(deviceRows, today)
        assertEquals(listOf("2026-09-04"), kept.map { it.day })
    }

    @Test
    fun `total is one metre, not five hundred and ninety five`() {
        val km = WeeklyWindow.totalKmWithin(deviceRows, today)
        assertEquals(0.0010174f, km, 1e-7f)
        assertTrue("must not resemble the 0.5945 km the screen showed", km < 0.01f)
    }

    @Test
    fun `window includes today`() {
        val kept = WeeklyWindow.rowsWithin(listOf(row("2026-09-04", 1f)), today)
        assertEquals(1, kept.size)
    }

    @Test
    fun `window includes the sixth day back`() {
        val kept = WeeklyWindow.rowsWithin(listOf(row("2026-08-29", 1f)), today)
        assertEquals(1, kept.size)
    }

    @Test
    fun `window excludes the seventh day back`() {
        val kept = WeeklyWindow.rowsWithin(listOf(row("2026-08-28", 1f)), today)
        assertTrue(kept.isEmpty())
    }

    @Test
    fun `a future row is excluded rather than counted`() {
        val kept = WeeklyWindow.rowsWithin(listOf(row("2026-09-05", 1f)), today)
        assertTrue(kept.isEmpty())
    }

    @Test
    fun `an unparseable day is dropped, not guessed at`() {
        val kept = WeeklyWindow.rowsWithin(listOf(row("not-a-date", 9f)), today)
        assertTrue(kept.isEmpty())
    }

    @Test
    fun `seven consecutive days are all kept`() {
        val rows = (0..6).map { row(today.minusDays(it.toLong()).toString(), 0.1f) }
        assertEquals(7, WeeklyWindow.rowsWithin(rows, today).size)
        assertEquals(0.7f, WeeklyWindow.totalKmWithin(rows, today), 1e-5f)
    }

    @Test
    fun `empty input gives an empty window, so the screen shows its empty state`() {
        assertTrue(WeeklyWindow.rowsWithin(emptyList(), today).isEmpty())
        assertEquals(0f, WeeklyWindow.totalKmWithin(emptyList(), today), 0f)
    }

    @Test
    fun `a gap week collapses to nothing rather than reaching backwards`() {
        // Every row older than the window: the screen must not borrow them.
        val rows = listOf(row("2026-08-01", 5f), row("2026-07-30", 5f))
        assertTrue(WeeklyWindow.rowsWithin(rows, today).isEmpty())
        assertEquals(0f, WeeklyWindow.totalKmWithin(rows, today), 0f)
    }
}
