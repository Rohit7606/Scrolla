package com.scrolla.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ScrollaExtendedColors(
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
