package com.scrolla.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Keeps the home-screen widget's number current (BUILD_PLAN 3.1).
 *
 * **AlarmManager, not WorkManager**, and not the platform's own
 * `updatePeriodMillis` either. That is the decision recorded for S2.6 — "more
 * reliable on OEM devices" — and it is the mechanism `TrackingHealthWatcher`
 * has been using successfully on MIUI, which is the only OEM this project has
 * long-running evidence from.
 *
 * `updatePeriodMillis` was the obvious alternative and is the wrong one: its
 * floor is thirty minutes and the platform skips it in Doze, so the figure
 * would go stale overnight and still be stale first thing in the morning —
 * precisely when a home-screen number is looked at.
 *
 * Two triggers, for two different failures:
 *
 *  - **The tick** catches the day rolling over. At midnight today's total
 *    becomes yesterday's and the widget must return to zero on its own; nothing
 *    else would tell it.
 *  - **[ScrollaWidgetProvider.refreshAll]** after a batch flush catches the
 *    ordinary case, and is why the number moves while someone is actually
 *    scrolling rather than up to fifteen minutes later.
 *
 * Inexact and non-wakeup, exactly like `TrackingHealthWatcher`: this must never
 * wake a sleeping phone to redraw a number nobody is looking at. The next time
 * the device is awake is soon enough.
 */
object ScrollaWidgets {

    private const val TAG = "ScrollaWidgets"
    private const val REQUEST_CODE = 3001

    /**
     * Fifteen minutes. Matches the foreground sync cadence, which is the rate
     * the in-app number already moves at, so the widget cannot look stale
     * *relative to the app* — the comparison a user actually makes.
     */
    private val INTERVAL_MS = AlarmManager.INTERVAL_FIFTEEN_MINUTES

    /**
     * Idempotent — the same PendingIntent is reused, so calling this from
     * several entry points re-arms one alarm rather than stacking them. Same
     * contract as `TrackingHealthWatcher.schedule()`.
     */
    fun scheduleRefresh(context: Context) {
        try {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            manager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + INTERVAL_MS,
                INTERVAL_MS,
                pendingIntent(context)
            )
            Log.d(TAG, "Widget refresh scheduled every ${INTERVAL_MS / 60000} min")
        } catch (e: Exception) {
            // Called from Application.onCreate, where a throw is not a missing
            // widget but an app that will not start — the same trap
            // SyncWorker.schedule fell into and was caught by a test.
            Log.e(TAG, "Could not schedule widget refresh", e)
        }
    }

    fun cancelRefresh(context: Context) {
        try {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            manager.cancel(pendingIntent(context))
            Log.d(TAG, "Widget refresh cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Could not cancel widget refresh", e)
        }
    }

    /**
     * Targets [WidgetRefreshReceiver], not a provider, and not
     * `ACTION_APPWIDGET_UPDATE`.
     *
     * Two traps avoided here, both of which fail silently:
     *
     *  - The framework ignores an `ACTION_APPWIDGET_UPDATE` with no
     *    `EXTRA_APPWIDGET_IDS`, and an alarm scheduled once cannot know ids
     *    that change every time a widget is added or removed. That would be an
     *    alarm firing correctly forever while the number never moved.
     *  - A `PendingIntent` names **one** component, so aiming it at
     *    `TodayWidgetProvider` would refresh the small size only and leave a
     *    medium or large widget on the same home screen frozen.
     */
    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WidgetRefreshReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
