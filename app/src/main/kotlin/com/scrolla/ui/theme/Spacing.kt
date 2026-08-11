package com.scrolla.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ScrollaSpacing(
    val none: Dp = 0.dp,
    val extraExtraSmall: Dp = 4.dp, // xxs
    val extraSmall: Dp = 8.dp,      // xs
    val small: Dp = 12.dp,          // sm
    val medium: Dp = 16.dp,         // md
    val large: Dp = 24.dp,          // lg
    val extraLarge: Dp = 32.dp,     // xl
    val extraExtraLarge: Dp = 48.dp, // xxl
    val extraExtraExtraLarge: Dp = 64.dp // xxxl
)

val LocalSpacing = staticCompositionLocalOf { ScrollaSpacing() }

val MaterialTheme.spacing: ScrollaSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
