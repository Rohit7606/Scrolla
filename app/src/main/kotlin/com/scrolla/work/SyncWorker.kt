package com.scrolla.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.scrolla.diagnostics.CrashReporter
import com.scrolla.model.ScrollaConstants
import com.scrolla.ui.ScrollaGraph
import java.util.concurrent.TimeUnit

/**
 * Syncs while the app is closed (BUILD_PLAN 1.1).
 *
 * The gap this fills: `triggerFirestoreSync()` was reachable only from
 * `SyncViewModel`, which lives only while the shell is composed. **A user who
 * tracked all day and never opened Scrolla never appeared on their group's
 * leaderboard that day** — `getGroupLeaderboard` filters on `date == today` and
 * they had no document for today. With fifty people the board showed whoever
 * happened to open the app, which makes the thing you open the app to look at
 * broken by not opening the app.
 *
 * Runs the same [com.scrolla.firestore.SyncEngine] the UI runs, from the same
 * single instance, so a background pass and a foreground pass cannot do the
 * work twice.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // ScrollaGraph.init() runs in ScrollaApplication.onCreate(), which
            // Android guarantees before any worker in this process.
            val ran = ScrollaGraph.syncEngine.sync(reason = "background")

            // Both outcomes are ordinary and both are success. "Dropped as
            // redundant" means the app was open and had just synced, which is
            // the mechanism working, not a failure to retry.
            Log.d(TAG, if (ran) "Background sync ran" else "Background sync dropped — a pass had just run")
            Result.success()
        } catch (e: Exception) {
            // The engine does not normally throw: ScrollRepository catches its
            // own Firestore failures and records them, so an exception here is
            // something unanticipated rather than the ordinary offline case.
            // Worth a report for exactly that reason.
            Log.e(TAG, "Background sync failed", e)
            CrashReporter.nonFatal(CrashReporter.Where.SYNC_BACKGROUND_WORKER, e)

            // Retry rather than success: an unexpected failure is the one case
            // where waiting a full hour for the next tick is worth avoiding.
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"

        /**
         * Unique name, so [schedule] can be called on every launch without
         * stacking up a new worker each time.
         */
        const val WORK_NAME = "scrolla-background-sync"

        /**
         * Idempotent — safe to call on every `Application.onCreate()`, which is
         * how `TrackingHealthWatcher.schedule()` already behaves.
         *
         * [ExistingPeriodicWorkPolicy.KEEP] rather than UPDATE: replacing the
         * request resets its interval, so on a phone that launches the app
         * often, CANCEL_AND_REENQUEUE would push the next run an hour into the
         * future every single time and the worker would never fire at all.
         * That failure would be completely silent, which is the kind this
         * project keeps paying for.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                // Without this the worker wakes with no network and burns the
                // full 15-second Firestore timeout doing nothing — measured
                // three times on 2026-09-14 during the aeroplane-mode test.
                .setRequiredNetworkType(NetworkType.CONNECTED)
                // Deliberately NOT requiresBatteryNotLow. A sync is a handful
                // of small writes, and gating it on battery would drop someone
                // off their group's board for the whole evening precisely when
                // their phone is low — an absence they could not explain and we
                // could not either.
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                ScrollaConstants.BACKGROUND_SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Background sync scheduled every ${ScrollaConstants.BACKGROUND_SYNC_INTERVAL_MINUTES} min")
        }
    }
}
