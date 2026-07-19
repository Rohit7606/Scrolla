package com.scrolla.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceHealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ServiceHealthState)

    @Query("SELECT * FROM service_health WHERE id = 1 LIMIT 1")
    suspend fun getOnce(): ServiceHealthState?   // ADDED

    @Query("SELECT * FROM service_health WHERE id = 1")
    fun observe(): Flow<ServiceHealthState?>   // unchanged
}