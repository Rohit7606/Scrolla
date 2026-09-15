package com.scrolla.firestore

import android.os.Build
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.scrolla.BuildConfig
import com.scrolla.diagnostics.CrashReporter
import com.scrolla.room.ServiceHealthState
import com.scrolla.ui.screens.TrackingGap
import kotlinx.coroutines.tasks.await
import java.time.ZoneId

/**
 * Makes one person's tracking outage visible to somebody who can act on it.
 *
 * **The gap this closes.** [TrackingGap] already detects the outage that has no
 * other symptom — the accessibility switch is on, the process was killed, and
 * nothing has been recorded since — and it has always been correct. It drove an
 * in-app card and stopped there. Service health was written to Room and **never
 * left the device**: not to Firestore, not to Crashlytics. So an OEM killing the
 * service for a tester produced an app that opens fine, shows a number, and
 * quietly stops measuring, and the only person who could ever find out was the
 * one least equipped to — they would simply drift away.
 *
 * Crashlytics catches crashes. This is not a crash. That is the whole problem.
 *
 * **Two outputs, because they answer different questions.**
 *
 *  - A document per user says *who* is dark and since when, precisely. Read it
 *    in the Firestore console.
 *  - A Crashlytics non-fatal says *how often and on what*. Crashlytics groups
 *    by device model and Android version for free, which is the OEM breakdown
 *    you would otherwise have to build — and OEM process-killing is the single
 *    most likely way this product fails at forty people.
 *
 * **Reported from the sync pass, not from the UI.** The in-app card can only
 * appear to someone who opened the app, and the person whose tracking died is
 * exactly the person with no reason to open it. `SyncWorker` runs hourly
 * regardless, so this fires for someone who has not launched Scrolla in a week —
 * which is the case that matters.
 *
 * Everything here is disclosed on the privacy screen. Adding a field to what
 * this app uploads without saying so would be the same failure as the tracking
 * itself: true, invisible, and nobody's choice but ours.
 */
class TrackingTelemetry(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * The `lastEventTimestamp` of the outage already reported in this process.
     *
     * **This is the whole dedupe and it needs no storage.** While a gap
     * persists, nothing is being recorded, so `lastEventTimestamp` cannot move —
     * one outage is one value and reports once. When tracking resumes the value
     * advances, so a *later* outage is a different value and reports again. A
     * timestamp-keyed check is exact where a "have I reported recently" timer
     * would be a guess.
     */
    private var reportedOutageAt: Long? = null

    suspend fun report(health: ServiceHealthState?, userId: String?, now: Long) {
        if (health == null || userId == null) return

        val gapHours = if (
            TrackingGap.isSuspicious(
                health.isAccessibilityServiceEnabled,
                health.lastEventTimestamp,
                now
            )
        ) {
            TrackingGap.wakingHoursSince(health.lastEventTimestamp, now, ZoneId.systemDefault())
        } else {
            null
        }

        writeSnapshot(health, userId, gapHours)

        if (gapHours != null && shouldReport(health.lastEventTimestamp, reportedOutageAt)) {
            reportedOutageAt = health.lastEventTimestamp
            CrashReporter.nonFatal(
                CrashReporter.Where.TRACKING_SILENT,
                // A synthetic throwable because Crashlytics groups by stack, and
                // the message carries the only number worth grouping on. This is
                // telemetry wearing an exception's clothes, which is a trade
                // made knowingly: the alternative is building a second reporting
                // pipeline for one signal.
                IllegalStateException(
                    "Tracking silent for $gapHours waking hours " +
                        "(accessibility on, no events since ${health.lastEventTimestamp})"
                )
            )
            Log.w(TAG, "Reported silent tracking: $gapHours waking hours")
        }
    }

    private suspend fun writeSnapshot(
        health: ServiceHealthState,
        userId: String,
        gapHours: Double?
    ) {
        try {
            firestore.collection("users").document(userId).set(
                mapOf(
                    "tracking" to mapOf(
                        "accessibilityEnabled" to health.isAccessibilityServiceEnabled,
                        "serviceRunning" to health.isServiceRunning,
                        "lastEventAt" to health.lastEventTimestamp,
                        "silentWakingHours" to gapHours,
                        "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "androidSdk" to Build.VERSION.SDK_INT,
                        "appVersion" to BuildConfig.VERSION_NAME,
                        "reportedAt" to FieldValue.serverTimestamp()
                    )
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            // The success path logs too. Only the failure did, which means a
            // silent no-op and a working write were indistinguishable from a
            // device — and this is the feature whose entire purpose is to stop
            // things being invisible.
            Log.d(
                TAG,
                "Tracking snapshot written: accessibility=${health.isAccessibilityServiceEnabled} " +
                    "running=${health.isServiceRunning} lastEventAt=${health.lastEventTimestamp} " +
                    "silentWakingHours=$gapHours"
            )
        } catch (e: Exception) {
            // Never costs the sync. This is diagnostics about the product, not
            // the user's data, and it must not be the reason a total fails to
            // upload.
            Log.w(TAG, "Could not write tracking snapshot: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "TrackingTelemetry"

        /**
         * True when this outage has not already been reported.
         *
         * Pure, so the one rule that decides whether a user's Crashlytics
         * dashboard fills with twenty-four copies of the same silent day can be
         * asserted on rather than observed.
         */
        fun shouldReport(lastEventAt: Long, alreadyReportedFor: Long?): Boolean =
            lastEventAt != alreadyReportedFor
    }
}
