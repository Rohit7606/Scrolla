package com.scrolla.firestore

import android.util.Log
import kotlinx.coroutines.flow.first
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
    private val singleFlight: SingleFlightSync = SingleFlightSync(),
    /** Held here rather than created per pass: its dedupe is one field of
     *  in-process state, and a fresh instance each time would report the same
     *  silent day on every tick. */
    private val telemetry: TrackingTelemetry = TrackingTelemetry()
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

            // One read of users/{uid}/groups for the whole pass (BUILD_PLAN
            // 2.2c). Both halves below used to fetch it separately, which was a
            // quarter of the sync read load spent asking the same question
            // twice. Reading it here rather than inside either half is what
            // makes it impossible for a third step to add a third read without
            // noticing.
            val memberships = readMemberships() ?: return@run

            scrollRepository.triggerFirestoreSync(memberships)
            updateGroupRecords(memberships)

            // Last, and never able to fail the pass. Reported from here rather
            // than from the UI because the person whose tracking died is
            // precisely the person with no reason to open the app — SyncWorker
            // runs hourly either way.
            reportTrackingHealth()
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
     * Tells somebody who can act on it that this device has stopped recording.
     *
     * See [TrackingTelemetry]. `observeServiceHealth()` is a Room-backed Flow
     * and `first()` takes its current value — the DAO's one-shot read was
     * deleted in the A-track audit and this does not need it back.
     */
    private suspend fun reportTrackingHealth() {
        try {
            val health = scrollRepository.observeServiceHealth().first()
            telemetry.report(
                health = health,
                userId = authRepository.currentUser?.uid,
                now = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Tracking telemetry skipped: ${e.message}")
        }
    }

    /**
     * The user's group memberships, or null if we could not find out.
     *
     * **Null and empty are different and the difference is the whole point.**
     * A failed read returning an empty list would upload today's total to no
     * groups, log "synced 0 group(s)", and mark the pass a success -- leaving
     * the user silently off every board until something else happened to work.
     * That is the exact shape of the bug SyncWorker exists to prevent, which
     * left anyone who did not open the app off the leaderboard for the day.
     */
    private suspend fun readMemberships(): List<GroupMembershipRef>? {
        val userId = authRepository.currentUser?.uid ?: run {
            Log.d(TAG, "Sync skipped — not signed in")
            return null
        }
        return groupRepository.getMemberships(userId).getOrElse {
            Log.w(TAG, "Sync skipped — could not read groups: ${it.message}")
            return null
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
    private suspend fun updateGroupRecords(memberships: List<GroupMembershipRef>) {
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

        // Was getUserGroups(user.uid) — a second read of users/{uid}/groups
        // plus a read of groups/{id} per group, for a groupName and a
        // memberCount this function never touches. It needs an id and a
        // joinedAt, and both are on the membership.
        val groups = memberships

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
