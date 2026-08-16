package com.scrolla.ui.screens

/**
 * Shared formatting utilities for Scrolla screens.
 *
 * These pure functions format data for display. They own presentation logic only —
 * never business logic or data transformation.
 */
object ScrollaFormatters {

    /**
     * Format distance to one decimal place.
     * â‰¥ 10 km → no decimal (e.g. "12")
     * < 10 km → one decimal (e.g. "2.3")
     */
    fun formatDistance(km: Float): String {
        return if (km >= 10f) {
            "%.0f".format(km)
        } else {
            "%.1f".format(km)
        }
    }

    /**
     * Format rank position as English ordinal: 1st, 2nd, 3rd, 4th, etc.
     *
     * Per Typography.md Â§2.4: ordinal suffix is same size (no superscript).
     * This ensures screen readers correctly read "2nd" not "2" + "nd" separately.
     */
    fun formatOrdinal(position: Int): String {
        val suffix = when {
            position % 100 in 11..13 -> "th"
            position % 10 == 1 -> "st"
            position % 10 == 2 -> "nd"
            position % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$position$suffix"
    }
}

