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