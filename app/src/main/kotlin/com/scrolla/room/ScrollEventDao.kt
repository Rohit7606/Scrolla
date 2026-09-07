package com.scrolla.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScrollEventDao {
    @Insert
    suspend fun insert(event: ScrollEvent)

    @Query("SELECT SUM(scrollCm) FROM scroll_events WHERE day = :day")
    suspend fun getTotalCmForDay(day: String): Float?

    @Query("SELECT appPackage, SUM(scrollCm) as totalCm FROM scroll_events WHERE day = :day GROUP BY appPackage ORDER BY totalCm DESC LIMIT 5")
    suspend fun getTopAppsByDay(day: String): List<AppPackageCm>

    @Query("SELECT hourBucket, SUM(scrollCm) as totalCm FROM scroll_events WHERE day = :day GROUP BY hourBucket ORDER BY totalCm DESC LIMIT 1")
    suspend fun getPeakHourForDay(day: String): HourBucketCm?

    /**
     * How many distinct hours of the day recorded any scrolling.
     *
     * The signal `RecordEligibility` was designed around and could not reach —
     * see its KDoc, which names this exact query as the swap it was waiting for.
     * A day is a plausible record candidate only if the tracker was alive across
     * a real span of it, and "how many hours did we hear from" says that far
     * better than "when was the last event", which a two-hour evening burst
     * passes just as easily as a full day.
     *
     * Measured on the device 2026-09-06: 2026-09-04 recorded 17:00–18:59, two
     * distinct hours, and took the group record permanently at 21.5 m.
     */
    @Query("SELECT COUNT(DISTINCT hourBucket) FROM scroll_events WHERE day = :day")
    suspend fun getActiveHourCountForDay(day: String): Int

    /**
     * Hours from the day's first recorded scroll to its last, inclusive.
     *
     * **Span, not count** — and the difference decides whether the rule is fair.
     * Counting active hours measures how much the *user* scrolled; span measures
     * how long the *tracker was alive*. Someone who scrolls at 08:00 and again at
     * 22:00 has two active hours and a fourteen-hour span, and they are exactly
     * the person a reverse leaderboard should reward. A count threshold would
     * disqualify them for being light users, which inverts the entire point of
     * the product.
     *
     * Measured 2026-09-07: 2026-09-05 recorded 12:00–17:59 — six active hours,
     * so it cleared a count of six, but a span of six on a day whose morning the
     * service spent dead. Span sees that; count could not.
     *
     * Null when the day has no rows at all.
     */
    @Query("SELECT MAX(hourBucket) - MIN(hourBucket) + 1 FROM scroll_events WHERE day = :day")
    suspend fun getActiveHourSpanForDay(day: String): Int?

    // A8: `getTotalCmBetweenDays()` was removed — zero callers. Range totals are
    // built from daily_totals (see WeeklyWindow), which is the cheaper source.

    /**
     * Retention. **Nothing calls this yet** — see A4 in AUDIT_A_TRACK.md.
     *
     * Kept rather than deleted precisely because it is the mechanism that would
     * bound this table: `scroll_events` grows ~400 rows/day forever, and it is
     * the most sensitive store in the app. The app tells users "App breakdown
     * stays on this device", which is true, but never says for how long,
     * because today the answer is "always". Wiring this up needs a retention
     * period chosen deliberately, not defaulted.
     */
    @Query("DELETE FROM scroll_events WHERE day < :beforeDay")
    suspend fun deleteOlderThan(beforeDay: String)
}