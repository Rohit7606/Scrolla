package com.scrolla.model

import java.util.Locale

/**
 * Shared distance conversion and formatting. Per `DATA_CONTRACT.md` Section 5,
 * this is the single home for turning raw pixels into cm, cm into km, and km
 * into display strings. Both tracks import from here — do not re-implement any
 * of it, and do not format km inline at a call site.
 *
 * `model/` is edit-together per `AGENTS.md` Section 2: tell the other person
 * before changing anything in this file.
 */
object DistanceFormatter {

    /** Converts raw pixel delta (from AccessibilityEvent) to cm using device DPI.
     *  Called by A's tracking layer on every scroll event. */
    fun pxToCm(deltaY: Int, ydpi: Float): Float {
        val cmPerPx = 2.54f / ydpi
        return Math.abs(deltaY) * cmPerPx
    }

    /** Converts cm to km. */
    fun cmToKm(cm: Float): Float = cm / ScrollaConstants.CM_PER_KM

    /** Formats a km value for display with its unit. Returns "2.3 km", "0.8 km", "12.1 km".
     *  Use this wherever the number and the unit sit in the same run of text. */
    fun formatKm(km: Float): String = String.format(Locale.US, "%.1f km", km)

    /** The same number without the unit: "2.3", "0.8", "12.1".
     *
     *  The redesigned screens set the figure and the unit as two separate `Text`
     *  composables in different type styles (a serif figure beside a sans unit),
     *  so those sites cannot use [formatKm] — it would pull "km" into the figure's
     *  typeface and baseline. They use this and render the unit themselves.
     *  Precision is identical to [formatKm], so the two never disagree. */
    fun formatKmValue(km: Float): String = String.format(Locale.US, "%.1f", km)

    /**
     * True when a distance reads better in metres than in kilometres.
     *
     * Measured on-device, a full day of heavy scrolling is roughly 0.1–0.5 km
     * (Person A's S0.7 test: ~24 m per 5 minutes of continuous scrolling), so a
     * km figure to one decimal shows "0.0" for most of every day. Below a
     * rounded 1000 m the number belongs in metres.
     */
    private fun usesMetres(km: Float): Boolean = (km * 1000f) < 999.5f

    /**
     * Sub-metre distances rendered as "0 m", which is a confident zero for a
     * value that is not zero. Found 2026-08-26 in a real data export: Telegram
     * at 0.4 m and the system launcher at 0.3 m both displayed "0 m" on App
     * Breakdown, indistinguishable from an app that had never been scrolled at
     * all.
     *
     * 0.5 is the threshold because `%.0f` rounds half-up, so that is exactly
     * the point below which the old format produced a zero.
     */
    private const val SUB_METRE_THRESHOLD_M = 0.5f

    /** The figure alone, in whichever unit suits it: "176", "1.2" or "<1". */
    fun formatDisplayValue(km: Float): String = if (usesMetres(km)) {
        val metres = km * 1000f
        if (metres > 0f && metres < SUB_METRE_THRESHOLD_M) "<1"
        else String.format(Locale.US, "%.0f", metres)
    } else {
        String.format(Locale.US, "%.1f", km)
    }

    /** The unit that belongs with [formatDisplayValue]: "m" or "km". */
    fun formatDisplayUnit(km: Float): String = if (usesMetres(km)) "m" else "km"

    /** Figure and unit together, for a single run of text: "176 m", "1.2 km". */
    fun formatDistance(km: Float): String =
        "${formatDisplayValue(km)} ${formatDisplayUnit(km)}"

    /** Spelled out for screen readers: "176 metres", "1.2 kilometres". */
    fun formatDistanceSpoken(km: Float): String {
        val unit = if (usesMetres(km)) "metres" else "kilometres"
        // TalkBack would otherwise read "<1 metres", which is neither a word
        // nor grammatical. The spoken form spells the comparison out.
        val value = formatDisplayValue(km)
        if (value == "<1") return "less than one metre"
        return "$value $unit"
    }

    /** Finds the nearest landmark match for a given km value.
     *  Returns a pair: (landmark name, exact landmark km) for display on Home and the recap card.
     *  Returns null if km is 0f or negative.
     *
     *  **Prefer [landmarkComparison] for anything user-facing.** This returns the
     *  closest entry however absurdly far off it is, which is only safe once the
     *  caller has applied the honesty check. */
    fun nearestLandmark(km: Float): Pair<String, Float>? {
        if (km <= 0f) return null
        return LANDMARKS.minByOrNull { Math.abs(it.km - km) }?.let { it.label to it.km }
    }

