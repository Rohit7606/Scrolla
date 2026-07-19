package com.scrolla.model

object ScrollaConstants {

    // --- Tracking ---
    val BATCH_FLUSH_EVENT_COUNT = 50        // flush to Room after this many scroll events
    val BATCH_FLUSH_INTERVAL_MS = 10_000L   // or after 10 seconds, whichever comes first
    val RECYCLE_RESET_THRESHOLD_PX = 500    // delta larger than this (negative) = view recycle, not real scroll

    // --- Sync ---
    val FIRESTORE_SYNC_INTERVAL_MS = 15 * 60 * 1000L   // 15 minutes — do not shorten without checking quota math
    val LEADERBOARD_CACHE_STALE_MS = 2 * 60 * 1000L    // skip re-read if last read was under 2 minutes ago

    // --- Notification ---
    val FOREGROUND_NOTIFICATION_ID = 1001
    val NOTIFICATION_CHANNEL_ID = "scrolla_tracking"
    val NOTIFICATION_CHANNEL_NAME = "Scroll Tracking"
    val NOTIFICATION_TEXT = "Tracking scroll distance"  // exact wording, not a placeholder

    // --- Distance ---
    val CM_PER_KM = 100_000f

    // --- Group ---
    val GROUP_CODE_LENGTH = 6
    val FIRESTORE_SYNC_INTERVAL_MIN = 15    // same as above, expressed in minutes for display use

}