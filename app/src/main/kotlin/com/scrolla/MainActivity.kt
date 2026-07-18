package com.scrolla

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.lifecycleScope
import com.scrolla.device.isScrollAccessibilityServiceEnabled
import com.scrolla.room.ScrollaDatabase
import com.scrolla.room.ServiceHealthState
import com.scrolla.ui.screens.BatteryWhitelistScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // S1.A8: re-check accessibility-service enablement on every foreground start.
        // Persist only isAccessibilityServiceEnabled, preserving all other fields.
        lifecycleScope.launch {
            try {
                val enabled = isScrollAccessibilityServiceEnabled(this@MainActivity)
                val db = ScrollaDatabase.getDatabase(applicationContext)
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
                // Fail loud, never crash. A future Service Health screen reads the
                // persisted state; a crash here must not block the UI from launching.
                e.printStackTrace()
            }
        }

        setContent {
            MaterialTheme {
                // TEMPORARY: direct screen for S1.A7 testing, replace when NavHost is built
                BatteryWhitelistScreen()
            }
        }
    }
}

// Note: ScrollaApp() and ScrollaAppPreview() removed - they were unused placeholders.
// Will be re-created when NavHost is implemented.