package com.scrolla.device

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/**
 * Returns true iff ScrollAccessibilityService is currently enabled in the OS
 * accessibility settings — distinct from the service's own internal health.
 *
 * Pure/logic-only: performs the check and returns a result. It does NOT write
 * to Room; the caller is responsible for persisting the result into
 * ServiceHealthState.isAccessibilityServiceEnabled (see BootCompletedReceiver and
 * MainActivity). This keeps the function easily unit-testable per AGENTS.md 5.1.
 */
fun isScrollAccessibilityServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabled = manager.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    ) ?: return false
    val targetComponent = "com.scrolla/.service.ScrollAccessibilityService"
    return enabled.any { info ->
        info.id == targetComponent
    }
}
