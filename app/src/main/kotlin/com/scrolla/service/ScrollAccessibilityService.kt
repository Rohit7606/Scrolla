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

    // S0.4: Per-view baselines for delta tracking. Key = "packageName:className:viewId"
    //
    // A9 (AUDIT_A_TRACK.md): this was a plain HashMap that was added to on every
    // event and never pruned, so it grew for the entire life of the process —
    // and this process is meant to live for weeks. An LRU bounds it.
    //
    // Eviction is cheap by construction: a key that comes back after being
    // evicted is simply treated as a view seen for the first time, which
    // establishes a baseline and contributes zero distance for that one event.
    // So the worst case is losing a single delta on a view the user has not
    // touched in a long time, which is well under the noise floor.
    private val lastKnownScrollY = object : LinkedHashMap<String, Int>(
        /* initialCapacity = */ 128,
        /* loadFactor = */ 0.75f,
        /* accessOrder = */ true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean =
            size > MAX_TRACKED_VIEWS
    }

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
        // Guarded because this runs on the service's entry point: anything
        // thrown here propagates out of onServiceConnected and the framework
        // marks the service malfunctioning. startForeground can throw for
        // reasons outside our control — a foreground-start restriction, an
        // OEM notification policy, or a type the platform does not accept
        // (FOREGROUND_SERVICE_TYPE_SPECIAL_USE is an API 34 constant and this
        // device is API 33). Tracking without the foreground notification is
        // degraded; no tracking at all is the failure we are fixing.
        //
        // A7 (AUDIT_A_TRACK.md): this whole block used to be inside
        // `if (SDK_INT >= R)`, so on API 24–29 — which minSdk = 24 admits —
        // startForeground was never called at all. No persistent notification,
        // no foreground priority, and an ordinary background process for the OS
        // to reclaim early, on exactly the older low-memory devices where that
        // happens soonest. Scrolla would install, look fine, and track almost
        // nothing, with no notification to hint otherwise. Given that MIUI
        // killing a *foreground* service is already the project's main outage
        // cause, shipping no foreground service at all was strictly worse.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // 3-arg overload takes the type integer, matching the manifest's
                // android:foregroundServiceType="specialUse".
                startForeground(
                    ScrollaConstants.FOREGROUND_NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                // API 24–29: the 2-arg overload. There is no service-type
                // argument on these platforms and none is required — the
                // manifest attribute is simply ignored below API 29.
                // buildNotification() already guards channel creation on O, so
                // this is correct on 24–25 (no channels) too.
                startForeground(
                    ScrollaConstants.FOREGROUND_NOTIFICATION_ID,
                    buildNotification()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed — continuing without it", e)
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

            // ----- S0.4 delta computation -----
            // The arithmetic lives in ScrollDelta so it is unit-testable; see
            // that file for why (A1 shipped broken because the only evidence
            // ever checked was a log line, which a pure function makes
            // unnecessary). Instagram and friends report scrollDeltaY while
            // scrollY stays 0, which is the fallback path in there.
            val result = ScrollDelta.compute(
                scrollY = scrollY,
                lastKnownY = lastKnownScrollY[compositeKey],
                scrollDeltaY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    event.scrollDeltaY
                } else {
                    null
                }
            )

            result.newBaseline?.let { lastKnownScrollY[compositeKey] = it }

            if (result.wasRecycleReset) {
                val discarded = DistanceFormatter.pxToCm(result.rawDelta, resources.displayMetrics.ydpi)
                Log.d(TAG, "pkg=$pkg RESET DETECTED delta=${result.rawDelta} discardedCm=$discarded scrollY=$scrollY key=$compositeKey")
            }

            val delta: Int = result.delta

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

        /**
         * Upper bound on tracked per-view baselines (A9).
         *
         * Sized to be far above what a real session reaches — a heavy day on
         * the test device produced a few hundred distinct composite keys — so
         * eviction is a backstop against an unbounded process rather than
         * something that happens in normal use.
         */
        private const val MAX_TRACKED_VIEWS = 500
    }
}