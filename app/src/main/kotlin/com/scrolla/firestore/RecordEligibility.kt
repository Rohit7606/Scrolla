package com.scrolla.firestore

import com.scrolla.room.DailyTotal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which of the user's days may set the group record.
 *
 * The group record is a **minimum** — lowest single day wins — which means
 * every way the tracker can fail produces a winning score. On 2026-08-25 the
 * accessibility service crashed at 09:34 and that day recorded 5.5 m. Had this
 * write existed then, 5.5 m would have become the group record, and
 * `firestore.rules`' `isRecordImprovement()` only permits
 * `recordKm <= resource.data.recordKm` — so it could never have been raised
 * again from any client. A bad record is permanent short of editing Firestore
 * by hand.
 *
 * That asymmetry is the whole design: rejecting a real achievement costs one
 * day, accepting a bogus one costs the feature forever. Every rule here fails
 * closed.
 *
 * **The honest limitation.** "Tracking broke" and "I genuinely barely touched
 * my phone" are the same shape in this data — both are just a small number.
 * No rule can separate them from daily totals alone. [LAST_SCROLL_CUTOFF_HOUR]
 * is a proxy, not a proof, and it will occasionally reject a real low day.
 * That is the direction to be wrong in.
 *
 * A stronger signal would be per-day tracking health, which does not exist —
 * `service_health` is a single current-state row with no history. Adding it
 * means a new table in `room/`, which is A's (PREMIUM_CHECKLIST P2.6e).
 */
object RecordEligibility {

    /**
     * A day only counts if its last recorded scroll happened at or after this
     * hour, local time — or after midnight, which is the same day's scrolling
     * having run past the boundary.
     *
     * Rationale: `DailyTotal.lastUpdated` is written on flush, and a flush only
     * happens when something was scrolled, so it is effectively "time of last
     * scroll". A day whose last scroll was breakfast-time is far more likely to
     * be a tracker that died than a person who genuinely stopped at 09:34 and
     * never picked their phone up again.
     *
     * Chosen over "day spans >= 6 distinct hour buckets", which is a slightly
     * better proxy but is not reachable from B's half: `ScrollRepository`
     * exposes no per-day hour buckets (`getPeakHourForDay` returns only the top
     * one), so it would need a new query in `room/`. If that method ever lands,
     * swapping [isPlausiblyComplete] is the only change required.
     */
    const val LAST_SCROLL_CUTOFF_HOUR = 18

    /**
     * The lowest day that is allowed to set a record, or null if none qualify.
     *
     * [today] is passed in rather than read from the clock so this is testable
     * without a fake clock.
     */
    fun bestEligibleDay(
        totals: List<DailyTotal>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): DailyTotal? = totals
        .filter { isEligible(it, today, zone) }
        .minByOrNull { it.totalKm }

    /** Visible for testing and for the KDoc above to be checkable. */
    fun isEligible(total: DailyTotal, today: LocalDate, zone: ZoneId): Boolean {
        val day = runCatching { LocalDate.parse(total.day) }.getOrNull() ?: return false

        // A day still in progress always wins on a minimum, because it has
        // barely started. Today is never eligible.
        if (!day.isBefore(today)) return false

        // A day with nothing recorded is an absence, not an achievement.
        if (total.totalKm <= 0f) return false

        return isPlausiblyComplete(total, day, zone)
    }

    /**
     * Did tracking still look alive by the end of this day?
     *
     * Swap this one function if a stronger signal becomes available.
     */
    private fun isPlausiblyComplete(total: DailyTotal, day: LocalDate, zone: ZoneId): Boolean {
        if (total.lastUpdated <= 0L) return false
        val lastUpdate = Instant.ofEpochMilli(total.lastUpdated).atZone(zone)

        // Scrolling that ran past midnight lands on the following date, which is
        // the strongest possible evidence the day completed.
        if (lastUpdate.toLocalDate().isAfter(day)) return true

        // Anything before the day it belongs to is a corrupt row.
        if (lastUpdate.toLocalDate().isBefore(day)) return false

        return lastUpdate.hour >= LAST_SCROLL_CUTOFF_HOUR
    }
}
