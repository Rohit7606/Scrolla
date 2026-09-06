package com.scrolla.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the fix for the reset guard that only logged (AUDIT_A_TRACK A1).
 *
 * The point of every test here is to assert on the **returned delta**, not on
 * whether a branch was entered. The broken version entered the right branch,
 * logged the right message, and then returned the phantom distance anyway —
 * which is how it passed SPRINT_LOG S0.5 and REVIEW_LOG review #1.
 */
class ScrollDeltaTest {

    private val threshold = 500

    // ---- the A1 regression itself ----

    @Test
    fun `a view recycle contributes zero distance`() {
        // The canonical recycle: a list jumps from 5000px down to the top.
        val result = ScrollDelta.compute(
            scrollY = 0 + 1,          // non-zero so we stay on the position path
            lastKnownY = 5000,
            scrollDeltaY = null,
            resetThresholdPx = threshold
        )
        assertEquals("a recycle must contribute no distance", 0, result.delta)
        assertTrue(result.wasRecycleReset)
    }

    @Test
    fun `a view recycle still moves the baseline`() {
        // Discarding the distance is only half of it. If the baseline did not
        // move, the *next* event would measure from 5000 and produce a second,
        // equally bogus jump.
        val result = ScrollDelta.compute(
            scrollY = 1,
            lastKnownY = 5000,
            scrollDeltaY = null,
            resetThresholdPx = threshold
        )
        assertEquals(1, result.newBaseline)
    }

    @Test
    fun `the event after a recycle measures from the new position`() {
        val recycle = ScrollDelta.compute(
            scrollY = 1, lastKnownY = 5000, scrollDeltaY = null, resetThresholdPx = threshold
        )
        val next = ScrollDelta.compute(
            scrollY = 121, lastKnownY = recycle.newBaseline, scrollDeltaY = null, resetThresholdPx = threshold
        )
        assertEquals("120px of real scrolling after the recycle", 120, next.delta)
        assertFalse(next.wasRecycleReset)
    }

    @Test
    fun `the raw delta is preserved for logging even though it is discarded`() {
        val result = ScrollDelta.compute(
            scrollY = 1, lastKnownY = 5000, scrollDeltaY = null, resetThresholdPx = threshold
        )
        assertEquals(-4999, result.rawDelta)
        assertEquals(0, result.delta)
    }

    // ---- the boundary, which decides what abs() is allowed to see ----

    @Test
    fun `upward scrolling just inside the threshold is real distance`() {
        // -500 is not "more negative than -500", so it counts. This is the case
        // the guard must not swallow: scrolling up is scrolling.
        val result = ScrollDelta.compute(
            scrollY = 1000, lastKnownY = 1500, scrollDeltaY = null, resetThresholdPx = threshold
        )
        assertEquals(-500, result.delta)
        assertFalse("−500 is a big thumb swipe, not a recycle", result.wasRecycleReset)
    }

    @Test
    fun `one pixel past the threshold is a recycle`() {
        val result = ScrollDelta.compute(
            scrollY = 1000, lastKnownY = 1501, scrollDeltaY = null, resetThresholdPx = threshold
        )
        assertEquals(0, result.delta)
        assertTrue(result.wasRecycleReset)
    }

    @Test
    fun `a large positive jump is not treated as a recycle`() {
        // Deliberate: the guard is one-sided. A big forward jump is a fling,
        // and flings are real distance. Documented so the asymmetry is a choice
        // rather than an oversight.
        val result = ScrollDelta.compute(
            scrollY = 6000, lastKnownY = 1000, scrollDeltaY = null, resetThresholdPx = threshold
        )
        assertEquals(5000, result.delta)
        assertFalse(result.wasRecycleReset)
    }

    // ---- ordinary scrolling ----

    @Test
    fun `downward scrolling counts as the change in position`() {
        val result = ScrollDelta.compute(
            scrollY = 340, lastKnownY = 200, scrollDeltaY = null, resetThresholdPx = threshold
        )
        assertEquals(140, result.delta)
        assertEquals(340, result.newBaseline)
    }

    @Test
    fun `the first event on a view contributes nothing but sets the baseline`() {
        // A view opened at an offset must not bill the user for where it began.
        val result = ScrollDelta.compute(
            scrollY = 3200, lastKnownY = null, scrollDeltaY = null, resetThresholdPx = threshold
        )
        assertEquals(0, result.delta)
        assertEquals(3200, result.newBaseline)
        assertFalse(result.wasRecycleReset)
    }

    // ---- the scrollDeltaY fallback path (Instagram) ----

    @Test
    fun `scrollY of zero falls back to the reported delta`() {
        val result = ScrollDelta.compute(
            scrollY = 0, lastKnownY = null, scrollDeltaY = 260, resetThresholdPx = threshold
        )
        assertEquals(260, result.delta)
    }

    @Test
    fun `the fallback path never touches the baseline`() {
        // These values are deltas, not positions. Storing one as a baseline
        // would corrupt the next position-path event on the same view.
        val result = ScrollDelta.compute(
            scrollY = 0, lastKnownY = 900, scrollDeltaY = 260, resetThresholdPx = threshold
        )
        assertNull(result.newBaseline)
    }

    @Test
    fun `the platform's unknown sentinel is not distance`() {
        val result = ScrollDelta.compute(
            scrollY = 0, lastKnownY = null, scrollDeltaY = -1, resetThresholdPx = threshold
        )
        assertEquals(0, result.delta)
    }

    @Test
    fun `a negative reported delta is real upward scrolling`() {
        // -1 is the sentinel; every other negative is a genuine scroll up, and
        // pxToCm's abs() is what makes it count.
        val result = ScrollDelta.compute(
            scrollY = 0, lastKnownY = null, scrollDeltaY = -260, resetThresholdPx = threshold
        )
        assertEquals(-260, result.delta)
    }

    @Test
    fun `below API 28 there is no fallback and nothing is counted`() {
        val result = ScrollDelta.compute(
            scrollY = 0, lastKnownY = null, scrollDeltaY = null, resetThresholdPx = threshold
        )
        assertEquals(0, result.delta)
        assertNull(result.newBaseline)
    }

    /**
     * The shape from the audit's evidence table: Chrome producing 435 cm in a
     * ten-second batch. Fifty events of ordinary scrolling with three recycles
     * mixed in should bill only the ordinary scrolling.
     */
    @Test
    fun `a batch of scrolling with recycles bills only the scrolling`() {
        var baseline: Int? = null
        var total = 0
        val positions = mutableListOf<Int>()
        repeat(20) { positions.add(200 + it * 120) }   // steady scroll down
        positions.add(1)                               // recycle to the top
        repeat(20) { positions.add(200 + it * 120) }   // and again

        for (y in positions) {
            val r = ScrollDelta.compute(y, baseline, null, threshold)
            baseline = r.newBaseline
            total += kotlin.math.abs(r.delta)
        }

        // Two runs of 19 steps at 120px, plus the 199px from the recycled
        // position 1 back up to 200 — which is real movement and should count.
        val realScrolling = 2 * 19 * 120 + 199
        assertEquals(realScrolling, total)

        // The number that matters: the 2479px recycle jump is absent. The
        // pre-fix code returned it, and pxToCm's abs() made it positive, so
        // this same batch used to bill 2479px more than the thumb travelled.
        assertEquals(4759, total)
        assertTrue("the phantom jump must not be in the total", total < 4759 + 2479)
    }
}
