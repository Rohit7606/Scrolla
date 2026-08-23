package com.scrolla.ui.screens

/**
 * Shared formatting utilities for Scrolla screens.
 *
 * These pure functions format data for display. They own presentation logic only —
 * never business logic or data transformation.
 *
 * Distance formatting deliberately does not live here: it belongs to
 * `model.DistanceFormatter` per `DATA_CONTRACT.md` Section 5, so that both tracks
 * share one implementation. Use `DistanceFormatter.formatKm` / `formatKmValue`.
 */
object ScrollaFormatters {

    /**
     * Format rank position as English ordinal: 1st, 2nd, 3rd, 4th, etc.
     *
     * Per Typography.md §2.4: ordinal suffix is same size (no superscript).
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

