package com.scrolla.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Accent500,
    onPrimary = Neutral0,
    primaryContainer = Accent100, // or Accent200
    onPrimaryContainer = Accent900,
    
    secondary = Neutral600,
    onSecondary = Neutral0,
    secondaryContainer = Neutral100,
    onSecondaryContainer = Neutral900,

    background = Neutral50,
    onBackground = Neutral900,
    
    surface = Neutral50,
    onSurface = Neutral900,
    surfaceVariant = Neutral100,
    onSurfaceVariant = Neutral700,
    
    outline = Neutral300,
    outlineVariant = Neutral200,

    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,

    surfaceContainer = Neutral50,
    surfaceContainerHigh = Neutral100,
    surfaceContainerHighest = Neutral150
)

private val DarkColorScheme = darkColorScheme(
    primary = Accent500,
    onPrimary = Neutral0,
    primaryContainer = Accent800,
    onPrimaryContainer = Accent50,

    secondary = NeutralDark600,
    onSecondary = NeutralDark0,
    secondaryContainer = NeutralDark200,
    onSecondaryContainer = NeutralDark800,

    background = NeutralDark50,
    onBackground = NeutralDark900,
    
    surface = NeutralDark50,
    onSurface = NeutralDark900,
    surfaceVariant = NeutralDark100,
    onSurfaceVariant = NeutralDark700,
    
    outline = NeutralDark400,
    outlineVariant = NeutralDark300,

    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,

    surfaceContainer = NeutralDark50,
    surfaceContainerHigh = NeutralDark100,
    surfaceContainerHighest = SurfaceContainerHighestDark // mapped in Color.kt
)

@Composable
fun ScrollaUILabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val extendedColors = if (darkTheme) {
        ScrollaExtendedColors(
            success = SuccessDark,
            onSuccess = NeutralDark0,
            successContainer = SuccessContainerDark,
            onSuccessContainer = OnSuccessContainerDark,
            warning = WarningDark,
            onWarning = NeutralDark0,
            warningContainer = WarningContainerDark,
            onWarningContainer = OnWarningContainerDark,
            info = InfoDark,
            onInfo = NeutralDark0,
            infoContainer = InfoContainerDark,
            onInfoContainer = OnInfoContainerDark,
            pending = PendingDark,
            onPending = NeutralDark0,
            pendingContainer = PendingContainerDark,
            onPendingContainer = OnPendingContainerDark
        )
    } else {
        ScrollaExtendedColors(
            success = SuccessLight,
            onSuccess = Neutral0,
            successContainer = SuccessContainerLight,
            onSuccessContainer = OnSuccessContainerLight,
            warning = WarningLight,
            onWarning = Neutral0,
            warningContainer = WarningContainerLight,
            onWarningContainer = OnWarningContainerLight,
            info = InfoLight,
            onInfo = Neutral0,
            infoContainer = InfoContainerLight,
            onInfoContainer = OnInfoContainerLight,
            pending = PendingLight,
            onPending = Neutral0,
            pendingContainer = PendingContainerLight,
            onPendingContainer = OnPendingContainerLight
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            var currentContext = context
            while (currentContext is android.content.ContextWrapper) {
                if (currentContext is Activity) break
                currentContext = currentContext.baseContext
            }
            
            val window = (currentContext as? Activity)?.window
            if (window != null) {
                // Update system bar icon colors based on current theme
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