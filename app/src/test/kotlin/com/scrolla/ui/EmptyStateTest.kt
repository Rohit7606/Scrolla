package com.scrolla.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.scrolla.ui.screens.AppBreakdownScreen
import com.scrolla.ui.screens.HallOfFameScreen
import com.scrolla.ui.screens.PersonalRecordsScreen
import com.scrolla.ui.screens.PrivacyScreen
import com.scrolla.ui.screens.ScrollaStrings
import com.scrolla.ui.screens.WeeklyRecapScreen
import com.scrolla.ui.theme.ScrollaUILabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first instrumented tests in this project (BUILD_PLAN 2.5), aimed squarely
 * at BUILD_PLAN 2.3: **no screen had ever been seen in its empty state**, and
 * that is where the last three UI bugs were.
 *
 * Why these screens and this state. Every one of them takes a `hasData` /
 * `hasRecord` flag whose default is the *populated* case, next to defaults like
 * `bestDayDate = "July 12"` and `topApp = "Instagram"`. Those exist for Compose
 * previews and they render perfectly plausibly, so a screen wired up wrongly
 * does not look broken — it looks like someone else's data. P4.2 is the same
 * failure caught once already.
 *
 * These tests do not check the populated path. A populated screen that is wrong
 * is visible the moment anyone opens it; an empty screen is the one nobody
 * looks at until a new user does, and a new user is every one of the fifty
 * people this is being handed to.
 *
 * These run on the JVM under Robolectric, in the ordinary `test` task — so
 * they run on every build rather than only when someone remembers to attach a
 * device. That matters more here than fidelity to real hardware: the thing
 * being asserted is which copy a screen renders when it has nothing, and the
 * failure mode is a build where nobody ran the check at all.
 */
@RunWith(RobolectricTestRunner::class)
// sdk 34: the app targets 35, and the only emulator image on this machine is
// API 37, where Espresso still calls the removed InputManager.getInstance().
//
// application: a bare Application instead of ScrollaApplication — see
// ScreenTestApplication for why booting Firebase to check a screen's empty
// state is both unnecessary and actively misleading.
@Config(sdk = [34], application = ScreenTestApplication::class)
class EmptyStateTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Every one of these screens animates itself in from a
     * `LaunchedEffect(Unit) { isVisible = true }`, so at composition time the
     * content is not on screen yet. Settling the clock past the longest entry
     * animation makes the assertions deterministic instead of a race against
     * a 300-500 ms tween.
     */
    private fun settleEntryAnimation() {
        compose.mainClock.advanceTimeBy(1_500)
    }

    @Test
    fun personalRecords_withNoData_offersNoRecord() {
        compose.setContent {
            ScrollaUILabTheme {
                PersonalRecordsScreen(hasData = false)
            }
        }

        settleEntryAnimation()
        compose.onNodeWithText(ScrollaStrings.RECORDS_EMPTY).assertIsDisplayed()
    }

    @Test
    fun personalRecords_withNoData_doesNotShowThePreviewDate() {
        // The specific trap: `bestDayDate` defaults to "July 12" and
        // `bestDayKm` to 0.4f. A user with no history must never be shown a
        // record that belongs to a Compose preview.
        compose.setContent {
            ScrollaUILabTheme {
                PersonalRecordsScreen(hasData = false)
            }
        }

        compose.onNodeWithText("July 12").assertDoesNotExist()
    }

    @Test
    fun appBreakdown_withNoData_saysSo() {
        compose.setContent {
            ScrollaUILabTheme {
                AppBreakdownScreen(hasData = false, apps = emptyList())
            }
        }

        settleEntryAnimation()
        compose.onNodeWithText(ScrollaStrings.APP_BREAKDOWN_EMPTY).assertIsDisplayed()
    }

    @Test
    fun appBreakdown_withNoData_doesNotNamePreviewApps() {
        // `apps` defaults to Instagram / TikTok / X. On a reverse leaderboard,
        // telling someone they scrolled 1.2 km of Instagram when they scrolled
        // nothing is not a cosmetic defect.
        compose.setContent {
            ScrollaUILabTheme {
                AppBreakdownScreen(hasData = false, apps = emptyList())
            }
        }

        compose.onNodeWithText("Instagram").assertDoesNotExist()
        compose.onNodeWithText("TikTok").assertDoesNotExist()
    }

    @Test
    fun weeklyRecap_withNoData_doesNotOfferAConfidentZero() {
        // This one already shipped: the ViewModel computed `hasData` and the
        // screen did not accept it, so an empty week rendered a hero figure of
        // 0 m and offered it for sharing.
        compose.setContent {
            ScrollaUILabTheme {
                WeeklyRecapScreen(weeklyDistanceKm = 0f, hasData = false)
            }
        }

        // Uppercased at the call site, so assert what is actually drawn. The first
        // version of this test asserted the raw constant and failed — the test was
        // wrong, not the screen.
        settleEntryAnimation()
        compose.onNodeWithText(ScrollaStrings.RECAP_EMPTY_HEADLINE.uppercase())
            .assertIsDisplayed()
    }

    @Test
    fun hallOfFame_withNoRecord_invitesRatherThanShowingZero() {
        compose.setContent {
            ScrollaUILabTheme {
                HallOfFameScreen(hasRecord = false)
            }
        }

        settleEntryAnimation()
        compose.onNodeWithText(ScrollaStrings.HALL_OF_FAME_NO_RECORD_TITLE).assertIsDisplayed()
        compose.onNodeWithText(ScrollaStrings.HALL_OF_FAME_NO_RECORD_BODY).assertIsDisplayed()
    }

    @Test
    fun hallOfFame_whenTheReadFailed_saysSoInsteadOfClaimingNoRecord() {
        // The distinction the screen's own KDoc calls out: "you could be first"
        // because nobody has set a record, and "you could be first" because the
        // network dropped, are different claims. Only one of them is honest.
        compose.setContent {
            ScrollaUILabTheme {
                HallOfFameScreen(
                    hasRecord = false,
                    errorMessage = ScrollaStrings.ERROR_GROUPS_UNAVAILABLE
                )
            }
        }

        settleEntryAnimation()
        compose.onNodeWithText(ScrollaStrings.ERROR_GROUPS_UNAVAILABLE).assertIsDisplayed()
        compose.onNodeWithText(ScrollaStrings.HALL_OF_FAME_NO_RECORD_TITLE).assertDoesNotExist()
    }

    @Test
    fun privacy_rendersAndKeepsItsCheckableClaim() {
        // New on 2026-09-14 and the one screen with no empty state, because it
        // has no data. The assertion is on the sentence that makes the page
        // worth anything: the one the user can verify without us.
        compose.setContent {
            ScrollaUILabTheme {
                PrivacyScreen()
            }
        }

        compose.onNodeWithText(ScrollaStrings.PRIVACY_SUMMARY_HEADLINE_2).assertIsDisplayed()
    }
}
