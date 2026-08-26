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
     *  Returns null if km is 0f or negative. */
    fun nearestLandmark(km: Float): Pair<String, Float>? {
        if (km <= 0f) return null
        return LANDMARKS.minByOrNull { Math.abs(it.second - km) }
    }

    private val LANDMARKS = listOf(
        Pair("Eiffel Tower height", 0.163f),
        Pair("Empire State Building height", 0.443f),
        Pair("Burj Khalifa height", 0.830f),
        Pair("1 km walk", 1.0f),
        Pair("height of 2 Burj Khalifas", 1.66f),
        Pair("Everest base camp to summit", 3.8f),
        Pair("full height of Mt. Everest", 8.8f),
        Pair("marathon distance", 42.2f),
        Pair("approx. ISS orbit altitude", 400.0f)
    )
}
