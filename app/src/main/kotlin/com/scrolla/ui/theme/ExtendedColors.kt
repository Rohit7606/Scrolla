package com.scrolla.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Everything Material's colour scheme has no slot for.
 *
 * `textLow` matters most: the 11sp section labels need a third text
 * level that still clears 4.5:1. Painting them in the accent — which is
 * what the app did on every screen — is what spent the coral budget ten
 * times over.
 */
data class ScrollaExtendedColors(
    // Third and fourth text levels
    val textLow: Color,
    val textFaint: Color,

    // Card anatomy — one border colour, one wash for "this is you"
    val cardBorder: Color,
    val selfRow: Color,

    // Direction. Only ever means up or down; never decorative.
    val improving: Color,
    val worsening: Color,

    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,

    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,

    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,

    val pending: Color,
    val onPending: Color,
    val pendingContainer: Color,
    val onPendingContainer: Color,
)

val LocalScrollaExtendedColors = staticCompositionLocalOf<ScrollaExtendedColors> {
    error("No ScrollaExtendedColors provided")
}

val MaterialTheme.scrollaColors: ScrollaExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalScrollaExtendedColors.current
