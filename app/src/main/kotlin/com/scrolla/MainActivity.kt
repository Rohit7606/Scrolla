package com.scrolla

import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.scrolla.auth.AuthRepository
import com.scrolla.device.isScrollAccessibilityServiceEnabled
import com.scrolla.room.ScrollaDatabase
import com.scrolla.room.ServiceHealthState
import com.scrolla.ui.screens.HomeScreen
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
            ScrollaUILabTheme {
                Surface {
                    // Real sign-in state: check Firebase session at startup so
                    // returning users skip the sign-in screen entirely.
                    var currentScreen by remember { mutableStateOf("splash") }
                    var isSignedIn by remember { mutableStateOf(AuthRepository().currentUser != null) }
                    var isFirstLaunch by remember { mutableStateOf(true) }

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
                                onSignInSuccess = {
                                    isSignedIn = true
                                    currentScreen = if (isFirstLaunch) "onboarding" else "home"
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
                                    isFirstLaunch = false
                                    currentScreen = "home"
                                },
                                onGrantPermission = {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            )
                        }
                        "home" -> {
                            HomeScreen()
                        }
                    }
                }
            }
        }
    }
}