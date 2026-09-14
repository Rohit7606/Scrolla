package com.scrolla.auth

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.scrolla.diagnostics.CrashReporter
import com.scrolla.ui.ScrollaGraph
import com.scrolla.work.SyncWorker

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

        // BUILD_PLAN 1.2: tag reports with the signed-in uid, and only the uid.
        //
        // One auth-state listener rather than a call at each of sign-in,
        // sign-out, account deletion and a launch that restores an existing
        // session. Those are four places today and the fourth — a launch with a
        // session already in place — is the one that would have been missed,
        // which is most users on most days.
        //
        // Fires immediately with the current state when added, so a session
        // restored before this line still gets tagged.
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            CrashReporter.setSignedInUser(auth.currentUser?.uid)
        }

        // BUILD_PLAN 1.1: keep syncing when the app is closed. Idempotent, the
        // same way TrackingHealthWatcher.schedule() above is — calling it on
        // every launch re-registers the one periodic job rather than stacking
        // them, because it is enqueued under a unique name with KEEP.
        SyncWorker.schedule(this)
    }
}