    /**
     * The landmark to compare this distance against, or null when no honest
     * comparison exists.
     *
     * [nearestLandmark] returns the closest entry by contract, however far off
     * it is — so ten centimetres of scrolling comes back as "Eiffel Tower
     * height", which is the nearest landmark and a ludicrous claim. A
     * comparison is only made when the figure is genuinely in that landmark's
     * neighbourhood; early in the day the honest answer is no comparison.
     *
     * **This lives here because it had already been written twice.** The rule
     * was a private function in `HomeViewModel` and again in
     * `WeeklyRecapViewModel`, and when the home-screen widget became the third
     * consumer it called `nearestLandmark()` directly and lost the guard
     * entirely — shipping a widget that would tell someone ten metres of
     * scrolling was the Eiffel Tower. A rule that three callers need is not a
     * ViewModel's private business.
     *
     * Returns the **short label**, which is written to follow "≈ " — where
     * dropping articles is the convention and "≈ Eiffel Tower height" reads
     * correctly. For a lead-in that needs a grammatical sentence, use
     * [landmarkPhrase].
     */
    fun landmarkComparison(
        km: Float,
        minRatio: Float = MIN_LANDMARK_RATIO,
        maxRatio: Float = MAX_LANDMARK_RATIO
    ): String? = honestLandmark(km, minRatio, maxRatio)?.label

    /**
     * The same comparison, written to follow "that's about …".
     *
     * **Two forms, because one string cannot serve both lead-ins.** The labels
     * are written for "≈ ", where articles are dropped by convention. Dropped
     * into a sentence they produce "that's about 1 km walk" and "that's about
     * Eiffel Tower height" — which is what the weekly recap shared to WhatsApp
     * on 2026-09-15, in an image, where it outlives any fix.
     *
     * This is the third time this exact seam has bitten: the widget once
     * rendered "about the height of Eiffel Tower height" by pairing a lead-in
     * with the wrong form. The guard stayed in one place and the *wording*
     * quietly forked instead. So both forms live on the landmark itself, and
     * neither is derived from the other.
     *
     * The design system sets the shape: "about the height of three Burj
     * Khalifas" — the landmark line carries its own articles.
     */
    fun landmarkPhrase(
        km: Float,
        minRatio: Float = MIN_LANDMARK_RATIO,
        maxRatio: Float = MAX_LANDMARK_RATIO
    ): String? = honestLandmark(km, minRatio, maxRatio)?.phrase

    /**
     * The honesty guard, in one place. Both public forms go through here so a
     * new rendering cannot arrive with its own idea of when a comparison is
     * true — the failure that shipped a widget claiming ten metres was the
     * Eiffel Tower.
     */
    private fun honestLandmark(km: Float, minRatio: Float, maxRatio: Float): Landmark? {
        val landmark = LANDMARKS.minByOrNull { Math.abs(it.km - km) } ?: return null
        if (km <= 0f || landmark.km <= 0f) return null
        val ratio = km / landmark.km
        return if (ratio in minRatio..maxRatio) landmark else null
    }

    /**
     * @param label follows "≈ " — articles dropped, as a caption sets them.
     * @param phrase follows "that's about " — a grammatical fragment.
     */
    data class Landmark(val km: Float, val label: String, val phrase: String)

    /** Below half a landmark, or above double it, a comparison is not honest. */
    const val MIN_LANDMARK_RATIO = 0.5f
    const val MAX_LANDMARK_RATIO = 2.0f

    private val LANDMARKS = listOf(
        Landmark(0.163f, "Eiffel Tower height", "the height of the Eiffel Tower"),
        Landmark(0.443f, "Empire State Building height", "the height of the Empire State Building"),
        Landmark(0.830f, "Burj Khalifa height", "the height of the Burj Khalifa"),
        Landmark(1.0f, "1 km walk", "a one-kilometre walk"),
        Landmark(1.66f, "height of 2 Burj Khalifas", "the height of two Burj Khalifas"),
        Landmark(3.8f, "Everest base camp to summit", "Everest base camp to the summit"),
        Landmark(8.8f, "full height of Mt. Everest", "the full height of Mount Everest"),
        Landmark(42.2f, "marathon distance", "a marathon"),
        Landmark(400.0f, "approx. ISS orbit altitude", "the altitude the ISS orbits at")
    )
}
