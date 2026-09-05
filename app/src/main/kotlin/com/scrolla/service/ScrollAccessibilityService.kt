package com.scrolla.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.scrolla.model.DistanceFormatter
import com.scrolla.model.ScrollaConstants
import com.scrolla.room.DailyTotal
import com.scrolla.room.ScrollEvent
import com.scrolla.room.ScrollaDatabase
import com.scrolla.room.ServiceHealthState
import java.util.HashMap
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class ScrollAccessibilityService : AccessibilityService() {

    // S0.4: Per-view HashMap for delta tracking. Key = "packageName:className:viewId"
    private val lastKnownScrollY = HashMap<String, Int>()

    // S1.A3: In-memory batch buffer for accumulating scroll distance
    private val batchBuffer = HashMap<String, Float>() // key: "day:appPackage:hourBucket", value: accumulated scrollCm
    private var eventCountSinceFlush = 0
    private var lastFlushTimestamp = 0L
    /**
     * Keeps a failed background write from killing tracking.
     *
     * The scope used to be `Dispatchers.IO + Job()` with no handler. In Android
     * an uncaught exception in a `launch` block reaches the thread's default
     * uncaught-exception handler, which kills the **process** — and killing this
     * process kills the accessibility binding, so the framework marks the
     * service "malfunctioning", switches the accessibility master switch off,
     * and tracking stops while the toggle in Settings still reads on. That is
     * the 2026-08-25 outage and the 2026-09-04 one: nine and a half hours, then
     * eighteen hours, of silence from a background write nobody could see fail.
     *
     * A plain `Job()` made it worse: one child failing cancels every sibling, so
     * even surviving the crash would have left the scope dead and every later
     * flush a no-op. `SupervisorJob` isolates failures to the coroutine that
     * had them.
     *
     * A dropped Room write costs one batch of scroll distance. Losing the
     * service costs every batch until a human notices, so this trade is not
     * close.
     */
    private val crashGuard = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught exception in a service coroutine — tracking kept alive", throwable)
        // Best-effort, and must never itself throw: this is the last line before
        // the default handler that would kill the process.
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ScrollaDatabase.getDatabase(applicationContext)
                        .serviceHealthDao()
                        .markFlushFailed(
                            reason = "coroutine: ${throwable::class.simpleName}: ${throwable.message?.take(100)}",
                            eventTs = lastEventAt
                        )
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + crashGuard)

    // Fix 3: In-memory timestamp of the most recent scroll event. Stamped on
    // every call to onAccessibilityEvent(), persisted during flush. This is the
    // primary staleness signal — "last event was N hours ago" is the cheapest
    // liveness check and the one that would have caught the 2026-08-25 outage.
    @Volatile
    private var lastEventAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "ScrollAccessibilityService connected")

        // S1.A5: StartForeground as per AGENTS.md Section 4.1
        // 3-arg overload (API 29+) takes an integer type; matches manifest
        // android:foregroundServiceType="specialUse".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Guarded because this runs on the service's entry point: anything
            // thrown here propagates out of onServiceConnected and the framework
            // marks the service malfunctioning. startForeground can throw for
            // reasons outside our control — a foreground-start restriction, an
            // OEM notification policy, or a type the platform does not accept
            // (FOREGROUND_SERVICE_TYPE_SPECIAL_USE is an API 34 constant and this
            // device is API 33). Tracking without the foreground notification is
            // degraded; no tracking at all is the failure we are fixing.
            try {
                startForeground(
                    ScrollaConstants.FOREGROUND_NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (e: Exception) {
                Log.e(TAG, "startForeground failed — continuing without it", e)
            }
        }

        // Fix 2: Mark isServiceRunning = true at the point the service actually
        // knows it has connected, not at first flush. ensureRowExists is a no-op
        // if the row already exists (IGNORE strategy).
        serviceScope.launch {
            try {
                val db = ScrollaDatabase.getDatabase(applicationContext)
                db.serviceHealthDao().ensureRowExists(
                    ServiceHealthState(
                        id = 1,
                        isServiceRunning = false,
                        isAccessibilityServiceEnabled = true,
                        lastEventTimestamp = 0L,
                        lastRoomFlushTimestamp = 0L,
                        lastFirestoreSyncTimestamp = 0L,
                        degradedReason = null
                    )
                )
                db.serviceHealthDao().updateServiceRunning(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark service running on connect", e)
            }
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
        try {
            // S0.4: Per-view delta tracking with HashMap. No global lastScrollY.
            if (event == null) return
            if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return

            // Fix 3: Stamp every event in memory. Persisted to Room during flush
            // so lastEventTimestamp and lastRoomFlushTimestamp come from different
            // sources and diverge when events arrive but flushes fail.
            lastEventAt = System.currentTimeMillis()

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
        } catch (e: Exception) {
            // Fail loud internally per AGENTS.md §4.8: log and mark degraded without crashing the service
            Log.e(TAG, "Error processing accessibility event", e)
            serviceScope.launch {
                try {
                    val db = ScrollaDatabase.getDatabase(applicationContext)
                    db.serviceHealthDao().markFlushFailed(
                        reason = "onAccessibilityEvent: ${e::class.simpleName}: ${e.message?.take(100)}",
                        eventTs = lastEventAt
                    )
                } catch (_: Exception) {}
            }
        }
    }

    private fun flushBatch(snapshot: HashMap<String, Float>) {
        serviceScope.launch {
            // Capture the in-memory event timestamp before any DB work.
            val eventTs = lastEventAt
            try {
                // getDatabase() sat OUTSIDE this try until 2026-09-05, alone
                // among the five launch blocks in this file. It opens SQLite, so
                // it can throw on a locked or corrupt database, an I/O error, or
                // a failed migration — and this is the hot path, running every 50
                // events or 10 seconds while scrolling. Uncaught there, it killed
                // the process and took the accessibility binding with it.
                val db = ScrollaDatabase.getDatabase(applicationContext)
                val timestamp = System.currentTimeMillis()
                for ((key, scrollCm) in snapshot) {
                    if (scrollCm > 0f) {
                        val parts = key.split(":")
                        if (parts.size == 3) {
                            val day = parts[0]
                            val appPackage = parts[1]
                            val hourBucket = parts[2].toIntOrNull() ?: continue
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
                // S1.A10: Recompute daily_totals for every distinct day touched by
                // this flush. Usually one day, but a flush can span midnight and
                // touch two. Collect distinct day strings from the snapshot keys
                // (first ":"-delimited segment, same parsing as the insert loop),
                // then upsert a DailyTotal per day using the authoritative current
                // sum queried from the table — this runs AFTER the inserts above,
                // so getTotalCmForDay() reflects the true up-to-date total, not just
                // this batch's contribution. Falls through to the catch block below
                // like any other failure in this try.
                val distinctDays = snapshot.keys.mapNotNull { key ->
                    val parts = key.split(":")
                    if (parts.size == 3) parts[0] else null
                }.toSet()
                for (day in distinctDays) {
                    val totalCm = db.scrollEventDao().getTotalCmForDay(day) ?: 0f
                    val totalKm = DistanceFormatter.cmToKm(totalCm)
                    db.dailyTotalDao().upsert(
                        DailyTotal(
                            day = day,
                            totalCm = totalCm,
                            totalKm = totalKm,
                            lastUpdated = timestamp
                        )
                    )
                }
                // Fix 2: Targeted update — touches only lastRoomFlushTimestamp,
                // lastEventTimestamp, and degradedReason. Cannot clobber
                // isServiceRunning or any other field written by lifecycle callbacks.
                db.serviceHealthDao().markFlushSuccess(flushTs = timestamp, eventTs = eventTs)
                Log.d("BatchFlush", "Successfully flushed batch at $timestamp")
            } catch (e: Exception) {
                // S1.A6: Mark degraded on failure (fail loud internally, invisible to user).
                // Fix 2: Targeted update — still stamps lastEventTimestamp so staleness
                // detection knows events were arriving even though writes failed.
                Log.e("BatchFlush", "Batch flush failed", e)
                try {
                    // Re-acquired rather than reused: opening the database is now
                    // inside the try above, so if that is what failed there is no
                    // handle to reuse. If it fails again the inner catch takes it.
                    ScrollaDatabase.getDatabase(applicationContext)
                        .serviceHealthDao()
                        .markFlushFailed(
                            reason = "${e::class.simpleName}: ${e.message?.take(100)}",
                            eventTs = eventTs
                        )
                } catch (_: Exception) {
                    // If the DB itself is broken, we can't mark degraded either.
                }
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

    override fun onUnbind(intent: Intent?): Boolean {
        // Fix 2: Clean-shutdown signal. Android unbinds before destroy, and on
        // a crash the destroy callback may never fire. This is belt-and-suspenders
        // for clean shutdown — NOT a crash detector (Fix 1 and Fix 3 are).
        // Note: onUnbind may fire on ordinary rebinds on some OEMs, which writes
        // a transient isServiceRunning = false before onServiceConnected writes true back.
        triggerFlushIfNeeded()
        serviceScope.launch {
            try {
                val db = ScrollaDatabase.getDatabase(applicationContext)
                db.serviceHealthDao().updateServiceRunning(false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark service stopped on unbind", e)
            }
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        // Best-effort flush first, then mark stopped.
        triggerFlushIfNeeded()
        // Fix 2: Write isServiceRunning = false AFTER the final flush, so the
        // flush's markFlushSuccess cannot overwrite it (targeted updates touch
        // different columns, so this is safe even with the race).
        serviceScope.launch {
            try {
                val db = ScrollaDatabase.getDatabase(applicationContext)
                db.serviceHealthDao().updateServiceRunning(false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark service stopped on destroy", e)
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScrollAccessibilityService"
    }
}