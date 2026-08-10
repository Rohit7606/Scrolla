package com.scrolla.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_totals")
data class DailyTotal(
    @PrimaryKey
    val day: String,            // "2025-01-15" — same format as ScrollEvent.day

    val totalCm: Float,         // sum of all ScrollEvent.scrollCm for this day
    val totalKm: Float,         // totalCm / 100_000f — precomputed for display, not re-derived every read
    val lastUpdated: Long       // timestamp of last recomputation
)