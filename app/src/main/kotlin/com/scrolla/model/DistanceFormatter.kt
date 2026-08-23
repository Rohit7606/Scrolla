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
