package com.scrolla.ui.screens

import com.scrolla.firestore.RecordEligibility
import com.scrolla.room.DailyTotal
import com.scrolla.room.ScrollRepository
import java.time.LocalDate

/**
 * The user's lowest day, judged by the same rules as the group record.
 *
 * **Why this exists.** `ScrollRepository.getPersonalBestDay()` returns the raw
 * minimum: the lowest completed day with any scrolling on it, and nothing more.
 * Audit A3 fixed it excluding today, which was the loudest symptom, and stopped
 * there — so it still had no idea whether a day was *trackable*, only whether it
 * was *low*.
 *
 * On 2026-09-07 that put **22 m** on the Profile screen, from 2026-09-04: a day
 * the service spent dead until 17:47 and recorded across two hours. The Hall of
 * Fame showed 22 m too, for an unrelated reason — the group record had already
 * been taken by that same day before the eligibility gate existed. Two screens,
 * two different code paths, one bad day, and no way for the user to tell that
 * their "best ever" was a tracker outage.
 *
 * The group record had solved this. `RecordEligibility` fails closed on today,
 * on empty days, on days whose recording spans too little of the clock, and on
 * days whose last scroll came too early. The personal record inherited none of
 * it — the same divergence the audit called out in A3, and the same shape as
 * Weekly Recap inheriting a window bug Personal Records had already fixed
 * (P2.9).
 *
 * So both now go through one function. Not because sharing code is tidy, but
 * because "your lowest day" and "the group's lowest day" answering differently
 * about the same history is a bug the user can see, and keeping two
 * implementations honest by discipline has already failed twice here.
 */
object PersonalBest {

    /**
     * A year, which is past anything Scrolla has ever held, so in practice this
     * is "everything" while keeping the query bounded. Matches the CSV export.
     */
    const val LOOKBACK_DAYS = 365

    /**
     * The lowest day that is actually allowed to count, or null if none qualify.
     *
     * Null is a real answer and callers must render it as one: on a fresh
     * install, or after a run of outages, there may genuinely be no day worth
     * calling a record. Every call site already handles null, because A3 made
     * that possible before this did.
     */
    suspend fun of(
        repository: ScrollRepository,
        today: LocalDate = LocalDate.now()
    ): DailyTotal? {
        val history = repository.getRecentDailyTotals(LOOKBACK_DAYS)
        if (history.isEmpty()) return null

        // Fetched up front because bestEligibleDay is pure and cannot suspend.
        val spans = history.associate { it.day to repository.getActiveHourSpan(it.day) }

        return RecordEligibility.bestEligibleDay(
            totals = history,
            today = today,
            activeSpanFor = { day -> spans[day] ?: 0 }
        )
    }
}
