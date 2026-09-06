package com.scrolla.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The rule that decides whether the app accuses someone's phone of killing it.
 *
 * Two ways to be wrong, and they are not symmetric. Missing a real outage costs
 * a day of data. Crying wolf on a quiet morning teaches the user to ignore the
 * one message that matters — so every ambiguous case here should resolve to
 * silence.
 */
class TrackingGapTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun suspicious(last: Long, now: Long, enabled: Boolean = true) =
        TrackingGap.isSuspicious(enabled, last, now, zone)

    // ---- the outage this exists to catch ----

    @Test
    fun `the real 25 August outage is caught`() {
        // Service died 09:34, noticed that evening. 9.4 waking hours.
        val last = at(2026, 8, 25, 9, 34)
        val now = at(2026, 8, 25, 19, 0)
        assertTrue(suspicious(last, now))
        assertEquals(9.43, TrackingGap.wakingHoursSince(last, now, zone), 0.05)
    }

    @Test
    fun `a kill spanning into the next day is caught`() {
        val last = at(2026, 9, 4, 11, 0)
        val now = at(2026, 9, 5, 12, 0)
        assertTrue(suspicious(last, now))
    }

    // ---- the false alarm that would ruin it ----

    @Test
    fun `a normal night of sleep is not an outage`() {
        // Last scroll 22:30, app opened at 09:00. Eleven elapsed hours and
        // nothing wrong. This is the case a naive elapsed-time rule fires on
        // every single morning.
        val last = at(2026, 9, 5, 22, 30)
        val now = at(2026, 9, 6, 9, 0)
        assertFalse(suspicious(last, now))
        assertEquals(0.0, TrackingGap.wakingHoursSince(last, now, zone), 0.01)
    }

    @Test
    fun `a late night plus a lie-in is still not an outage`() {
        val last = at(2026, 9, 5, 23, 45)
        val now = at(2026, 9, 6, 12, 30)
        // Only 09:00-12:30 counts: 3.5 waking hours, under the threshold.
        assertEquals(3.5, TrackingGap.wakingHoursSince(last, now, zone), 0.01)
        assertFalse(suspicious(last, now))
    }

    @Test
    fun `a couple of hours in a lecture is not an outage`() {
        val last = at(2026, 9, 6, 10, 0)
        val now = at(2026, 9, 6, 13, 0)
        assertFalse(suspicious(last, now))
    }

    // ---- the boundary ----

    @Test
    fun `just under five waking hours stays quiet`() {
        val last = at(2026, 9, 6, 10, 0)
        val now = at(2026, 9, 6, 14, 50)
        assertFalse(suspicious(last, now))
    }

    @Test
    fun `five waking hours fires`() {
        val last = at(2026, 9, 6, 10, 0)
        val now = at(2026, 9, 6, 15, 0)
        assertTrue(suspicious(last, now))
    }

    @Test
    fun `silence outside waking hours does not accumulate`() {
        // 22:00 to 09:00 is entirely outside the window, however long it runs.
        val last = at(2026, 9, 5, 22, 0)
        val now = at(2026, 9, 6, 9, 0)
        assertEquals(0.0, TrackingGap.wakingHoursSince(last, now, zone), 0.01)
    }

    // ---- states that are somebody else's message, or nobody's ----

    @Test
    fun `a disabled service is not reported as a gap`() {
        // That is the tracking-off banner's job, and saying both at once would
        // be two different explanations for one problem.
        val last = at(2026, 8, 25, 9, 0)
        val now = at(2026, 8, 26, 20, 0)
        assertFalse(suspicious(last, now, enabled = false))
    }

    @Test
    fun `a first run with nothing ever recorded is not an outage`() {
        assertFalse(suspicious(0L, at(2026, 9, 6, 18, 0)))
    }

    @Test
    fun `a timestamp in the future fails closed`() {
        val now = at(2026, 9, 6, 12, 0)
        val last = at(2026, 9, 7, 12, 0)
        assertFalse(suspicious(last, now))
    }

    @Test
    fun `an equal timestamp is not a gap`() {
        val t = at(2026, 9, 6, 12, 0)
        assertFalse(suspicious(t, t))
        assertEquals(0.0, TrackingGap.wakingHoursSince(t, t, zone), 0.001)
    }

    // ---- multi-day, where the loop could go wrong ----

    @Test
    fun `a three day outage accumulates each day's waking hours`() {
        val last = at(2026, 9, 1, 9, 0)
        val now = at(2026, 9, 4, 9, 0)
        // Three full waking windows of 13 hours each.
        assertEquals(39.0, TrackingGap.wakingHoursSince(last, now, zone), 0.01)
        assertTrue(suspicious(last, now))
    }

    @Test
    fun `a very old timestamp does not hang the scan`() {
        val last = at(2020, 1, 1, 9, 0)
        val now = at(2026, 9, 6, 18, 0)
        assertTrue(suspicious(last, now))
    }
}
