package com.scrolla.ui.screens

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.scrolla.device.isScrollAccessibilityServiceEnabled

/**
 * Whether Scrolla's accessibility service is switched on, **asked of the system
 * rather than of our own database**.
 *
 * The tracking-off banner used to read `ServiceHealthState.isAccessibilityServiceEnabled`
 * out of Room. That row is a cache, and on 2026-09-06 it was measured lying: after
 * an APK reinstall the device reported `accessibility_enabled = 0`, an empty
 * `enabled_accessibility_services` and `Bound services:{}`, while the row still
 * said `isServiceRunning = 1, isAccessibilityServiceEnabled = 1`.
 *
 * The reason is that a package replace kills the process outright — `onDestroy`
 * and `onUnbind` never run, so nothing writes `false` — and the only re-check
 * lived in `MainActivity.onCreate`/`onResume`. Until the next resume the app
 * believed tracking was healthy while nothing at all was being recorded.
 *
 * For a banner whose entire job is to say "you are not being tracked right now",
 * a cache that can be stale in the reassuring direction is the wrong source.
 * This asks the framework every time it could have changed:
 *
 *  - on composition, so a cold start is correct immediately;
 *  - on every `ON_RESUME`, which covers returning from the accessibility
 *    settings screen the banner itself sends people to;
 *  - and from [AccessibilityManager]'s own state-change callback, so a service
 *    switched off while Scrolla is open updates without a resume.
 *
 * Room stays the source for the Settings health card, which is a history of what
 * the tracker has been doing. This is about what is true now.
 */
@Composable
fun rememberAccessibilityEnabled(): State<Boolean> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val state = remember { mutableStateOf(isScrollAccessibilityServiceEnabled(context)) }
    val currentContext by rememberUpdatedState(context)

    DisposableEffect(lifecycleOwner, currentContext) {
        fun refresh() {
            state.value = isScrollAccessibilityServiceEnabled(currentContext)
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Fires when the accessibility master switch flips. Individual services
        // being toggled does not always reach this callback on every OEM, which
        // is why the resume check above is not redundant with it.
        val manager = currentContext
            .getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val listener = AccessibilityManager.AccessibilityStateChangeListener { refresh() }
        manager?.addAccessibilityStateChangeListener(listener)

        refresh()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            manager?.removeAccessibilityStateChangeListener(listener)
        }
    }

    return state
}
