package com.scrolla.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailyTotalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dailyTotal: DailyTotal)

    @Query("SELECT * FROM daily_totals WHERE day = :day")
    suspend fun getForDay(day: String): DailyTotal?

    @Query("SELECT * FROM daily_totals ORDER BY day DESC LIMIT :days")
    suspend fun getRecentDays(days: Int): List<DailyTotal>

    /**
     * The lowest completed day on record, or null until there is one.
     *
     * A3 (AUDIT_A_TRACK.md): both of these used to read the whole table. A row
     * for today appears on the first flush of the morning, and a few minutes of
     * scrolling is always the lowest number in the table — so from breakfast
     * onwards the app announced a new personal record every single day. Checked
     * on the device 2026-09-05: this returned today at 4.06 m over a history
     * whose real minimum was 21.55 m.
     *
     * `day < :today` excludes the day still in progress. `totalKm > 0` excludes
     * days with nothing recorded, which are an absence rather than an
     * achievement — the same reasoning as
     * [com.scrolla.firestore.RecordEligibility], which already fails closed on
     * both for the *group* record.
     *
     * Not yet aligned with that helper's 18:00 last-scroll rule; see A3 in the
     * audit for why that needs a decision rather than a patch.
     */
    @Query("SELECT MIN(totalKm) FROM daily_totals WHERE day < :today AND totalKm > 0")
    suspend fun getPersonalBest(today: String): Float?

    /** The full row behind [getPersonalBest], for screens that show the date. */
    @Query("SELECT * FROM daily_totals WHERE day < :today AND totalKm > 0 ORDER BY totalKm ASC LIMIT 1")
    suspend fun getPersonalBestDay(today: String): DailyTotal?
}