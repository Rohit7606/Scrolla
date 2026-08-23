package com.scrolla.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The generic t-shirt scale stays for the screens that have not been
 * reworked, but the redesigned screens use the four named values below
 * and nothing else. Card padding was 16, 24 and 40 in three different
 * places; naming the intent is what stops that recurring.
 */
data class ScrollaSpacing(
    val none: Dp = 0.dp,
    val extraExtraSmall: Dp = 4.dp,
    val extraSmall: Dp = 8.dp,
    val small: Dp = 12.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val extraExtraLarge: Dp = 48.dp,
    val extraExtraExtraLarge: Dp = 64.dp,

    /** Left and right inset for every screen. */
    val screenInset: Dp = 20.dp,
    /** Inside every card. One value, no exceptions. */
    val cardPadding: Dp = 20.dp,
    /** Between two cards in the same group. */
    val cardGap: Dp = 12.dp,
    /** Between one section of a screen and the next. */
    val sectionGap: Dp = 34.dp
)

val LocalSpacing = staticCompositionLocalOf { ScrollaSpacing() }

val MaterialTheme.spacing: ScrollaSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
