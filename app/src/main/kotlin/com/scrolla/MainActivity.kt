package com.scrolla

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.scrolla.device.isScrollAccessibilityServiceEnabled
import com.scrolla.room.ScrollaDatabase
import com.scrolla.room.ServiceHealthState
import com.scrolla.ui.screens.HomeScreen
import com.scrolla.ui.screens.OnboardingScreen
import com.scrolla.ui.screens.SignInScreen
import com.scrolla.ui.screens.SplashScreen
import com.scrolla.ui.theme.ScrollaUILabTheme
import kotlinx.coroutines.launch

/**
 * Navigation destinations — simple state machine for the linear launch flow.
 * Replace with NavHost when deep-linking or bottom nav is needed.
 */
private enum class AppScreen { SPLASH, SIGN_IN, ONBOARDING, HOME }

class MainActivity : ComponentActivity() {

    /** Compose-observable navigation state. */
    private var currentScreen by mutableStateOf(AppScreen.SPLASH)

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
                when (currentScreen) {
                    AppScreen.SPLASH -> SplashScreen(
                        onSplashFinished = {
                            // If user is already authenticated, skip sign-in.
                            currentScreen = if (FirebaseAuth.getInstance().currentUser != null) {
                                AppScreen.ONBOARDING
                            } else {
                                AppScreen.SIGN_IN
                            }
                        }
                    )

                    AppScreen.SIGN_IN -> SignInScreen(
                        onSignInSuccess = {
                            currentScreen = AppScreen.ONBOARDING
                        },
                        onSignInError = { errorMessage ->
                            Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    )

                    AppScreen.ONBOARDING -> OnboardingScreen(
                        onFinishOnboarding = {
                            currentScreen = AppScreen.HOME
                        },
                        onGrantPermission = {
                            // Launch accessibility settings so user can enable the service.
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )

                    AppScreen.HOME -> HomeScreen()
                }
            }
        }
    }
}
