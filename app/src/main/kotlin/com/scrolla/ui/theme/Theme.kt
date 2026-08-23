package com.scrolla.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Surface mapping, and why it is the way it is:
 *
 *   background / surface   the screen ground and the nav bar sit on it
 *   surfaceContainer       cards
 *   surfaceContainerHigh   raised things — selected rows, the self row
 *   surfaceVariant         inner blocks inside a card (tracks, wells)
 *
 * These were all the same value before, which is why nothing looked
 * like it was sitting on anything.
 */
private val LightColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = OnAccentLight,
    primaryContainer = AccentContainerLight,
    onPrimaryContainer = OnAccentContainerLight,

    secondary = TextMidLight,
    onSecondary = CardLight,
    secondaryContainer = RaisedLight,
    onSecondaryContainer = TextHiLight,

    background = GroundLight,
    onBackground = TextHiLight,

    surface = GroundLight,
    onSurface = TextHiLight,
    surfaceVariant = RaisedLight,
    onSurfaceVariant = TextMidLight,

    outline = HairlineStrongLight,
    outlineVariant = HairlineLight,

    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,

    surfaceContainerLowest = GroundLight,
    surfaceContainerLow = GroundLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = RaisedLight,
    surfaceContainerHighest = HairlineLight
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = OnAccentDark,
    primaryContainer = AccentContainerDark,
    onPrimaryContainer = OnAccentContainerDark,

    secondary = TextMidDark,
    onSecondary = GroundDark,
    secondaryContainer = RaisedDark,
    onSecondaryContainer = TextHiDark,

    background = GroundDark,
    onBackground = TextHiDark,

    surface = GroundDark,
    onSurface = TextHiDark,
    surfaceVariant = RaisedDark,
    onSurfaceVariant = TextMidDark,

    outline = HairlineStrongDark,
    outlineVariant = HairlineDark,

    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,

    surfaceContainerLowest = GroundDark,
    surfaceContainerLow = GroundDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = RaisedDark,
    surfaceContainerHighest = HairlineDark
)

@Composable
fun ScrollaUILabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val extendedColors = if (darkTheme) {
        ScrollaExtendedColors(
            textLow = TextLowDark,
            textFaint = TextFaintDark,
            cardBorder = HairlineDark,
            selfRow = SelfRowDark,
            improving = ImprovingDark,
            worsening = WorseningDark,
            success = SuccessDark,
            onSuccess = GroundDark,
            successContainer = SuccessContainerDark,
            onSuccessContainer = OnSuccessContainerDark,
            warning = WarningDark,
            onWarning = GroundDark,
            warningContainer = WarningContainerDark,
            onWarningContainer = OnWarningContainerDark,
            info = InfoDark,
            onInfo = GroundDark,
            infoContainer = InfoContainerDark,
            onInfoContainer = OnInfoContainerDark,
            pending = PendingDark,
            onPending = GroundDark,
            pendingContainer = PendingContainerDark,
            onPendingContainer = OnPendingContainerDark
        )
    } else {
        ScrollaExtendedColors(
            textLow = TextLowLight,
            textFaint = TextFaintLight,
            cardBorder = HairlineLight,
            selfRow = SelfRowLight,
            improving = ImprovingLight,
            worsening = WorseningLight,
            success = SuccessLight,
            onSuccess = CardLight,
            successContainer = SuccessContainerLight,
            onSuccessContainer = OnSuccessContainerLight,
            warning = WarningLight,
            onWarning = CardLight,
            warningContainer = WarningContainerLight,
            onWarningContainer = OnWarningContainerLight,
            info = InfoLight,
            onInfo = CardLight,
            infoContainer = InfoContainerLight,
            onInfoContainer = OnInfoContainerLight,
            pending = PendingLight,
            onPending = CardLight,
            pendingContainer = PendingContainerLight,
            onPendingContainer = OnPendingContainerLight
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var currentContext = view.context
            while (currentContext is android.content.ContextWrapper) {
                if (currentContext is Activity) break
                currentContext = currentContext.baseContext
            }

            val window = (currentContext as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides ScrollaSpacing(),
        LocalScrollaExtendedColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
