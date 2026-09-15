package com.scrolla.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.scrolla.ui.screens.LeaderboardEntry
import com.scrolla.ui.screens.LeaderboardScreen
import com.scrolla.ui.screens.ScrollaStrings
import com.scrolla.ui.theme.ScrollaUILabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BUILD_PLAN 3.5 — the three group states nobody designs for and everybody
 * ends up in: a group of one, a group where nobody has synced today, and a
 * member who never syncs at all.
 *
 * All three were already handled when this was written. These tests exist so
 * they stay handled: each one is a branch that only appears for a user in an
 * unusual state, which is precisely the branch that gets broken by a refactor
 * and noticed by a stranger rather than by us.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ScreenTestApplication::class)
class GroupEdgeCaseTest {

    @get:Rule
    val compose = createComposeRule()

    private fun settle() = compose.mainClock.advanceTimeBy(1_500)

    @Test
    fun `a group of one is not a ranking`() {
        // The whole product is a comparison. Telling someone they came first
        // out of themselves is the participation trophy standingOf refuses on
        // Home, and the board must refuse it too.
        compose.setContent {
            ScrollaUILabTheme {
                LeaderboardScreen(
                    entries = listOf(LeaderboardEntry("You", 2.3f, isSelf = true)),
                    memberCount = 1
                )
            }
        }
        settle()

        compose.onNodeWithText(ScrollaStrings.LEADERBOARD_EMPTY_SOLO_HEADLINE).assertIsDisplayed()
        compose.onNodeWithText(ScrollaStrings.LEADERBOARD_EMPTY_SOLO_BODY).assertIsDisplayed()
    }

    @Test
    fun `a group where nobody has synced says so`() {
        // Distinct from "you have no group", which is a different problem with
        // a different fix. Reporting one as the other sends someone to join a
        // group they are already in.
        compose.setContent {
            ScrollaUILabTheme {
                LeaderboardScreen(
                    entries = emptyList(),
                    memberCount = 4,
                    emptyBoardMessage = ScrollaStrings.LEADERBOARD_EMPTY_NO_TOTALS
                )
            }
        }
        settle()

        compose.onNodeWithText(ScrollaStrings.LEADERBOARD_EMPTY_NO_TOTALS).assertIsDisplayed()
    }

    @Test
    fun `members who never sync are counted but not invented`() {
        // Five people are in the group; two have reported today. The caption
        // has to say both numbers, or a short list reads as the whole group and
        // second of two looks like second of five.
        compose.setContent {
            ScrollaUILabTheme {
                LeaderboardScreen(
                    entries = listOf(
                        LeaderboardEntry("Monish", 0.9f),
                        LeaderboardEntry("You", 2.3f, isSelf = true)
                    ),
                    memberCount = 5
                )
            }
        }
        settle()

        compose.onNodeWithText("2 of 5 synced today").assertIsDisplayed()
    }
}
