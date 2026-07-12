package com.scrolla.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.scrolla.model.DistanceFormatter
import com.scrolla.model.ScrollaConstants
import java.util.HashMap

class ScrollAccessibilityService : AccessibilityService() {

    // S0.4: Per-view HashMap for delta tracking. Key = "packageName:className:viewId"
    // No global lastScrollY — tracking is strictly per-view to prevent cross-app phantom distance.
    // Assumption: AccessibilityService callbacks run on a single background thread by default,
    // so no explicit synchronization is required. This is safe per Android docs.
    private val lastKnownScrollY = HashMap<String, Int>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "ScrollAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // S0.4: Per-view delta tracking with HashMap. No global lastScrollY.
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return

        val pkg = event.packageName?.toString() ?: "unknown"
        val scrollY = event.scrollY
        val className = event.className?.toString() ?: "unknown"

        // Source node is only used to read viewIdResourceName for the composite key.
        // canRetrieveWindowContent is false, so source is often null — fall back gracefully.
        // The node info is system-owned for the duration of this call; recycle it after reading.
        val viewId = event.source?.let { source ->
            val id = source.viewIdResourceName ?: "unknown"
            source.recycle()
            id
        } ?: "unknown"

        val compositeKey = "$pkg:$className:$viewId"

        // ----- S0.4 delta computation (three paths) -----
        // Branch 1: scrollY != 0  (normal delta path)
        val delta: Int = if (scrollY != 0) {
            // Compute per‑view delta
            val lastY = lastKnownScrollY[compositeKey]
            val computed = if (lastY != null) (scrollY - lastY) else 0

            // ----- RESET detection (inside this branch only) -----
            if (computed < -ScrollaConstants.RECYCLE_RESET_THRESHOLD_PX) {
                val cm = DistanceFormatter.pxToCm(computed, resources.displayMetrics.ydpi)
                Log.d(TAG, "pkg=$pkg RESET DETECTED delta=$computed deltaCm=$cm scrollY=$scrollY key=$compositeKey")
            }

            // Update the HashMap for the next event
            lastKnownScrollY[compositeKey] = scrollY
            computed
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && event.scrollDeltaY != -1
                && event.scrollDeltaY != 0) {
            // Instagram, Chrome, etc. report scrollDeltaY even when scrollY==0.
            // Use that value directly; do NOT update the HashMap here.
            event.scrollDeltaY
        } else {
            0
        }
        // -----------------------------------------------------

        // Convert delta to centimeters for logging (used for both reset and normal lines)
        val deltaCm = DistanceFormatter.pxToCm(delta, resources.displayMetrics.ydpi)

        // Log the event (reset detection already logged above if applicable)
        Log.d(TAG, "pkg=$pkg delta=$delta deltaCm=$deltaCm scrollY=$scrollY key=$compositeKey")
    }

    override fun onInterrupt() {
        // No-op for now
    }

    companion object {
        private const val TAG = "ScrollAccessibilityService"
    }
}