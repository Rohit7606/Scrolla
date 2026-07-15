package com.scrolla.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.scrolla.model.DistanceFormatter
import com.scrolla.model.ScrollaConstants
import com.scrolla.room.ScrollEvent
import com.scrolla.room.ScrollaDatabase
import java.util.HashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

class ScrollAccessibilityService : AccessibilityService() {

    // S0.4: Per-view HashMap for delta tracking. Key = "packageName:className:viewId"
    // No global lastScrollY — tracking is strictly per-view to prevent cross-app phantom distance.
    // Assumption: AccessibilityService callbacks run on a single background thread by default,
    // so no explicit synchronization is required. This is safe per Android docs.
    private val lastKnownScrollY = HashMap<String, Int>()

    // S1.A3: In-memory batch buffer for accumulating scroll distance
    private val batchBuffer = HashMap<String, Float>() // key: "day:appPackage:hourBucket", value: accumulated scrollCm
    private var eventCountSinceFlush = 0
    private var lastFlushTimestamp = 0L
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

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

        // ----- S1.A3: Batch accumulator flush logic -----
        // Accumulate scrollCm into batch buffer keyed by (day, appPackage, hourBucket)
        val day = LocalDate.now().toString()
        val hourBucket = java.time.LocalTime.now().hour
        val batchKey = "$day:$pkg:$hourBucket"
        batchBuffer[batchKey] = (batchBuffer[batchKey] ?: 0f) + deltaCm

        eventCountSinceFlush++

        // Check flush condition: event count OR elapsed time
        val currentTime = System.currentTimeMillis()
        val timeSinceLastFlush = currentTime - lastFlushTimestamp
        val shouldFlushByCount = eventCountSinceFlush >= ScrollaConstants.BATCH_FLUSH_EVENT_COUNT
        val shouldFlushByTime = timeSinceLastFlush >= ScrollaConstants.BATCH_FLUSH_INTERVAL_MS

        if (shouldFlushByCount || shouldFlushByTime) {
            // Take snapshot and clear buffer synchronously BEFORE launching coroutine
            val snapshot = HashMap(batchBuffer)
            batchBuffer.clear()
            flushBatch(snapshot)
            eventCountSinceFlush = 0
            lastFlushTimestamp = currentTime
        }
    }

    private fun flushBatch(snapshot: HashMap<String, Float>) {
        serviceScope.launch {
            try {
                val db = ScrollaDatabase.getDatabase(applicationContext)
                val timestamp = System.currentTimeMillis()

                for ((key, scrollCm) in snapshot) {
                    if (scrollCm > 0f) {
                        val parts = key.split(":")
                        if (parts.size == 3) {
                            val day = parts[0]
                            val appPackage = parts[1]
                            val hourBucket = parts[2].toInt()
                            val event = ScrollEvent(
                                day = day,
                                appPackage = appPackage,
                                scrollCm = scrollCm,
                                hourBucket = hourBucket,
                                timestamp = timestamp
                            )
                            db.scrollEventDao().insert(event)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BatchFlush", "Batch flush failed", e)
            }
        }
    }

    override fun onInterrupt() {
        // No-op for now
    }

    companion object {
        private const val TAG = "ScrollAccessibilityService"
    }
}