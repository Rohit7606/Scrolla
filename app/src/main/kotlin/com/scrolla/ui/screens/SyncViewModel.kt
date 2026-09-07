package com.scrolla.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrolla.auth.AuthRepository
import com.scrolla.firestore.BackupReconcile
import com.scrolla.firestore.BackupRepository
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
    private val authRepository: AuthRepository = AuthRepository(),
    private val backupRepository: BackupRepository = BackupRepository()
) : ViewModel() {

    /** The reconcile is a whole-history read; once a session is enough. */
    private var hasReconciled = false

    /** Called when the app comes to foreground, and on the interval timer. */
    fun syncNow(reason: String) {
        viewModelScope.launch {
            Log.d(TAG, "Firestore sync requested ($reason)")
            reconcilePersonalBackup()
            scrollRepository.triggerFirestoreSync()
            updateGroupRecords()
        }
    }

    /**
     * Two-way reconcile between the device and the user's own cloud backup.
     *
     * Runs **before** the group sync on purpose: a restored day has to be in
     * Room before `updateGroupRecords()` looks for the best eligible day, or the
     * first run after a reinstall would offer the group a record chosen from an
     * almost-empty history.
     *
     * Once per session, not every fifteen minutes. It is a full read of the
     * user's history, and nothing changes between ticks that the ordinary
     * per-day write does not already cover.
     *
     * Failure is silent by design. Backup is not something the user asked for in
     * the moment, so a failed reconcile should cost them nothing and say
     * nothing; the next launch tries again. The one thing it must never do is
     * lose local data, which is why the plan is computed by
     * [BackupReconcile] with the device winning every disagreement.
     */
    private suspend fun reconcilePersonalBackup() {
        if (hasReconciled) return
        val userId = authRepository.currentUser?.uid ?: return

        val cloud = backupRepository.fetchAll(userId).getOrElse {
            Log.d(TAG, "Backup reconcile skipped — could not read backup: ${it.message}")
            return
        }

        val local = scrollRepository.getRecentDailyTotals(BACKUP_LOOKBACK_DAYS)
        val plan = BackupReconcile.plan(local = local, cloud = cloud)

        // Set before the writes, not after: a failure part-way through must not
        // retry the whole reconcile on every tick for the rest of the session.
        hasReconciled = true

        if (plan.isEmpty) {
            Log.d(TAG, "Backup reconcile: already in sync (${local.size} local, ${cloud.size} cloud)")
            return
        }

        if (plan.toRestore.isNotEmpty()) {
            val restored = scrollRepository.restoreDailyTotals(plan.toRestore)
            Log.i(TAG, "Backup reconcile restored $restored day(s) from the cloud")
        }

        if (plan.toUpload.isNotEmpty()) {
            backupRepository.upload(userId, plan.toUpload)
                .onSuccess { Log.i(TAG, "Backup reconcile uploaded $it day(s)") }
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
        // Every exit below logs. It did not, and on 2026-09-06 that made the
        // feature's state unreadable from a device: it ran, wrote nothing, and
        // said nothing, so there was no way to tell whether a record had been
        // set, refused, or never attempted. Three of the exits were completely
        // silent — no user, blank display name, and the loop finishing with
        // nothing written — and `onSuccess` with no `onFailure` meant a rejected
        // write was silent too. For a feature whose whole output is one number
        // that can never be raised again once lowered, that is the wrong
        // trade: this is cheap and the alternative is guessing.
        val user = authRepository.currentUser ?: run {
            Log.d(TAG, "Record update skipped — not signed in")
            return
        }
        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: run {
            // Not cosmetic. `recordHolder` is a name shown to the whole group,
            // so there is nothing sensible to write without one — but silently
            // disabling the entire record feature is a different thing from
            // declining one write, and it was indistinguishable from working.
            Log.w(TAG, "Record update skipped — signed-in user has no display name (uid=${user.uid})")
            return
        }

        // 30 days is well past the point where an older day could still be a
        // record nobody has claimed, without pulling the whole history every
        // fifteen minutes.
        val history = scrollRepository.getRecentDailyTotals(RECORD_LOOKBACK_DAYS)

        // Pre-fetch the hour counts rather than querying inside the filter:
        // bestEligibleDay is a pure function and must stay one, so it takes a
        // plain lookup rather than a suspending call it cannot make.
        val activeSpans = history.associate { it.day to scrollRepository.getActiveHourSpan(it.day) }

        val best = RecordEligibility.bestEligibleDay(
            totals = history,
            today = LocalDate.now(),
            activeSpanFor = { day -> activeSpans[day] ?: 0 }
        ) ?: run {
            Log.d(TAG, "No record-eligible day yet")
            return
        }

        val groups = groupRepository.getUserGroups(user.uid).getOrElse {
            Log.d(TAG, "Skipping record update — could not read groups: ${it.message}")
            return
        }

        if (groups.isEmpty()) {
            Log.d(TAG, "Record update skipped — user belongs to no groups")
            return
        }

        Log.d(TAG, "Offering ${best.day} (${best.totalKm} km) to ${groups.size} group(s)")

        for (group in groups) {
            groupRepository.updateGroupRecordIfBetter(
                groupId = group.groupId,
                displayName = displayName,
                day = best.day,
                candidateKm = best.totalKm
            ).onSuccess { written ->
                if (written) {
                    Log.i(TAG, "New record in ${group.groupId}: ${best.totalKm} km on ${best.day}")
                } else {
                    // The ordinary outcome, and previously invisible — which
                    // made "the record is already better" look identical to
                    // "the record code never ran".
                    Log.d(TAG, "Record in ${group.groupId} not beaten by ${best.totalKm} km")
                }
            }.onFailure {
                // Losing a race or being refused by the rules is expected and
                // retried in fifteen minutes, so this stays out of the user's
                // way — but it must not be invisible to us.
                Log.w(TAG, "Record write failed for ${group.groupId}: ${it.message}")
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

        /**
         * How much local history to offer the backup. A year is past anything
         * Scrolla has ever held, so in practice this is "everything", while
         * keeping the query bounded — the same figure the CSV export uses.
         */
        const val BACKUP_LOOKBACK_DAYS = 365
    }
}
