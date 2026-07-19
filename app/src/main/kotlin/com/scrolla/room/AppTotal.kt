package com.scrolla.room

import androidx.room.Entity

@Entity(
    tableName = "app_totals",
    primaryKeys = ["day", "appPackage"]
)
data class AppTotal(
    val day: String,            // "2025-01-15"
    val appPackage: String,     // "com.instagram.android"
    val totalCm: Float          // accumulated cm for this app on this day
)