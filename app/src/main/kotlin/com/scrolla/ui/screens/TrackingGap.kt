package com.scrolla.ui.screens

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Detects the outage that has no other symptom: the accessibility service is
 * still switched on, but the process was killed and nothing has been recorded
 * since.
 *
 * This is the failure that cost this project three silent outages. The
 * tracking-off banner cannot see it — the switch is on, so by that measure
 * everything is fine — and the user cannot see it either, because a day with no
 * scrolling looks exactly like a good day on a leaderboard where **lower wins**.
 * A dead tracker is indistinguishable from self-control until someone checks the
 * database.
 *
 * ### Why it counts waking hours instead of elapsed hours
 *
 * The naive rule — "no events for six hours" — fires every single morning. Last
 * event 22:30, user asleep, opens the app at 09:00: eleven hours of silence and
 * nothing whatsoever is wrong. Elapsed time cannot distinguish a killed process
 * from a night's sleep, so this measures only the overlap between the silence
 * and the hours a person is plausibly awake and holding their phone.
 *
 * The real outage on 2026-08-25 — service dead 09:34 to 19:00 — is 9.4 waking
 * hours and fires. A normal night is under one.
 *
 * ### The honest limitation
 *
 * Exactly the one [com.scrolla.firestore.RecordEligibility] carries: "the
 * tracker died" and "I genuinely barely touched my phone" are the same shape in
 * this data, because both are an absence. [WAKING_START_HOUR] and
 * [WAKING_END_HOUR] are a proxy for being awake, not a measurement of it, and
 * someone on a night shift or a long flight will be told their tracking broke
 * when it did not.
 *
 * So this fails toward silence: a generous threshold, waking hours only, and it
 * drives an in-app card rather than a notification. A false alarm should cost
 * someone a glance, never an interruption. The real fix is per-day tracking
 * health (PREMIUM_CHECKLIST P2.6e), which needs a new table in `room/`.
 */
object TrackingGap {

    /** Start of the window in which someone is assumed to be awake, local time. */
    const val WAKING_START_HOUR = 9

    /** End of that window. Deliberately not midnight — late scrolling is real,
     *  but its absence is weak evidence of anything. */
    const val WAKING_END_HOUR = 22

    /**
     * How many waking hours of total silence count as an outage.
     *
     * Five, because a person can plausibly not touch their phone for a morning —
     * a lecture, a film, a long drive — and being wrong about that is worse than
     * being late. The 2026-08-25 outage ran 9.4 and would still have been caught
     * with room to spare.
     */
    const val SUSPICIOUS_WAKING_HOURS = 5.0

    /**
     * Waking hours elapsed between two instants, summing each day's overlap with
     * the [WAKING_START_HOUR]–[WAKING_END_HOUR] window.
     */
    fun wakingHoursBetween(from: Instant, to: Instant, zone: ZoneId): Double {
        if (!to.isAfter(from)) return 0.0

        val start = LocalDateTime.ofInstant(from, zone)
        val end = LocalDateTime.ofInstant(to, zone)

        var total = 0.0
        var day: LocalDate = start.toLocalDate()

        // Bounded so a corrupt or absent timestamp cannot spin here. Anything
        // past the cap is already far beyond the threshold.
        var guard = 0
        while (!day.isAfter(end.toLocalDate()) && guard < MAX_DAYS_SCANNED) {
            val wakeStart = LocalDateTime.of(day, LocalTime.of(WAKING_START_HOUR, 0))
            val wakeEnd = LocalDateTime.of(day, LocalTime.of(WAKING_END_HOUR, 0))

            val overlapStart = maxOf(wakeStart, start)
            val overlapEnd = minOf(wakeEnd, end)

            if (overlapEnd.isAfter(overlapStart)) {
                total += Duration.between(overlapStart, overlapEnd).toMinutes() / 60.0
            }

            day = day.plusDays(1)
            guard++
        }
        return total
    }

    /**
     * True when tracking looks dead despite being switched on.
     *
     * @param isAccessibilityEnabled asked of the system, not of Room — a cached
     *   value here would report a gap for a service the user simply turned off,
     *   which is the other banner's job and a different message.
     * @param lastEventTimestamp from `ServiceHealthState`. Zero means nothing has
     *   ever been recorded, which is a first run rather than an outage.
     */
    fun isSuspicious(
        isAccessibilityEnabled: Boolean,
        lastEventTimestamp: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (!isAccessibilityEnabled) return false
        if (lastEventTimestamp <= 0L) return false
        // A timestamp in the future is a corrupt row or a clock change, not an
        // outage. Fail closed rather than accuse.
        if (lastEventTimestamp > now) return false

        return wakingHoursSince(lastEventTimestamp, now, zone) >= SUSPICIOUS_WAKING_HOURS
    }

    /** Visible for the UI, which reports the size of the gap it found. */
    fun wakingHoursSince(lastEventTimestamp: Long, now: Long, zone: ZoneId): Double =
        wakingHoursBetween(
            Instant.ofEpochMilli(lastEventTimestamp),
            Instant.ofEpochMilli(now),
            zone
        )

    private const val MAX_DAYS_SCANNED = 400
}
