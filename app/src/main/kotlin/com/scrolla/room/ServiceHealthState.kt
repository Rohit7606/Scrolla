package com.scrolla.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_health")
data class ServiceHealthState(
    @PrimaryKey
    val id: Int = 1,            // always 1, singleton row

    val isServiceRunning: Boolean,
    val lastEventTimestamp: Long,       // timestamp of last scroll event received
    val lastRoomFlushTimestamp: Long,   // timestamp of last successful Room write
    val lastFirestoreSyncTimestamp: Long,
    val degradedReason: String?         // null = healthy, non-null = what went wrong
)