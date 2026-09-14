package com.scrolla.widget

import com.scrolla.room.DailyTotal
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [WidgetSnapshot.weekStats] is the only arithmetic the large widget does, and
 * a home screen is the worst place to discover it is wrong — nobody is there to
 * question a number on a wallpaper. So it is asserted on here rather than read
 * off a device.
 */
class WidgetSnapshotWeekStatsTest {

    private fun day(date: String, km: Float) =
        DailyTotal(day = date, totalCm = km * 100_000f, totalKm = km, lastUpdated = 0L)

    private fun snapshotOf(week: List<DailyTotal>) = WidgetSnapshot(
        todayKm = 0f,
        landmark = null,
        standing = null,
        week = week,
        topApps = emptyList(),
        board = emptyList()
    )

    @Test
    fun `sums the week`() {
        val stats = snapshotOf(
            listOf(
                day("2026-09-08", 1f),
                day("2026-09-09", 2f),
                day("2026-09-10", 3f)
            )
        ).weekStats()

        assertEquals(6f, stats.totalKm, 0.0001f)
    }

    @Test
    fun `average divides by seven, not by the days that have rows`() {
        // Three days of 7 km each, four days with no row at all. The average of
        // the week is 3 km/day. The average of the days that happen to have a
        // row is 7 — more than twice as high, and the number a naive
        // `week.map { it.totalKm }.average()` would put on the home screen.
        val stats = snapshotOf(
            listOf(
                day("2026-09-08", 7f),
                day("2026-09-09", 7f),
                day("2026-09-10", 7f)
            )
        ).weekStats()

        assertEquals(3f, stats.averageKm, 0.0001f)
    }

    @Test
    fun `best day is the largest, not the last`() {
        val stats = snapshotOf(
            listOf(
                day("2026-09-08", 2f),
                day("2026-09-09", 9f),
                day("2026-09-10", 4f)
            )
        ).weekStats()

        assertEquals(9f, stats.bestKm, 0.0001f)
    }

    @Test
    fun `an empty week is zeroes, not a crash`() {
        // The state of a fresh install, and the one the widget renders first.
        val stats = snapshotOf(emptyList()).weekStats()

        assertEquals(0f, stats.totalKm, 0.0001f)
        assertEquals(0f, stats.averageKm, 0.0001f)
        assertEquals(0f, stats.bestKm, 0.0001f)
    }
}
