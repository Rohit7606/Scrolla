package com.scrolla.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scroll_events")
data class ScrollEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val day: String,            // "2025-01-15" — LOCAL date, LocalDate.now().toString(), NEVER UTC
    val appPackage: String,     // e.g. "com.instagram.android"
    val scrollCm: Float,        // accumulated cm for this batch, always positive
    val hourBucket: Int,        // 0–23, hour of day in local time when batch was flushed
    val timestamp: Long         // System.currentTimeMillis() at flush time
)
