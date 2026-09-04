package com.scrolla.device

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.scrolla.MainActivity
import com.scrolla.room.ScrollaDatabase
import com.scrolla.room.ServiceHealthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fired by [TrackingHealthWatcher]'s repeating alarm (PREMIUM_CHECKLIST P2.12).
 *
 * Checks whether the accessibility service is actually enabled — the same
 * [isScrollAccessibilityServiceEnabled] check that consults the master switch,
 * so a crashed-but-still-listed service reads as off — and:
 *   - if off, posts one high-priority notification asking the user to turn it
 *     back on, and persists the state to Room so the in-app banner agrees;
 *   - if on, clears any standing notification and persists the healthy state.
 *
 * A stable notification id means a repeat firing updates the one notification
 * rather than stacking a new one every fifteen minutes.
 *
 * Person A convention (AGENTS.md 4.8): fail loud via logs, never crash. The copy
 * lives here rather than in ui/ScrollaStrings so device/ does not depend on B's
 * UI layer — the same choice the foreground-service notification makes with
 * ScrollaConstants.
 */
class TrackingHealthReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = isScrollAccessibilityServiceEnabled(appContext)
                if (enabled) clearNotification(appContext) else notifyTrackingOff(appContext)
                persist(appContext, enabled)
            } catch (e: Exception) {
                Log.e(TAG, "health check failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun persist(context: Context, enabled: Boolean) {
        val db = ScrollaDatabase.getDatabase(context)
        // Ensure a row exists without clobbering the other fields, then update
        // only the one this check owns — the pattern BootCompletedReceiver uses.
        db.serviceHealthDao().ensureRowExists(
            ServiceHealthState(
                id = 1,
                isServiceRunning = false,
                isAccessibilityServiceEnabled = enabled,
                lastEventTimestamp = 0L,
                lastRoomFlushTimestamp = 0L,
                lastFirestoreSyncTimestamp = 0L,
                degradedReason = null
            )
        )
        db.serviceHealthDao().updateAccessibilityEnabled(enabled)
    }

    private fun notifyTrackingOff(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            )
        }
        // Straight to the accessibility list — the one action that fixes it.
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(NOTIF_TITLE)
            .setContentText(NOTIF_BODY)
            .setStyle(NotificationCompat.BigTextStyle().bigText(NOTIF_BODY))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        // On API 33+ this silently no-ops without POST_NOTIFICATIONS; the runtime
        // grant is requested in MainActivity. The in-app banner is the guaranteed
        // half regardless.
        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "POST_NOTIFICATIONS not granted; banner will still show in-app", e)
        }
    }

    private fun clearNotification(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val TAG = "TrackingHealthReceiver"
        private const val CHANNEL_ID = "scrolla_alerts"
        private const val CHANNEL_NAME = "Tracking alerts"
        private const val NOTIFICATION_ID = 2002

        // Device-local copy on purpose — see the class KDoc.
        private const val NOTIF_TITLE = "Scrolla stopped tracking"
        private const val NOTIF_BODY =
            "Tap to turn it back on — no scroll distance is being recorded until you do."
    }
}
