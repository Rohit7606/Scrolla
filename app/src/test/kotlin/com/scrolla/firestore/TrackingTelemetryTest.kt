package com.scrolla.firestore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides whether a silent day is reported once or twenty-four
 * times.
 *
 * It looks trivial, and that is the point: the interesting part is *what it is
 * keyed on*. Keying on "have I reported in the last N hours" would be a guess
 * that drifts with the sync cadence. Keying on the outage's own
 * `lastEventTimestamp` is exact, because during an outage nothing is recorded
 * and so that value physically cannot move.
 */
class TrackingTelemetryTest {

    @Test
    fun `an outage reports the first time`() {
        assertTrue(TrackingTelemetry.shouldReport(lastEventAt = 1_000L, alreadyReportedFor = null))
    }

    @Test
    fun `the same outage does not report again`() {
        // Every hourly sync pass during a silent day sees the identical
        // lastEventTimestamp, because a dead service records nothing that could
        // advance it. One outage, one report.
        assertFalse(
            TrackingTelemetry.shouldReport(lastEventAt = 1_000L, alreadyReportedFor = 1_000L)
        )
    }

    @Test
    fun `a later outage reports again`() {
        // Tracking recovered, recorded something at 5000, then died again. That
        // is a second outage and a second fact worth knowing — suppressing it
        // would make a phone that dies daily look like a phone that died once.
        assertTrue(
            TrackingTelemetry.shouldReport(lastEventAt = 5_000L, alreadyReportedFor = 1_000L)
        )
    }

    @Test
    fun `a device with no events ever is still one outage`() {
        // lastEventTimestamp is 0 on a fresh install that has never recorded.
        // It reports once and then stops, rather than every hour forever.
        assertTrue(TrackingTelemetry.shouldReport(lastEventAt = 0L, alreadyReportedFor = null))
        assertFalse(TrackingTelemetry.shouldReport(lastEventAt = 0L, alreadyReportedFor = 0L))
    }
}
