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

    @Query("SELECT SUM(scrollCm) FROM scroll_events WHERE day BETWEEN :startDay AND :endDay")
    suspend fun getTotalCmBetweenDays(startDay: String, endDay: String): Float?

    @Query("DELETE FROM scroll_events WHERE day < :beforeDay")
    suspend fun deleteOlderThan(beforeDay: String)
}