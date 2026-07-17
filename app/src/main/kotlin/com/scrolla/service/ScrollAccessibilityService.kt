package com.scrolla.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.scrolla.model.DistanceFormatter
import com.scrolla.model.ScrollaConstants
import com.scrolla.room.ScrollEvent
import com.scrolla.room.ScrollaDatabase
import com.scrolla.room.ServiceHealthState
import java.util.HashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

class ScrollAccessibilityService : AccessibilityService() {

    // S0.4: Per-view HashMap for delta tracking. Key = "packageName:className:viewId"
    private val lastKnownScrollY = HashMap<String, Int>()

    // S1.A3: In-memory batch buffer for accumulating scroll distance
    private val batchBuffer = HashMap<String, Float>() // key: "day:appPackage:hourBucket", value: accumulated scrollCm
    private var eventCountSinceFlush = 0
    private var lastFlushTimestamp = 0L
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "ScrollAccessibilityService connected")
        // S1.A5: StartForeground as per AGENTS.md Section 4.1
        // 3-arg overload (API 29+) takes an integer type; matches manifest
        // android:foregroundServiceType="dataSync".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                ScrollaConstants.FOREGROUND_NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        }
    }

    private fun buildNotification(): Notification {
        // Create notification channel if required
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                ScrollaConstants.NOTIFICATION_CHANNEL_ID,
                ScrollaConstants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Build notification
        return NotificationCompat.Builder(this, ScrollaConstants.NOTIFICATION_CHANNEL_ID)
            .setContentText(ScrollaConstants.NOTIFICATION_TEXT)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Placeholder icon
            .setOngoing(true) // Non-dismissible
            .build()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // S0.4: Per-view delta tracking with HashMap. No global lastScrollY.
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return

        val pkg = event.packageName?.toString() ?: "unknown"
        val scrollY = event.scrollY
        val className = event.className?.toString() ?: "unknown"

        // Source node is only used to read viewIdResourceName for the composite key.
        // canRetrieveWindowContent is false, so source is often null – fall back gracefully.
        // The node info is system-owned for the duration of this call; recycle it after reading.
        val viewId = event.source?.let { source ->
            val id = source.viewIdResourceName ?: "unknown"
            source.recycle()
            id
        } ?: "unknown"

        val compositeKey = "$pkg:$className:$viewId"

        // ----- S0.4 delta computation (three paths) -----
        // Branch 1: scrollY != 0 (normal delta path)
        val delta: Int = if (scrollY != 0) {
            // Compute per–view delta
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
            triggerFlushIfNeeded()
        }
    }

    private fun flushBatch(snapshot: HashMap<String, Float>) {
        serviceScope.launch {
            // S1.A6: Fetch state once before try/catch for both branches
            val db = ScrollaDatabase.getDatabase(applicationContext)
            val currentState = db.serviceHealthDao().getOnce() ?: ServiceHealthState(
                id = 1,
                isServiceRunning = true,
                lastEventTimestamp = 0L,
                lastRoomFlushTimestamp = 0L,
                lastFirestoreSyncTimestamp = 0L,
                degradedReason = null
            )
            try {
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
                // S1.A6: Mark service as healthy on successful flush
                val updatedState = currentState.copy(
                    isServiceRunning = true,
                    lastRoomFlushTimestamp = timestamp,
                    degradedReason = null
                )
                db.serviceHealthDao().upsert(updatedState)
                Log.d("BatchFlush", "Successfully flushed batch at $timestamp")
            } catch (e: Exception) {
                // S1.A6: Mark degraded on failure (fail loud internally, invisible to user)
                Log.e("BatchFlush", "Batch flush failed", e)
                val updatedState = currentState.copy(
                    degradedReason = "${e::class.simpleName}: ${e.message?.take(100)}"
                )
                db.serviceHealthDao().upsert(updatedState)
            }
        }
    }

    private fun triggerFlushIfNeeded() {
        if (batchBuffer.isEmpty()) return
        val snapshot = HashMap(batchBuffer)
        batchBuffer.clear()
        flushBatch(snapshot)
        eventCountSinceFlush = 0
        lastFlushTimestamp = System.currentTimeMillis()
    }

    override fun onInterrupt() {
        // Best-effort flush; may not complete before process death
        triggerFlushIfNeeded()
    }

    override fun onDestroy() {
        // Best-effort flush; may not complete before process death
        triggerFlushIfNeeded()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScrollAccessibilityService"
    }
}