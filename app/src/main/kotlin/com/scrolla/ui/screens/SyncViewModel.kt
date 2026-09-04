package com.scrolla.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrolla.auth.AuthRepository
import com.scrolla.firestore.GroupRepository
import com.scrolla.firestore.RecordEligibility
import com.scrolla.model.ScrollaConstants
import com.scrolla.room.ScrollRepository
import com.scrolla.ui.ScrollaGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Drives the Firestore sync cadence (SPRINT_LOG S2.3), and the group record
 * write that rides along with it (PREMIUM_CHECKLIST P2.6).
 *
 * B decides *when* to sync; A owns the daily-total write itself behind
 * `triggerFirestoreSync()` (`DATA_CONTRACT.md` §3.3 — "B must not write to
 * Firestore directly for daily totals"). The record is a different document and
 * a different contract: it lives on the group metadata doc, which is B's, so it
 * is written here rather than inside A's sync.
 */
class SyncViewModel(
    private val scrollRepository: ScrollRepository = ScrollaGraph.scrollRepository,
    private val groupRepository: GroupRepository = GroupRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    /** Called when the app comes to foreground, and on the interval timer. */
    fun syncNow(reason: String) {
        viewModelScope.launch {
            Log.d(TAG, "Firestore sync requested ($reason)")
            scrollRepository.triggerFirestoreSync()
            updateGroupRecords()
        }
    }

    /**
     * Offers the user's best eligible day to every group they belong to.
     *
     * Runs after the totals sync rather than before it, so a day that has just
     * become eligible is already in Firestore when its record lands.
     *
     * Nothing here surfaces to the UI. A record write that loses a race, or is
     * refused by the rules, is ordinary — the next sync tries again fifteen
     * minutes later, and there is nothing a user could usefully do about it.
     */
    private suspend fun updateGroupRecords() {
        val user = authRepository.currentUser ?: return
        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: return

        // 30 days is well past the point where an older day could still be a
        // record nobody has claimed, without pulling the whole history every
        // fifteen minutes.
        val history = scrollRepository.getRecentDailyTotals(RECORD_LOOKBACK_DAYS)
        val best = RecordEligibility.bestEligibleDay(history, LocalDate.now()) ?: run {
            Log.d(TAG, "No record-eligible day yet")
            return
        }

        val groups = groupRepository.getUserGroups(user.uid).getOrElse {
            Log.d(TAG, "Skipping record update — could not read groups: ${it.message}")
            return
        }

        for (group in groups) {
            groupRepository.updateGroupRecordIfBetter(
                groupId = group.groupId,
                displayName = displayName,
                day = best.day,
                candidateKm = best.totalKm
            ).onSuccess { written ->
                if (written) Log.d(TAG, "New record in ${group.groupId}: ${best.totalKm} km")
            }
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
        const val RECORD_LOOKBACK_DAYS = 30
    }
}
