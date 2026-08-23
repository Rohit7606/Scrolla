package com.scrolla.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrolla.model.ScrollaConstants
import com.scrolla.room.ScrollRepository
import com.scrolla.ui.ScrollaGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drives the Firestore sync cadence (SPRINT_LOG S2.3).
 *
 * B decides *when* to sync; A owns the write itself behind
 * `triggerFirestoreSync()` (`DATA_CONTRACT.md` §3.3 — "B must not write to
 * Firestore directly for daily totals"). Until A implements that function it is
 * a logged no-op, so this schedule is correct but has no cloud effect yet.
 */
class SyncViewModel(
    private val scrollRepository: ScrollRepository = ScrollaGraph.scrollRepository
) : ViewModel() {

    /** Called when the app comes to foreground, and on the interval timer. */
    fun syncNow(reason: String) {
        viewModelScope.launch {
            Log.d(TAG, "Firestore sync requested ($reason)")
            scrollRepository.triggerFirestoreSync()
        }
    }

    /**
     * Runs for as long as the shell is composed. The first sync fires
     * immediately; afterwards every FIRESTORE_SYNC_INTERVAL_MS. Do not shorten
     * the interval without redoing the quota math in AGENTS.md.
     */
    suspend fun runPeriodicSync() {
        while (true) {
            syncNow("periodic")
            delay(ScrollaConstants.FIRESTORE_SYNC_INTERVAL_MS)
        }
    }

    private companion object {
        const val TAG = "SyncViewModel"
    }
}
