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

    @Query("SELECT MIN(totalKm) FROM daily_totals")
    suspend fun getPersonalBest(): Float?

    @Query("SELECT * FROM daily_totals ORDER BY totalKm ASC LIMIT 1")
    suspend fun getPersonalBestDay(): DailyTotal?
}