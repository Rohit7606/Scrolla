package com.scrolla.auth

import android.app.Application
import com.google.firebase.FirebaseApp

/**
 * Application class to initialize Firebase.
 */
class ScrollaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
    }
}