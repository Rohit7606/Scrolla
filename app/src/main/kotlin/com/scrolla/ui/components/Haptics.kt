package com.scrolla.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The app's two haptic weights (PREMIUM_CHECKLIST P3.2a).
 *
 * There were zero `performHapticFeedback` calls in the entire app. On Android
 * this is a large part of what "premium" physically means, and it costs one line
 * per surface — but only if it goes in the shared surfaces rather than at forty
 * call sites, which is why this is wired into [ScrollaPrimaryButton] and
 * `Modifier.bounceClick` instead of sprinkled through screens.
 *
 * **Platform constants, not Compose's `HapticFeedbackType`.** Compose exposes
 * only `LongPress` and `TextHandleMove`, and `TextHandleMove` maps to
 * `TEXT_HANDLE_MOVE`, which several OEMs treat as a no-op or render so faintly
 * it cannot be felt — it is meant for dragging a text selection handle, not for
 * a button. `CLOCK_TICK` is the light tick the platform actually uses for
 * pickers and is honoured far more widely.
 *
 * **This still respects the user.** `View.performHapticFeedback()` returns false
 * and does nothing when the system's touch-feedback setting is off, and that is
 * correct: an app that buzzes after someone has explicitly turned haptics off is
 * not premium, it is rude. If nothing is felt, check
 * `settings get system haptic_feedback_enabled` before suspecting this file —
 * on the Xiaomi test device it was 0 out of the box, which is what made these
 * look broken on 2026-08-26.
 */
object ScrollaHaptics {

    /** Anything you press to navigate or select. Frequent, so it stays light. */
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * Something that actually happened — a destructive action confirmed, a
     * record broken. Rare on purpose; a heavy buzz on every tap reads as a
     * broken phone rather than a premium one.
     */
    fun confirm(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}

/** Sugar for the common case inside a composable. */
@Composable
fun rememberTapHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { ScrollaHaptics.tap(view) } }
}

@Composable
fun rememberConfirmHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { ScrollaHaptics.confirm(view) } }
}
