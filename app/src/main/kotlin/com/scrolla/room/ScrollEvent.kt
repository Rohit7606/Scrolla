package com.scrolla.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A4 (AUDIT_A_TRACK.md): the `day` index is not premature optimisation. Every
 * read of this table filters on `day` and there was no index, so
 * `EXPLAIN QUERY PLAN` on the device showed `SCAN scroll_events` for the
 * today-total query that runs on **every Home load**, plus `getTopAppsByDay`
 * and `getPeakHourForDay` which scan and group.
 *
 * The table is also unbounded — measured at ~400 rows/day on real usage,
 * ≈146,000 a year — because `ScrollEventDao.deleteOlderThan()` was written and
 * never wired to anything. The index makes the scans cheap; it does not make
 * the table bounded. Retention is still an open decision (audit A4).
 */
@Entity(
    tableName = "scroll_events",
    indices = [Index(value = ["day"])]
)
data class ScrollEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val day: String,            // "2025-01-15" — LOCAL date, LocalDate.now().toString(), NEVER UTC
    val appPackage: String,     // e.g. "com.instagram.android"
    val scrollCm: Float,        // accumulated cm for this batch, always positive
    val hourBucket: Int,        // 0–23, hour of day in local time when batch was flushed
    val timestamp: Long         // System.currentTimeMillis() at flush time
)
