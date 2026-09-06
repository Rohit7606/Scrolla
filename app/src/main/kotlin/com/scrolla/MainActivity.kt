package com.scrolla

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.scrolla.auth.AuthRepository
import com.scrolla.device.TrackingHealthWatcher
import com.scrolla.device.isScrollAccessibilityServiceEnabled
import com.scrolla.room.ScrollaDatabase
import com.scrolla.room.ServiceHealthState
import com.scrolla.ui.screens.MainShell
import com.scrolla.ui.screens.OnboardingScreen
import com.scrolla.ui.screens.SignInScreen
import com.scrolla.ui.screens.SplashScreen
import com.scrolla.ui.theme.ScrollaUILabTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() — hands off from the system splash
        // to the app's Compose UI without a white-flash transition.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        refreshAccessibilityStatus()

        // P2.12: keep the background health watch armed. Idempotent, so calling
        // it on every launch simply re-arms the one alarm.
        TrackingHealthWatcher.schedule(applicationContext)

        // The FGS notification (bug #1) and the P2.12 alert both need this on
        // API 33+, where it defaults denied and the app had no request flow.
        requestNotificationPermissionIfNeeded()

        setContent {
            ScrollaUILabTheme {
                androidx.compose.material3.Surface {
                    // Real sign-in state: check Firebase session at startup so
                    // returning users skip the sign-in screen entirely.
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val prefs = remember { context.getSharedPreferences("scrolla_prefs", android.content.Context.MODE_PRIVATE) }

                    var currentScreen by remember { mutableStateOf("splash") }
                    var isSignedIn by remember { mutableStateOf(AuthRepository().currentUser != null) }
                    var isFirstLaunch by remember { mutableStateOf(prefs.getBoolean("is_first_launch", true)) }

                    when (currentScreen) {
                        "splash" -> {
                            SplashScreen(
                                onSplashFinished = {
                                    currentScreen = if (isSignedIn) {
                                        if (isFirstLaunch) "onboarding" else "home"
                                    } else {
                                        "signin"
                                    }
                                }
                            )
                        }
                        "signin" -> {
                            SignInScreen(
                                onSignInSuccess = { isNewUser ->
                                    isSignedIn = true
                                    if (isNewUser) {
                                        currentScreen = "onboarding"
                                    } else {
                                        prefs.edit().putBoolean("is_first_launch", false).apply()
                                        isFirstLaunch = false
                                        currentScreen = "home"
                                    }
                                },
                                onSignInError = { msg ->
                                    // Person B track: fail visible — surface error in a future snackbar.
                                    // For now, log so it's discoverable without crashing.
                                    android.util.Log.e("MainActivity", "Sign-in failed: $msg")
                                }
                            )
                        }
                        "onboarding" -> {
                            OnboardingScreen(
                                onFinishOnboarding = {
                                    prefs.edit().putBoolean("is_first_launch", false).apply()
                                    isFirstLaunch = false
                                    currentScreen = "home"
                                },
                                onGrantPermission = {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            )
                        }
                        "home" -> {
                            MainShell(
                                onSignedOut = {
                                    isSignedIn = false
                                    currentScreen = "signin"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * S1.A8: re-check accessibility-service enablement on every foreground return.
     *
     * This used to sit in onCreate() and its comment claimed "every foreground
     * start", but onCreate runs once per activity creation. Enabling or disabling
     * the service in Android Settings and coming back left the persisted state
     * stale, so the health card kept reporting whatever was true at launch — which
     * is exactly the moment the user is looking at it to confirm their change
     * took effect.
     */
    override fun onResume() {
        super.onResume()
        refreshAccessibilityStatus()
    }

    private fun refreshAccessibilityStatus() {
        // Targeted update — ensure the row exists (IGNORE) then update only
        // isAccessibilityServiceEnabled, with no read-then-write snapshot race.
        lifecycleScope.launch {
            try {
                val enabled = isScrollAccessibilityServiceEnabled(this@MainActivity)
                val db = ScrollaDatabase.getDatabase(applicationContext)
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

                // If the switch is off the service cannot be running, whatever
                // the row currently claims. Worth writing explicitly because
                // `isServiceRunning` is only ever cleared by onDestroy/onUnbind,
                // and a package replace or an OEM process kill takes the process
                // without either — leaving the Settings health card reporting a
                // running tracker with nothing behind it. Measured on 2026-09-06:
                // the row read (isServiceRunning=1, isAccessibilityServiceEnabled=1)
                // while the device reported Bound services:{}.
                if (!enabled) {
                    db.serviceHealthDao().updateServiceRunning(false)
                }
            } catch (e: Exception) {
                // Fail loud, never crash. A future Service Health screen reads the
                // persisted state; a crash here must not block the UI from launching.
                e.printStackTrace()
            }
        }
    }

    /**
     * POST_NOTIFICATIONS is a runtime permission on API 33+ and defaults denied,
     * which is DEVICE_TEST_LOG bug #1 — the foreground-service notification never
     * showed on a fresh install because nothing asked. The P2.12 tracking-off
     * alert needs it too. A single quiet request at launch; if denied, the
     * in-app banner still covers the app-open case.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }
}
