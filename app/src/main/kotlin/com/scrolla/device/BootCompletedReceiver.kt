package com.scrolla.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scrolla.room.ScrollaDatabase
import com.scrolla.room.ServiceHealthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * S1.A8: Re-checks accessibility-service enablement after every reboot.
 *
 * On BOOT_COMPLETED it queries isScrollAccessibilityServiceEnabled (pure logic) and
 * persists the result into ServiceHealthState.isAccessibilityServiceEnabled. It reads
 * the existing singleton row first and only overwrites the field this check owns, so
 * other fields (isServiceRunning, timestamps, degradedReason) are preserved.
 *
 * Person A convention (AGENTS.md 4.8): fail loud internally via logs, never crash,
 * never show UI directly from the receiver. onReceive returns immediately; the
 * Room write runs on a background coroutine so the main-thread broadcast is not blocked.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = isScrollAccessibilityServiceEnabled(context)
                Log.i(TAG, "BOOT_COMPLETED: ScrollAccessibilityService enabled = $enabled")

                val db = ScrollaDatabase.getDatabase(context.applicationContext)
                val current = db.serviceHealthDao().getOnce()
                val updated = if (current != null) {
                    current.copy(isAccessibilityServiceEnabled = enabled)
                } else {
                    ServiceHealthState(
                        id = 1,
                        isServiceRunning = false,
                        isAccessibilityServiceEnabled = enabled,
                        lastEventTimestamp = 0L,
                        lastRoomFlushTimestamp = 0L,
                        lastFirestoreSyncTimestamp = 0L,
                        degradedReason = null
                    )
                }
                db.serviceHealthDao().upsert(updated)
            } catch (e: Exception) {
                // Fail loud, never crash. Leave state untouched so a later real
                // check (MainActivity.onCreate) can correct it.
                Log.e(TAG, "BOOT_COMPLETED: failed to persist accessibility status", e)
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
