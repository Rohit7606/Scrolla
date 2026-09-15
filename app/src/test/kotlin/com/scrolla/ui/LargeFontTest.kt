package com.scrolla.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.scrolla.ui.screens.AppBreakdownScreen
import com.scrolla.ui.screens.InsightsScreen
import com.scrolla.ui.screens.LeaderboardScreen
import com.scrolla.ui.screens.PrivacyScreen
import com.scrolla.ui.screens.ScrollaStrings
import com.scrolla.ui.screens.SettingsScreen
import com.scrolla.ui.theme.ScrollaUILabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BUILD_PLAN 3.7 — every screen at 200 % font scale.
 *
 * An app built on an accessibility API should survive one. Someone who has
 * turned their system font all the way up is very plausibly the person who most
 * wants a tool that measures how much they stare at a phone.
 *
 * **This is a Compose test because the device refuses.** MIUI blocks
 * `adb shell settings put system font_scale` the same way it blocks
 * `settings put secure` and input injection, so the scale cannot be driven on
 * the one phone this project tests on. Density is overridden directly here
 * instead, which is what the system setting does anyway.
 *
 * What this catches: text that vanishes, a screen that throws under the tighter
 * constraints, content pushed entirely out of its container. What it cannot
 * catch is overlap that still renders — that needs eyes on a screenshot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ScreenTestApplication::class)
class LargeFontTest {

    @get:Rule
    val compose = createComposeRule()

    /** The Android accessibility maximum. */
    private val fontScale = 2.0f

    private fun atLargeFont(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale)
            ) {
                ScrollaUILabTheme { content() }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
    }

    /**
     * Checks the instrument before trusting it.
     *
     * Every other test here passes trivially if the density override silently
     * fails to apply — they would render at 100 %, assert some text exists, and
     * report that the app survives a font scale it never saw. That is a worse
     * outcome than having no test, because it is a green tick that means
     * nothing.
     */
    @Test
    fun the_font_scale_is_actually_applied() {
        var seen = 0f
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale)
            ) {
                seen = LocalDensity.current.fontScale
            }
        }
        compose.waitForIdle()

        org.junit.Assert.assertEquals(2.0f, seen, 0.0001f)
    }

    @Test
    fun insights_survives_200_percent() {
        atLargeFont { InsightsScreen() }
        compose.onNodeWithText(ScrollaStrings.INSIGHTS_TITLE).assertIsDisplayed()
    }

    @Test
    fun settings_survives_200_percent() {
        atLargeFont { SettingsScreen() }
        compose.onNodeWithText(ScrollaStrings.SETTINGS_TITLE).assertIsDisplayed()
    }

    @Test
    fun privacy_survives_200_percent() {
        // The screen that makes the promises. If anything has to stay readable
        // at any font size, it is this one.
        atLargeFont { PrivacyScreen() }
        compose.onNodeWithText(ScrollaStrings.PRIVACY_TITLE).assertIsDisplayed()
    }

    @Test
    fun appBreakdown_survives_200_percent() {
        atLargeFont { AppBreakdownScreen() }
        compose.onNodeWithText(ScrollaStrings.APP_BREAKDOWN_TITLE).assertIsDisplayed()
    }

    @Test
    fun leaderboard_survives_200_percent() {
        // The rows carry a rank, a name and a figure on one line, which is the
        // layout most likely to give way when the type doubles.
        atLargeFont { LeaderboardScreen(memberCount = 5) }
        compose.onNodeWithText("5 of 5 synced today").assertIsDisplayed()
    }
}
