package com.scrolla.ui.screens

import com.scrolla.room.DailyTotal
import java.time.LocalDate

/**
 * Narrows daily totals to an actual calendar week (PREMIUM_CHECKLIST P2.9).
 *
 * `getRecentDailyTotals(7)` runs `ORDER BY day DESC LIMIT 7`, which is the seven
 * most recent **rows**, not the last seven **days** — and a day with no
 * scrolling has no row at all. On a device with five days of history from
 * 2026-08-23 to 08-27 and one row for 09-04, those seven rows spanned thirteen
 * days, and Weekly Recap summed them into a confident **"595 m this week"** when
 * the real seven-day total was one metre.
 *
 * Personal Records already solved this: its best-week card walks consecutive
 * calendar dates rather than consecutive rows, for exactly this reason. This is
 * the same guard for the recap.
 *
 * Taking seven rows and filtering by date is complete as well as correct: at
 * most seven rows can fall inside a seven-day window, and they are necessarily
 * the newest ones, so nothing inside the window is ever left behind.
 *
 * Pure and clock-injected so it is testable without Android.
 */
object WeeklyWindow {

    /** Days in the window, counting today. */
    const val WINDOW_DAYS = 7

    /**
     * The subset of [totals] that genuinely falls within the [WINDOW_DAYS]-day
     * window ending on [today].
     *
     * Rows whose `day` will not parse are dropped rather than guessed at — the
     * same fail-closed choice `RecordEligibility` makes for corrupt rows, since
     * a row we cannot date is a row we cannot claim happened this week.
     */
    fun rowsWithin(totals: List<DailyTotal>, today: LocalDate): List<DailyTotal> {
        val earliest = today.minusDays((WINDOW_DAYS - 1).toLong())
        return totals.filter { row ->
            val day = runCatching { LocalDate.parse(row.day) }.getOrNull() ?: return@filter false
            !day.isBefore(earliest) && !day.isAfter(today)
        }
    }

    /** Total km across the window. Zero when nothing falls inside it. */
    fun totalKmWithin(totals: List<DailyTotal>, today: LocalDate): Float =
        rowsWithin(totals, today).map { it.totalKm }.sum()
}
