package com.scrolla.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrolla.firestore.SyncEngine
import com.scrolla.model.ScrollaConstants
import com.scrolla.ui.ScrollaGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drives the Firestore sync cadence *while the app is on screen* (SPRINT_LOG
 * S2.3).
 *
 * The work itself moved to [SyncEngine] for BUILD_PLAN 1.1, because this class
 * lives only as long as the shell is composed — which is precisely why a user
 * who tracked all day and never opened Scrolla never reached their group's
 * leaderboard. `SyncWorker` now covers that case, and both call the same
 * engine so there is one definition of what a sync pass is.
 *
 * What is left here is only *when* to sync while someone is looking.
 */
class SyncViewModel(
    private val syncEngine: SyncEngine = ScrollaGraph.syncEngine
) : ViewModel() {

    /** Called when the app comes to foreground, and on the interval timer. */
    fun syncNow(reason: String) {
        viewModelScope.launch { syncEngine.sync(reason) }
    }

    /**
     * Runs for as long as the shell is composed. The first sync fires
     * immediately; afterwards every FIRESTORE_SYNC_INTERVAL_MS. Do not shorten
     * the interval without redoing the quota math in AGENTS.md.
     *
     * The immediate first pass overlaps the `RefreshOnResume` trigger in
     * `MainShell` on every launch — both fire within ~400 ms. That duplicate is
     * dropped inside the engine rather than here; see `SyncCadence`.
     */
    suspend fun runPeriodicSync() {
        while (true) {
            syncNow("periodic")
            delay(ScrollaConstants.FIRESTORE_SYNC_INTERVAL_MS)
        }
    }
}
