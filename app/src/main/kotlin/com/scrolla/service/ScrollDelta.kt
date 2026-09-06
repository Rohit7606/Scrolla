package com.scrolla.service

import com.scrolla.model.ScrollaConstants

/**
 * The per-event distance decision, pulled out of [ScrollAccessibilityService]
 * so it can be tested without an Android device.
 *
 * **Why this file exists.** The reset guard (A1 in `DOCS/AUDIT_A_TRACK.md`)
 * shipped broken for two months and was signed off twice - SPRINT_LOG S0.5 and
 * REVIEW_LOG review #1 - because both checks confirmed that the "RESET
 * DETECTED" log line appeared. It did appear. The guard logged and then
 * returned the delta anyway, so every view recycle became phantom distance.
 * Watching a log prove that a branch was *entered* says nothing about what the
 * branch *returned*, and the branch's return value is the entire product.
 *
 * So the arithmetic lives here as a pure function with no Android types in its
 * signature, and `ScrollDeltaTest` asserts on returned values.
 *
 * Behaviour is unchanged from the service's original three-path structure apart
 * from the A1 fix itself.
 */
object ScrollDelta {

    /**
     * @param delta pixels to count as distance. Sign is preserved; the caller
     *   converts with `DistanceFormatter.pxToCm`, which applies `abs()`.
     * @param newBaseline non-null means "store this against the composite key".
     *   Null means leave the baseline untouched - the `scrollDeltaY` path
     *   deliberately does not maintain one, because the values it reads are
     *   already deltas rather than positions.
     * @param wasRecycleReset whether this event was treated as a view recycle
     *   and discarded. For logging, and for tests to assert the reason rather
     *   than just the number.
     * @param rawDelta what the position arithmetic produced before the reset
     *   guard. Equal to [delta] unless [wasRecycleReset].
     */
    data class Result(
        val delta: Int,
        val newBaseline: Int?,
        val wasRecycleReset: Boolean,
        val rawDelta: Int
    )

    /**
     * @param scrollY `AccessibilityEvent.scrollY`; 0 means the app does not
     *   report an absolute scroll position (Instagram, and Chrome in some
     *   modes) - see SENSOR_PROGRESS §4.
     * @param lastKnownY the baseline held for this composite key, or null if
     *   this is the first event seen for that view.
     * @param scrollDeltaY `AccessibilityEvent.scrollDeltaY`, or null when the
     *   platform predates API 28 and cannot report it. The platform uses -1 for
     *   "unknown", which is not a distance.
     */
    fun compute(
        scrollY: Int,
        lastKnownY: Int?,
        scrollDeltaY: Int?,
        resetThresholdPx: Int = ScrollaConstants.RECYCLE_RESET_THRESHOLD_PX
    ): Result {
        // Path 1: the app reports an absolute position, so distance is the
        // change in that position since the last event on this same view.
        if (scrollY != 0) {
            // No baseline yet means nothing to subtract from. The first event
            // on a view establishes the baseline and contributes nothing, so a
            // view that opens already scrolled down does not bill the user for
            // the offset it started at.
            val computed = if (lastKnownY != null) scrollY - lastKnownY else 0

            // A jump this far negative is the view recycling its position -
            // RecyclerView rebinding, a page load, a tab switch - not a thumb
            // travelling up the screen. Real upward scrolling is bounded by how
            // far a finger moves in one event.
            val isReset = computed < -resetThresholdPx

            return Result(
                // The baseline still moves to the new position (below), so the
                // *next* event measures from where the view actually is. This
                // event contributes zero: nothing was scrolled, the view jumped.
                delta = if (isReset) 0 else computed,
                newBaseline = scrollY,
                wasRecycleReset = isReset,
                rawDelta = computed
            )
        }

        // Path 2: scrollY == 0, so fall back to the reported delta. There is no
        // position to keep a baseline from, hence newBaseline = null.
        //
        // Note this path has no reset detection and needs none - a recycle
        // shows up as a position jump, and there are no positions here.
        val reported = scrollDeltaY?.takeIf { it != UNKNOWN_DELTA && it != 0 } ?: 0
        return Result(
            delta = reported,
            newBaseline = null,
            wasRecycleReset = false,
            rawDelta = reported
        )
    }

    /** The platform's "no delta available" sentinel for `scrollDeltaY`. */
    const val UNKNOWN_DELTA = -1
}
