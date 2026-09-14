package com.scrolla.diagnostics

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * The one place Scrolla talks to Crashlytics (BUILD_PLAN 1.2).
 *
 * Every failure this project has had was found with a USB cable and 16 MB of
 * logcat. That works while the only user is holding the phone. At fifty it
 * does not: a crash nobody can see is a user who silently stops opening the
 * app, and the first anyone hears of it is that they stopped.
 *
 * ## What is sent (BUILD_PLAN 1.2c)
 *
 * Sent:
 * - the stack trace of a caught exception, and its type;
 * - a [Where] label naming the operation that failed;
 * - the signed-in Firebase uid, and nothing else about the person;
 * - Crashlytics' own device fields — model, OS version, free RAM/disk.
 *
 * Never sent:
 * - **scroll distances**, per day or per app;
 * - **package names of the apps the user scrolls** — the single most
 *   sensitive thing this app holds, since an AccessibilityService sees every
 *   app on the device;
 * - display name, email, phone number, group codes;
 * - anything read out of `scroll_events` or `daily_totals`.
 *
 * That is enforced by the shape of the API, not by remembering to be careful:
 * [Where] is an enum, so there is no `String` parameter on [nonFatal] for a
 * package name or a distance to be interpolated into. Adding a
 * `setCustomKey` call that carries user data would be a deliberate act, and
 * this KDoc is the thing to read before doing it.
 *
 * Breadcrumbs ([breadcrumb]) take a free-form message and are therefore the
 * one hole in that guarantee — they are attached to reports. Callers must pass
 * constants and counts, never values read out of the database. There is a
 * lint-by-reading rule here rather than a compiler-enforced one because a
 * breadcrumb with no detail is not worth attaching.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"

    /**
     * The operations worth a non-fatal report.
     *
     * An enum rather than a string for the privacy reason in the class KDoc,
     * and for a practical one: these become the grouping key in the Crashlytics
     * console, so they have to be a closed set or every report lands in its own
     * bucket and the console stops being readable.
     */
    enum class Where {
        // --- The flush path. Tracking is lost here, and only here. ---
        /** A whole batch of scroll events failed to reach Room. */
        BATCH_FLUSH,
        /** The flush failed, and so did recording that it failed. */
        BATCH_FLUSH_DEGRADED_MARK,

        // --- The sync path. The user vanishes from the leaderboard here. ---
        SYNC_FIRESTORE,
        SYNC_FIRESTORE_TIMEOUT,
        /** The background worker threw — the engine normally handles its own. */
        SYNC_BACKGROUND_WORKER,
        /** WorkManager refused to schedule, so there is no background sync. */
        SYNC_SCHEDULE_FAILED,

        // --- Room reads. Each one returns a default that looks like real data. ---
        READ_TODAY_TOTAL,
        READ_TODAY_TOP_APPS,
        READ_TODAY_PEAK_HOUR,
        READ_RECENT_DAILY_TOTALS,
        READ_PERSONAL_BEST,
        READ_ACTIVE_HOUR_SPAN,
        READ_TOTAL_FOR_DATE,
        OBSERVE_SERVICE_HEALTH,

        // --- Writes. These lose or fail to restore data. ---
        RESTORE_DAILY_TOTALS,
        CLEAR_APP_HISTORY,

        // --- Service lifecycle. ---
        SERVICE_MARK_STOPPED
    }

    /**
     * Null until [FirebaseCrashlytics] can be reached.
     *
     * Resolved lazily and defensively because this object is touched from
     * `ScrollRepository`, which is exercised by plain JVM unit tests where
     * Firebase does not exist. A diagnostics helper that crashes the thing it
     * is diagnosing — or fails 131 green tests — is worse than no diagnostics.
     */
    private val crashlytics: FirebaseCrashlytics? by lazy {
        try {
            FirebaseCrashlytics.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "Crashlytics unavailable — reports will be logged only", e)
            null
        }
    }

    /**
     * Attaches the Firebase uid, so reports from one struggling user can be
     * read together instead of as unrelated noise.
     *
     * The uid is explicitly the *only* identifier attached. Display name and
     * email are both to hand at every call site and neither goes in: the uid is
     * enough to find someone in the Firebase console when they ask for help,
     * and it means nothing to anyone reading a report without that access.
     *
     * Pass null on sign-out so a shared device does not file the next person's
     * failures under the last person's id.
     */
    fun setSignedInUser(uid: String?) {
        try {
            crashlytics?.setUserId(uid ?: "")
        } catch (e: Throwable) {
            Log.w(TAG, "Could not set Crashlytics user id", e)
        }
    }

    /**
     * A breadcrumb attached to any report filed afterwards.
     *
     * Constants and counts only — see the class KDoc. "flush of 34 events" is
     * fine; the package names in that flush are not.
     */
    fun breadcrumb(message: String) {
        try {
            crashlytics?.log(message)
        } catch (e: Throwable) {
            Log.w(TAG, "Could not log Crashlytics breadcrumb", e)
        }
    }

    /**
     * Reports a caught exception that the app recovered from.
     *
     * These are the ones that matter here. Scrolla barely crashes; what it does
     * is *degrade quietly* — a read returns 0 km and the screen renders a
     * confident zero, a flush fails and a day's tracking is gone with a Log.e
     * nobody sees. Every one of those catches was written to keep the app
     * standing, which is right, and every one of them makes the failure
     * invisible, which is the problem this closes.
     *
     * Never throws. A diagnostics call that can take down the caller would make
     * the catch blocks it lives in unsafe.
     */
    fun nonFatal(where: Where, e: Throwable) {
        try {
            crashlytics?.apply {
                setCustomKey(KEY_WHERE, where.name)
                recordException(e)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not record non-fatal for $where", t)
        }
    }

    /** Grouping key in the console. Value is always a [Where] name. */
    private const val KEY_WHERE = "scrolla_where"
}
