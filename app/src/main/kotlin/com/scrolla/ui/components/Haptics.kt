package com.scrolla.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The app's two haptic weights (PREMIUM_CHECKLIST P3.2a).
 *
 * There were zero `performHapticFeedback` calls in the entire app. On Android
 * this is a large part of what "premium" physically means, and it costs one line
 * per surface — but only if it goes in the shared surfaces rather than at forty
 * call sites, which is why this is wired into [ScrollaPrimaryButton] and
 * [bounceClick] instead of sprinkled through screens.
 *
 * Two weights, used consistently:
 *  - [tap] for anything you press to navigate or select. Frequent, so it must be
 *    light enough not to become noise.
 *  - [confirm] for something that actually happened — a destructive action
 *    confirmed, a record broken. Rare on purpose; a heavy buzz on every tap
 *    reads as a broken phone, not a premium one.
 */
object ScrollaHaptics {
    fun tap(haptics: HapticFeedback) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun confirm(haptics: HapticFeedback) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

/** Sugar for the common case inside a composable. */
@Composable
fun rememberTapHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return { ScrollaHaptics.tap(haptics) }
}

@Composable
fun rememberConfirmHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return { ScrollaHaptics.confirm(haptics) }
}
