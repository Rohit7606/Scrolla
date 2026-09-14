package com.scrolla.firestore

import android.util.Log
import com.scrolla.auth.AuthRepository
import com.scrolla.room.ScrollRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One sync pass: reconcile the personal backup, push today's total, then offer
 * the group records.
 *
 * Lifted out of `SyncViewModel` for BUILD_PLAN 1.1. It lived in a ViewModel,
 * which lives only while the app is on screen — so **a user who tracked all day
 * and never opened Scrolla did not appear on their group's leaderboard that
 * day**, because `getGroupLeaderboard` filters on `date == today` and they had
 * no document. At fifty people the board showed whoever happened to open the
 * app. The thing you open the app to see was broken by not opening the app.
 *
 * So this is a plain class with no Android lifecycle in it: `SyncViewModel`
 * drives it while the UI is up, and `SyncWorker` drives it when the UI is gone.
 * Nothing here knows which one called.
 *
 * **There must be exactly one instance per process** — held by `ScrollaGraph`.
 * Both halves of its state assume that. [singleFlight] is what stops a
 * foreground pass and a background pass doing the same work at the same time,
 * and it can only do that if they share it; two instances would each serialise
 * against themselves and race each other, which is the bug of 2026-09-14
 * rebuilt with more steps.
 */
class SyncEngine(
    private val scrollRepository: ScrollRepository,
    private val groupRepository: GroupRepository = GroupRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val backupRepository: BackupRepository = BackupRepository(),
    private val singleFlight: SingleFlightSync = SingleFlightSync()
) {

    /**
     * The reconcile is a whole-history read; once a session is enough.
     *
     * Only safe as a plain flag because [singleFlight] serialises passes. It is
     * a check-then-set with a suspending read between the two halves, so with
     * passes running concurrently both cleared it and both uploaded — visible
     * on device 2026-09-14 as `Backup reconcile uploaded 2 day(s)` twice,
     * 103 ms apart. Now that a background worker can call this too, the two
     * racers no longer even have to be on the same screen.
     */
    private var hasReconciled = false

    /**
     * Runs a pass unless one has just run.
     *
     * @param reason appears in the log and is the only way to tell the callers
     *   apart afterwards — "foreground", "periodic", "background".
     * @return true if the work ran, false if it was dropped as redundant.
     */
    suspend fun sync(reason: String): Boolean {
        val ran = singleFlight.run {
            Log.d(TAG, "Firestore sync requested ($reason)")
            reconcilePersonalBackup()
            scrollRepository.triggerFirestoreSync()
            updateGroupRecords()
        }

        if (!ran) {
            // Logged, not silent. A dropped pass and a pass that never fired
            // look identical from outside, and this code has already paid for
            // that distinction once.
            Log.d(
                TAG,
                "Firestore sync skipped ($reason) — a pass started " +
                    "${singleFlight.millisSinceLastStart()} ms ago"
            )
        }
        return ran
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

        val groups = groupRepository.getUserGroups(user.uid).getOrElse {
            Log.d(TAG, "Skipping record update — could not read groups: ${it.message}")
            return
        }

        if (groups.isEmpty()) {
            Log.d(TAG, "Record update skipped — user belongs to no groups")
            return
        }

        // Evaluated per group, not once for all of them. A group record is
        // earned inside the group, so each one is bounded by the date this user
        // joined *that* group — and those dates differ. Computing one "best day"
        // and offering it everywhere is what let a group created on 7 September
        // inherit a record from 25 August.
        for (group in groups) {
            val joinedDay = group.joinedAt
                ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

            if (joinedDay == null) {
                // Every membership written by joinGroup/createGroup carries
                // joinedAt, so this means a hand-edited or very old document.
                // Log rather than silently falling back to the whole history,
                // which is the behaviour this bound exists to remove.
                Log.w(TAG, "No joinedAt for ${group.groupId} — record offer skipped")
                continue
            }

            val best = RecordEligibility.bestEligibleDay(
                totals = history,
                today = LocalDate.now(),
                activeSpanFor = { day -> activeSpans[day] ?: 0 },
                notBefore = joinedDay
            )

            if (best == null) {
                Log.d(TAG, "No record-eligible day for ${group.groupId} since joining $joinedDay")
                continue
            }

            Log.d(TAG, "Offering ${best.day} (${best.totalKm} km) to ${group.groupId}, joined $joinedDay")

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

    private companion object {
        const val TAG = "SyncEngine"
        const val RECORD_LOOKBACK_DAYS = 30

        /**
         * How much local history to offer the backup. A year is past anything
         * Scrolla has ever held, so in practice this is "everything", while
         * keeping the query bounded — the same figure the CSV export uses.
         */
        const val BACKUP_LOOKBACK_DAYS = 365
    }
}
