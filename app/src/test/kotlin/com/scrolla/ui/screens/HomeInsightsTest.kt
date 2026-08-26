package com.scrolla.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeInsightsTest {

    private fun select(
        todayKm: Float = 0.1f,
        hasSensorData: Boolean = true,
        peakHour: Int? = 21,
        personalBestKm: Float? = null,
        yesterdayKm: Float? = null,
        dayOfYear: Int = 1
    ) = HomeInsights.select(
        todayKm, hasSensorData, peakHour, personalBestKm, yesterdayKm, dayOfYear
    )

    @Test
    fun `with nothing to say it returns the placeholder rather than inventing one`() {
        val insight = select(todayKm = 0f, hasSensorData = false, peakHour = null)
        assertEquals(ScrollaStrings.HOME_INSIGHT_PLACEHOLDER_LABEL, insight.label)
        assertEquals(ScrollaStrings.HOME_INSIGHT_PLACEHOLDER_BODY, insight.body)
    }

    @Test
    fun `a personal best is only claimed when today is actually under it`() {
        // Above the best day: saying "today is under it" would be flattery.
        val above = select(todayKm = 0.5f, personalBestKm = 0.2f, peakHour = null)
        assertNotEquals(ScrollaStrings.HOME_INSIGHT_PERSONAL_BEST_LABEL, above.label)

        val below = select(todayKm = 0.1f, personalBestKm = 0.2f, peakHour = null)
        assertEquals(ScrollaStrings.HOME_INSIGHT_PERSONAL_BEST_LABEL, below.label)
    }

    @Test
    fun `the quick win only appears when today is below yesterday`() {
        // Lowest wins, so below yesterday is the good direction.
        val worse = select(todayKm = 0.3f, yesterdayKm = 0.1f, peakHour = null)
        assertNotEquals(ScrollaStrings.HOME_INSIGHT_QUICK_WIN_LABEL, worse.label)

        val better = select(todayKm = 0.1f, yesterdayKm = 0.3f, peakHour = null)
        assertEquals(ScrollaStrings.HOME_INSIGHT_QUICK_WIN_LABEL, better.label)
    }

    @Test
    fun `no yesterday row means no comparison, not a comparison against zero`() {
        val insight = select(todayKm = 0.1f, yesterdayKm = null, peakHour = null)
        assertEquals(ScrollaStrings.HOME_INSIGHT_PLACEHOLDER_LABEL, insight.label)
    }

    @Test
    fun `the same day always yields the same insight`() {
        // A card that changes on every recomposition reads as a bug.
        val args = { d: Int -> select(todayKm = 0.1f, personalBestKm = 0.5f, yesterdayKm = 0.4f, dayOfYear = d) }
        assertEquals(args(200).label, args(200).label)
        assertEquals(args(200).body, args(200).body)
    }

    @Test
    fun `it actually rotates across days when more than one qualifies`() {
        val labels = (1..12).map {
            select(todayKm = 0.1f, personalBestKm = 0.5f, yesterdayKm = 0.4f, dayOfYear = it).label
        }.toSet()
        assertTrue("expected rotation, got $labels", labels.size > 1)
    }

    @Test
    fun `a negative day of year does not throw`() {
        // floorMod rather than %, so this cannot go out of bounds.
        val insight = select(todayKm = 0.1f, personalBestKm = 0.5f, dayOfYear = -3)
        assertTrue(insight.body.isNotBlank())
    }

    @Test
    fun `a zero day produces no insight that implies scrolling happened`() {
        val insight = select(todayKm = 0f, personalBestKm = 0.5f, yesterdayKm = 0.4f, peakHour = null)
        assertEquals(ScrollaStrings.HOME_INSIGHT_PLACEHOLDER_LABEL, insight.label)
    }
}
