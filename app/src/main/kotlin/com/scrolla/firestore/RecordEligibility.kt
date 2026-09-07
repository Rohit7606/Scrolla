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
    /**
     * The minimum hours a day's recording must **span**, first scroll to last.
     *
     * Span, not count, and the distinction is what makes the rule fair. Counting
     * active hours measures how much the *user* scrolled; span measures how long
     * the *tracker was alive*. Someone who scrolls at 08:00 and again at 22:00
     * has two active hours and a fourteen-hour span, and they are precisely the
     * person a reverse leaderboard exists to reward — a count threshold would
     * disqualify them for being light, which inverts the product.
     *
     * Ten, because it is comfortably longer than any burst the tracker has
     * produced after a restart, and comfortably shorter than a real day. Both
     * bad records this rule was written for fall well outside it:
     *
     * - 2026-09-04 recorded 17:00-18:59 — **span 2**, 21.5 m. It took the group
     *   record from 104 m, and `isRecordImprovement()` meant no client could
     *   ever raise it back.
     * - 2026-09-05 recorded 12:00-17:59 — **span 6**, 68.4 m, the whole morning
     *   dead. It cleared an earlier six-hour *count* threshold exactly, which is
     *   how counting was found wanting.
     */
    const val MIN_ACTIVE_SPAN_HOURS = 10

    /**
     * @param activeSpanFor hours from the day's first recorded scroll to its last.
     *   Defaults to a value that always passes, so existing callers and tests
     *   keep the pre-2026-09-06 behaviour rather than silently tightening.
     */
    /**
     * @param notBefore the earliest day that may count, normally the date the
     *   user joined this group. Null means no lower bound.
     *
     *   A group record is something earned **inside** the group. Without this
     *   bound, joining a group hands it your best day from any point in your
     *   own history — on 2026-09-07 a group created that afternoon was given a
     *   record from 25 August, two weeks before it existed and before anyone in
     *   it could have competed. On a leaderboard where lowest wins, that sets a
     *   bar nobody else was ever in the room for, and `isRecordImprovement()`
     *   makes it permanent.
     */
    fun bestEligibleDay(
        totals: List<DailyTotal>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        activeSpanFor: (String) -> Int = { MIN_ACTIVE_SPAN_HOURS },
        notBefore: LocalDate? = null
    ): DailyTotal? = totals
        .filter { isEligible(it, today, zone, activeSpanFor(it.day), notBefore) }
        .minByOrNull { it.totalKm }

    /** Visible for testing and for the KDoc above to be checkable. */
    fun isEligible(
        total: DailyTotal,
        today: LocalDate,
        zone: ZoneId,
        activeSpanHours: Int = MIN_ACTIVE_SPAN_HOURS,
        notBefore: LocalDate? = null
    ): Boolean {
        val day = runCatching { LocalDate.parse(total.day) }.getOrNull() ?: return false

        // Days from before the user joined this group are their own history,
        // not the group's. See notBefore.
        if (notBefore != null && day.isBefore(notBefore)) return false

        // A day still in progress always wins on a minimum, because it has
        // barely started. Today is never eligible.
        if (!day.isBefore(today)) return false

        // A day with nothing recorded is an absence, not an achievement.
        if (total.totalKm <= 0f) return false

        // A day the tracker only saw a slice of is not a low day, it is a
        // missing day. Keeps out 2026-09-04 (17:00-18:59, span 2) and
        // 2026-09-05 (12:00-17:59, span 6, morning dead).
        if (activeSpanHours < MIN_ACTIVE_SPAN_HOURS) return false

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
