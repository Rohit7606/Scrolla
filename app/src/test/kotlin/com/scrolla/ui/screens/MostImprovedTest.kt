package com.scrolla.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BUILD_PLAN 3.3.
 *
 * Naming someone "most improved" is a public claim about another person, made
 * on a screen their friends are looking at. Nearly all of these assert that the
 * function **declines** — which is the whole design, and the same contract
 * [standingOf] has.
 */
class MostImprovedTest {

    private fun member(
        name: String,
        last7: Float?,
        prev7: Float?,
        isSelf: Boolean = false
    ) = ImprovementCandidate(name, isSelf, last7, prev7)

    @Test
    fun `names the biggest drop`() {
        val result = mostImprovedOf(
            listOf(
                member("Lewis", last7 = 4f, prev7 = 6f),   // down 2.0
                member("Maya", last7 = 3f, prev7 = 3.5f),  // down 0.5
                member("You", last7 = 5f, prev7 = 5.2f, isSelf = true)
            )
        )

        assertEquals("Lewis", result?.displayName)
        assertEquals(2f, result?.droppedKm ?: 0f, 0.0001f)
    }

    @Test
    fun `the reader can win it`() {
        // The point of the award is that the person at the bottom of a reverse
        // board has something they can take. That includes whoever is reading.
        val result = mostImprovedOf(
            listOf(
                member("You", last7 = 2f, prev7 = 9f, isSelf = true),
                member("Lewis", last7 = 4f, prev7 = 4.5f)
            )
        )

        assertEquals("You", result?.displayName)
        assertEquals(true, result?.isSelf)
    }

    @Test
    fun `refuses when only one member has both weeks`() {
        // "Most improved" of one is not an award, it is a participant.
        assertNull(
            mostImprovedOf(
                listOf(
                    member("Lewis", last7 = 1f, prev7 = 8f),
                    member("Maya", last7 = 3f, prev7 = null)
                )
            )
        )
    }

    @Test
    fun `a member with no previous week is not the worst backslider`() {
        // Maya joined four days ago. Treating her missing week as 0 km would
        // score her at -3 and, worse, would have made anyone new look like the
        // person getting worse fastest. She is simply not eligible yet.
        val result = mostImprovedOf(
            listOf(
                member("Lewis", last7 = 4f, prev7 = 6f),
                member("Alex", last7 = 5f, prev7 = 5.5f),
                member("Maya", last7 = 3f, prev7 = null)
            )
        )

        assertEquals("Lewis", result?.displayName)
    }

    @Test
    fun `a zero previous week is not an improvement`() {
        assertNull(
            mostImprovedOf(
                listOf(
                    member("Lewis", last7 = 0f, prev7 = 0f),
                    member("Maya", last7 = 0f, prev7 = 0f)
                )
            )
        )
    }

    @Test
    fun `a week where everyone scrolled more has no winner`() {
        // Rewarding the person who got worse the least is not recognition, and
        // the group can see the numbers.
        assertNull(
            mostImprovedOf(
                listOf(
                    member("Lewis", last7 = 7f, prev7 = 6f),
                    member("Maya", last7 = 9f, prev7 = 6f)
                )
            )
        )
    }

    @Test
    fun `a drop too small to mean anything is not an award`() {
        // 40 m across a whole week is one evening of not opening an app.
        assertNull(
            mostImprovedOf(
                listOf(
                    member("Lewis", last7 = 5.96f, prev7 = 6f),
                    member("Maya", last7 = 6.5f, prev7 = 6.51f)
                )
            )
        )
    }

    @Test
    fun `a tie at the top names nobody`() {
        // Both cut exactly 2 km. Picking whichever happened to sort first would
        // be arbitrary, and invisible to the person who did not get named.
        assertNull(
            mostImprovedOf(
                listOf(
                    member("Lewis", last7 = 4f, prev7 = 6f),
                    member("Maya", last7 = 2f, prev7 = 4f)
                )
            )
        )
    }

    @Test
    fun `an empty group has no winner`() {
        assertNull(mostImprovedOf(emptyList()))
    }
}
