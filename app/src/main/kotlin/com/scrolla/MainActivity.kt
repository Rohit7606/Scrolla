package com.scrolla

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme

import com.scrolla.ui.screens.BatteryWhitelistScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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