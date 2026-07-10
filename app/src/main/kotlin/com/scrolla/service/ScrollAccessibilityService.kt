package com.scrolla.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
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

        // Lookup last scrollY for this composite key, compute delta
        val lastY = lastKnownScrollY[compositeKey]
        val delta = if (lastY != null) (scrollY - lastY) else 0

        // Update map with current scrollY for future events
        lastKnownScrollY[compositeKey] = scrollY

        Log.d(TAG, "pkg=$pkg delta=$delta scrollY=$scrollY key=$compositeKey")
    }

    override fun onInterrupt() {
        // No-op for now
    }

    companion object {
        private const val TAG = "ScrollAccessibilityService"
    }
}