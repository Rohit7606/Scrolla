package com.scrolla.device

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.scrolla.service.ScrollAccessibilityService

/**
 * Returns true iff ScrollAccessibilityService is currently enabled AND the
 * global accessibility master switch is on.
 *
 * Both conditions are required:
 * - The enabled-services list can still contain a crashed service, so checking
 *   it alone returned true for 9.5 hours on 2026-08-25 while nothing was
 *   actually being delivered.
 * - Settings.Secure.ACCESSIBILITY_ENABLED reads 0 when the framework has
 *   switched accessibility off (e.g. because its only enabled service crashed).
 *
 * Pure/logic-only: performs the check and returns a result. It does NOT write
 * to Room; the caller is responsible for persisting the result into
 * ServiceHealthState.isAccessibilityServiceEnabled (see BootCompletedReceiver and
 * MainActivity). This keeps the function easily unit-testable per AGENTS.md 5.1.
 */
fun isScrollAccessibilityServiceEnabled(context: Context): Boolean {
    // 1. Check the global master switch first — if it's off, nothing is being
    //    delivered to any accessibility service, regardless of what the enabled list says.
    val masterSwitch = Settings.Secure.getInt(
        context.contentResolver,
        Settings.Secure.ACCESSIBILITY_ENABLED,
        0
    )
    if (masterSwitch == 0) return false

    // 2. Check that our specific service is in the enabled list.
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabled = manager.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    ) ?: return false

    // Compare via ComponentName rather than raw string — the abbreviated form
    // ("com.scrolla/.service.ScrollAccessibilityService") is not guaranteed
    // across OEMs; some flatten to the fully-qualified form.
    val target = ComponentName(context, ScrollAccessibilityService::class.java)
    return enabled.any { info ->
        ComponentName.unflattenFromString(info.id) == target
    }
}
