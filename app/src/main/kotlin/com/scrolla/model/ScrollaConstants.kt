package com.scrolla.model

object ScrollaConstants {

    // --- Tracking ---
    val BATCH_FLUSH_EVENT_COUNT = 50        // flush to Room after this many scroll events
    val BATCH_FLUSH_INTERVAL_MS = 10_000L   // or after 10 seconds, whichever comes first
    val RECYCLE_RESET_THRESHOLD_PX = 500    // delta larger than this (negative) = view recycle, not real scroll

    // --- Sync ---
    val FIRESTORE_SYNC_INTERVAL_MS = 15 * 60 * 1000L   // 15 minutes — do not shorten without checking quota math

    /**
     * How often the background worker syncs when the app is **not** open
     * (BUILD_PLAN 1.1). Four times longer than the foreground interval, on
     * purpose.
     *
     * Foreground sync costs what it costs because someone is looking at the
     * screen and wants the number to be right. Background sync exists for a
     * different and much weaker requirement: that a user who tracked all day
     * and never opened Scrolla still has a document for today, so the group's
     * board is not just "whoever happened to open the app".
     *
     * An hour stale is invisible on a board that resets daily. Fifteen minutes
     * would be 96 passes a day instead of 24, against a measured battery cost
     * that is already **82 % network** — so the four-times-cheaper option that
     * satisfies the actual requirement is the right one. WorkManager's floor is
     * 15 minutes, so this can be lowered if the board ever turns out to feel
     * stale, but that should follow a complaint, not a guess.
     */
    val BACKGROUND_SYNC_INTERVAL_MINUTES = 60L
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