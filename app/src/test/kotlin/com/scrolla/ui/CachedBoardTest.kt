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
 * BUILD_PLAN 2.7a — the board is allowed to show cached rows, but not to let
 * "N of M synced today" stand unqualified while it does.
 *
 * This is asserted here rather than on a phone because reproducing it by hand
 * means putting the device on a train: the radios have to be off *and* the tab
 * has to be opened, and MIUI refuses adb input injection, so nobody can drive
 * that sequence reliably enough to run it on every build. What a device can
 * prove — that Firestore actually sets `isFromCache` when the server is
 * unreachable — is proved by a log line in `GroupRepository`, once, on real
 * hardware. This covers the half that has to keep being true afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ScreenTestApplication::class)
class CachedBoardTest {

    @get:Rule
    val compose = createComposeRule()

    private val entries = listOf(
        LeaderboardEntry("Monish", 0.9f),
        LeaderboardEntry("You", 2.3f, isSelf = true)
    )

    private fun settleEntryAnimation() {
        compose.mainClock.advanceTimeBy(1_500)
    }

    @Test
    fun cachedBoard_saysSo() {
        compose.setContent {
            ScrollaUILabTheme {
                LeaderboardScreen(entries = entries, memberCount = 2, isFromCache = true)
            }
        }
        settleEntryAnimation()

        compose.onNodeWithText(ScrollaStrings.LEADERBOARD_FROM_CACHE).assertIsDisplayed()
    }

    @Test
    fun cachedBoard_still_shows_the_rows() {
        // The rows are the last true thing we knew and they stay. Blanking a
        // board because it could not be refreshed is the failure 2.7 was
        // originally written about, from the other direction.
        compose.setContent {
            ScrollaUILabTheme {
                LeaderboardScreen(entries = entries, memberCount = 2, isFromCache = true)
            }
        }
        settleEntryAnimation()

        compose.onNodeWithText("Monish").assertIsDisplayed()
        compose.onNodeWithText("2 of 2 synced today").assertIsDisplayed()
    }

    @Test
    fun freshBoard_saysNothing() {
        // The caption must not become wallpaper. If it showed on every load,
        // nobody would read it on the one load that matters.
        compose.setContent {
            ScrollaUILabTheme {
                LeaderboardScreen(entries = entries, memberCount = 2, isFromCache = false)
            }
        }
        settleEntryAnimation()

        compose.onNodeWithText(ScrollaStrings.LEADERBOARD_FROM_CACHE).assertDoesNotExist()
    }
}
