package com.scrolla.firestore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duplicate-pass rule (BUILD_PLAN 1.3), asserted on returned values rather
 * than on the log lines that hid it for days.
 */
class SyncCadenceTest {

    @Test
    fun `first pass of a session always runs`() {
        assertTrue(SyncCadence.shouldRun(lastStartedAt = 0L, now = 0L, minSpacingMs = 60_000L))
    }

    @Test
    fun `never-synced is not treated as synced at the epoch`() {
        // now == 0 and lastStartedAt == 0 would read as "elapsed 0, skip" under
        // a plain subtraction. The one pass that matters most must not be the
        // one that is skipped.
        assertTrue(SyncCadence.shouldRun(lastStartedAt = 0L, now = 10L, minSpacingMs = 60_000L))
    }

    @Test
    fun `the launch double-trigger is collapsed`() {
        // The measured case: foreground at t, periodic 418 ms later.
        val foregroundAt = 1_000_000L
        assertTrue(SyncCadence.shouldRun(0L, foregroundAt))
        assertFalse(SyncCadence.shouldRun(lastStartedAt = foregroundAt, now = foregroundAt + 418L))
    }

    @Test
    fun `a second trigger still loses after a full pass has run`() {
        // Passes are serialised, so the second trigger asks only once the first
        // has finished — roughly six seconds later on device. It must still be
        // recognised as the launch duplicate it is.
        val startedAt = 1_000_000L
        assertFalse(SyncCadence.shouldRun(lastStartedAt = startedAt, now = startedAt + 6_000L))
    }

    @Test
    fun `the fifteen-minute timer is never throttled`() {
        val startedAt = 1_000_000L
        val nextTick = startedAt + 15 * 60 * 1000L
        assertTrue(SyncCadence.shouldRun(lastStartedAt = startedAt, now = nextTick))
    }

    @Test
    fun `exactly at the spacing boundary runs`() {
        val startedAt = 1_000_000L
        assertTrue(
            SyncCadence.shouldRun(
                lastStartedAt = startedAt,
                now = startedAt + SyncCadence.MIN_SYNC_SPACING_MS
            )
        )
    }

    @Test
    fun `one millisecond short of the boundary skips`() {
        val startedAt = 1_000_000L
        assertFalse(
            SyncCadence.shouldRun(
                lastStartedAt = startedAt,
                now = startedAt + SyncCadence.MIN_SYNC_SPACING_MS - 1
            )
        )
    }

    @Test
    fun `a backwards clock runs rather than skips`() {
        // An NTP correction or a user editing the date must not be able to
        // strand someone off their group's board until the next tick.
        val startedAt = 1_000_000L
        assertTrue(SyncCadence.shouldRun(lastStartedAt = startedAt, now = startedAt - 5_000L))
    }

    @Test
    fun `a foreground return after a minute away still syncs`() {
        val startedAt = 1_000_000L
        assertTrue(SyncCadence.shouldRun(lastStartedAt = startedAt, now = startedAt + 60_001L))
    }
}
