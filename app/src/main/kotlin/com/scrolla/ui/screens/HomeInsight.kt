package com.scrolla.ui.screens

import com.scrolla.model.DistanceFormatter

/** One rotating insight for Home: a short label and the sentence under it. */
data class HomeInsight(val label: String, val body: String)

/**
 * Picks Home's rotating insight (SPRINT_LOG S2.1, PREMIUM_CHECKLIST P3.2f).
 *
 * S2.1 asked for "a rotating insight card wired to ≥ 1 real insight type" and
 * one type was built, so the clause passed while the card never rotated. Three
 * of the four labels written for it — personal best, quick win, and the
 * placeholder — had never been rendered.
 *
 * Two rules, both inherited from the rest of the app:
 *
 * **Never invent.** Every insight here is derived from a value that exists. If
 * none qualify the card says so, rather than showing a confident sentence about
 * nothing — which is what the unused placeholder copy was written for.
 *
 * **Rotate deterministically.** [dayOfYear] picks among whatever qualifies, so
 * the card is stable for a whole day rather than changing on every recomposition.
 * An insight that flickers as you scroll the screen reads as a bug.
 *
 * Pure and clock-free so it is testable without Android.
 */
object HomeInsights {

    fun select(
        todayKm: Float,
        hasSensorData: Boolean,
        peakHour: Int?,
        personalBestKm: Float?,
        yesterdayKm: Float?,
        dayOfYear: Int
    ): HomeInsight {
        val candidates = mutableListOf<HomeInsight>()

        if (peakHour != null) {
            candidates += HomeInsight(
                label = ScrollaStrings.HOME_INSIGHT_PEAK_HOUR_LABEL,
                body = "Most of it happens between ${ScrollaFormatters.formatHourRange(peakHour)}."
            )
        }

        // Only once today is genuinely under the best day. "You are close to your
        // best" while sitting above it would be flattery, not information.
        if (personalBestKm != null && hasSensorData && todayKm > 0f && todayKm < personalBestKm) {
            candidates += HomeInsight(
                label = ScrollaStrings.HOME_INSIGHT_PERSONAL_BEST_LABEL,
                body = "Your quietest day so far is ${DistanceFormatter.formatDistance(personalBestKm)}. " +
                    "Today is under it."
            )
        }

        // Lowest wins, so being below yesterday is the good direction.
        if (yesterdayKm != null && todayKm > 0f && todayKm < yesterdayKm) {
            val saved = yesterdayKm - todayKm
            candidates += HomeInsight(
                label = ScrollaStrings.HOME_INSIGHT_QUICK_WIN_LABEL,
                body = "That is ${DistanceFormatter.formatDistance(saved)} less than yesterday."
            )
        }

        if (candidates.isEmpty()) {
            return HomeInsight(
                label = ScrollaStrings.HOME_INSIGHT_PLACEHOLDER_LABEL,
                body = ScrollaStrings.HOME_INSIGHT_PLACEHOLDER_BODY
            )
        }

        // Math.floorMod, not %, so a negative dayOfYear cannot throw.
        return candidates[Math.floorMod(dayOfYear, candidates.size)]
    }
}
