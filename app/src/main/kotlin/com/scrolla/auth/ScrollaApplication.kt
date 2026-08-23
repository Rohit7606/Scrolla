package com.scrolla.auth

import android.app.Application
import com.google.firebase.FirebaseApp
import com.scrolla.ui.ScrollaGraph

/**
 * Application class to initialize Firebase and B's UI composition root.
 */
class ScrollaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        // Must run before any ViewModel is constructed — see ScrollaGraph.
        ScrollaGraph.init(this)
    }
}