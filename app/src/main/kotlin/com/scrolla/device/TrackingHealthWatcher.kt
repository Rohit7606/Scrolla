package com.scrolla.device

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Background watch for a stopped accessibility service (PREMIUM_CHECKLIST P2.12).
 *
 * The in-app banner catches a stopped service the instant the app is opened, but
 * the failure this exists for happens while the app is **closed**: the 9.5-hour
 * silent gap on 2026-08-25, and the multi-day gap after a UPI app told the user
 * to turn Scrolla off. A crashed or disabled AccessibilityService cannot restart
 * itself — that needs WRITE_SECURE_SETTINGS, a system permission, refused even
 * to adb on MIUI — so the most an app can honestly do is notice quickly and ask
 * the user to turn it back on.
 *
 * AlarmManager rather than WorkManager, matching the widget decision recorded in
 * the sprint plan: more reliable than WorkManager on aggressive OEM builds.
 * Inexact and non-wakeup, so it costs almost nothing, needs no exact-alarm
 * permission, and never wakes a sleeping phone just to check — the next time the
 * device is awake is soon enough to tell someone their tracking is off.
 */
object TrackingHealthWatcher {

    private const val TAG = "TrackingHealthWatcher"
    private const val REQUEST_CODE = 2001
    private val INTERVAL_MS = AlarmManager.INTERVAL_FIFTEEN_MINUTES

    /**
     * Idempotent: the same PendingIntent is reused, so calling this from several
     * entry points (app start, resume, boot) re-arms one alarm rather than
     * stacking them.
     */
    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (am == null) {
            Log.e(TAG, "no AlarmManager; cannot schedule health watch")
            return
        }
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            INTERVAL_MS,
            pendingIntent(context)
        )
        Log.i(TAG, "health watch scheduled every ${INTERVAL_MS}ms")
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context.applicationContext,
            REQUEST_CODE,
            Intent(context.applicationContext, TrackingHealthReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
