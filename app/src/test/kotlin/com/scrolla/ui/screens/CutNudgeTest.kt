package com.scrolla.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BUILD_PLAN 3.4.
 *
 * This line tells someone to change their behaviour and promises a result. It
 * is rendered with nobody checking the arithmetic first, so the arithmetic is
 * checked here — and most of all, the cases where it should say nothing at all.
 */
class CutNudgeTest {

    @Test
    fun `names the rank a cut would reach`() {
        // 5.0 km, cutting 2.0 km of Instagram by a fifth saves 0.4 -> 4.6,
        // which slips past Maya on 4.8. Third becomes second.
        val rank = cutNudgeRank(
            topAppKm = 2.0f,
            selfKm = 5.0f,
            memberKm = listOf(3.0f, 4.8f, 5.0f)
        )

        assertEquals(2, rank)
    }

    @Test
    fun `says nothing when the cut would not move you`() {
        // Saves 0.1 km and the person ahead is a kilometre clear. Offering a
        // plan that lands you exactly where you already are is filler.
        assertNull(
            cutNudgeRank(
                topAppKm = 0.5f,
                selfKm = 5.0f,
                memberKm = listOf(3.0f, 3.9f, 5.0f)
            )
        )
    }

    @Test
    fun `says nothing when there is no honest rank`() {
        // A group of one has no standing to improve on. This is standingOf's
        // refusal, inherited rather than re-decided.
        assertNull(cutNudgeRank(topAppKm = 2f, selfKm = 5f, memberKm = listOf(5f)))
    }

    @Test
    fun `says nothing when the user has not synced today`() {
        assertNull(cutNudgeRank(topAppKm = 2f, selfKm = null, memberKm = listOf(3f, 4f)))
    }

    @Test
    fun `says nothing when there is no app data`() {
        // A zero-length cut saves nothing, and "cutting nothing by 20% would
        // put you in 2nd" is a lie about arithmetic.
        assertNull(cutNudgeRank(topAppKm = 0f, selfKm = 5f, memberKm = listOf(3f, 5f)))
    }

    @Test
    fun `already first stays silent`() {
        // Nothing above first place, so no cut can promise anything.
        assertNull(cutNudgeRank(topAppKm = 3f, selfKm = 1f, memberKm = listOf(1f, 4f, 6f)))
    }

    @Test
    fun `a duplicate total does not remove the wrong person`() {
        // Two members are on exactly 5.0 and one of them is the user. Removing
        // every 5.0 from the comparison would delete the other person from the
        // group, and the nudge would promise a rank that counted one fewer
        // competitor than actually exists.
        val rank = cutNudgeRank(
            topAppKm = 4.0f,
            selfKm = 5.0f,
            memberKm = listOf(4.5f, 5.0f, 5.0f)
        )

        // 5.0 - 0.8 = 4.2, ahead of 4.5 and of the other person on 5.0: first.
        assertEquals(1, rank)
    }

    @Test
    fun `a cut cannot push a total below zero`() {
        val rank = cutNudgeRank(
            topAppKm = 100f,
            selfKm = 1f,
            memberKm = listOf(0.5f, 1f)
        )

        // Clamped at 0, which is still ahead of 0.5 — first, not a negative rank.
        assertEquals(1, rank)
    }
}
