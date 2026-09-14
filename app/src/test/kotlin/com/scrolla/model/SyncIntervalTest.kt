package com.scrolla.model

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards on the background sync cadence (BUILD_PLAN 1.1).
 *
 * Small, and worth having because both failures here are silent. WorkManager
 * clamps a periodic interval below its own floor without complaining, so a
 * well-meant "make it more responsive" edit would produce a job running at a
 * rate nobody chose and no error anywhere.
 */
class SyncIntervalTest {

    /** WorkManager's `PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS`. */
    private val workManagerFloorMinutes = 15L

    @Test
    fun `background interval is at or above WorkManager's floor`() {
        assertTrue(
            "WorkManager silently clamps anything under $workManagerFloorMinutes min; " +
                "BACKGROUND_SYNC_INTERVAL_MINUTES is " +
                "${ScrollaConstants.BACKGROUND_SYNC_INTERVAL_MINUTES}",
            ScrollaConstants.BACKGROUND_SYNC_INTERVAL_MINUTES >= workManagerFloorMinutes
        )
    }

    @Test
    fun `background syncs less often than foreground`() {
        // The asymmetry is the point. Foreground is for someone watching the
        // number; background only has to get a document written for today, and
        // 82% of Scrolla's measured battery cost is already network.
        val backgroundMs = ScrollaConstants.BACKGROUND_SYNC_INTERVAL_MINUTES * 60 * 1000L
        assertTrue(
            "background ${backgroundMs}ms should exceed foreground " +
                "${ScrollaConstants.FIRESTORE_SYNC_INTERVAL_MS}ms",
            backgroundMs > ScrollaConstants.FIRESTORE_SYNC_INTERVAL_MS
        )
    }

    @Test
    fun `a background pass cannot be dropped by its own spacing rule`() {
        // SyncCadence drops a pass starting within MIN_SYNC_SPACING_MS of the
        // last. If the background interval were ever set below that window,
        // every background pass would be dropped on arrival and the worker
        // would run forever doing nothing — the precise shape of silent
        // failure this project keeps rediscovering.
        val backgroundMs = ScrollaConstants.BACKGROUND_SYNC_INTERVAL_MINUTES * 60 * 1000L
        assertTrue(
            "background interval must exceed the sync spacing window",
            backgroundMs > com.scrolla.firestore.SyncCadence.MIN_SYNC_SPACING_MS
        )
    }
}
