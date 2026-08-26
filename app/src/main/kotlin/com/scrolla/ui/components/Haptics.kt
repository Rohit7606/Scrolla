package com.scrolla.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The app's two haptic weights (PREMIUM_CHECKLIST P3.2a).
 *
 * **Why this drives the vibrator directly rather than
 * `View.performHapticFeedback()`.** That API is gated on
 * `Settings.System.haptic_feedback_enabled`, which governs *touch feedback* —
 * keyboard taps, system UI presses. Plenty of people turn that off because
 * keyboard buzz annoys them, and they do not thereby mean "no app should ever
 * give me feedback". Apps that feel good on Android almost universally use the
 * vibrator channel, which that setting does not gate; the first version of this
 * file used `performHapticFeedback` and was silently a no-op on the test device
 * for exactly that reason.
 *
 * **The user still gets a switch — an honest one.** Bypassing a system setting
 * only to offer no alternative would be worse than respecting it, so haptics are
 * on by default and can be turned off in Settings ([isEnabled]/[setEnabled]).
 * That is the same bargain every well-behaved Android app makes.
 *
 * `createPredefined` still respects the system's *vibration intensity* setting
 * and Do Not Disturb, so the OS keeps the final say on strength.
 */
object ScrollaHaptics {

    private const val PREFS = "scrolla_prefs"
    private const val KEY_ENABLED = "haptics_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Anything you press to navigate or select. Frequent, so it stays light. */
    fun tap(context: Context) {
        vibrate(context, light = true)
    }

    /**
     * Something that actually happened — a destructive action confirmed, a
     * record broken. Rare on purpose; a heavy buzz on every tap reads as a
     * broken phone rather than a premium one.
     */
    fun confirm(context: Context) {
        vibrate(context, light = false)
    }

    private fun vibrate(context: Context, light: Boolean) {
        if (!isEnabled(context)) return
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = if (light) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK
            vibrator.vibrate(VibrationEffect.createPredefined(effect))
        } else {
            // Pre-Q has no predefined effects. These durations are deliberately
            // short: anything longer stops reading as a tick and starts reading
            // as a notification.
            val ms = if (light) 12L else 24L
            vibrator.vibrate(
                VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}

/** Sugar for the common case inside a composable. */
@Composable
fun rememberTapHaptic(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { { ScrollaHaptics.tap(context) } }
}

@Composable
fun rememberConfirmHaptic(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { { ScrollaHaptics.confirm(context) } }
}
