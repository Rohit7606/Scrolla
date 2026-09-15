package com.scrolla.firestore

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/** Raised when a user tries to join a group they are already a member of. */
class AlreadyInGroupException : Exception("You're already in this group")

/** One group the user belongs to, joined with the group's own metadata. */
data class GroupMembership(
    val groupId: String,
    val groupName: String,
    val displayName: String,
    val isPrimary: Boolean,
    val memberCount: Int,
    /**
     * When this user joined this group, epoch millis, or null for a membership
     * written before it was read back.
     *
     * Both `joinGroup` and `createGroup` have always written `joinedAt`, and
     * until 2026-09-07 nothing ever read it — so a group record could be set by
     * a day from before the group existed. See `RecordEligibility.notBefore`.
     */
    val joinedAt: Long? = null
)

/**
 * A membership row exactly as it is stored under `users/{uid}/groups`, with no
 * group metadata joined onto it (BUILD_PLAN 2.2c).
 *
 * [GroupMembership] is this plus a read of `groups/{id}` for the name and member
 * count, which is what a *screen* needs. A sync pass needs neither: it writes
 * totals keyed by group id and it bounds records by `joinedAt`, both of which
 * live on the membership itself. Reading the group document to get them cost a
 * read per group per pass for two fields nobody looked at.
 */
data class GroupMembershipRef(
    val groupId: String,
    val displayName: String,
    val joinedAt: Long?
)

/** One member's total for a single day, as stored under /groups/{id}/dailyTotals. */
data class MemberDailyTotal(
    val userId: String,
    val displayName: String,
    val totalKm: Float,
    /**
     * This member's own seven-day totals, written by their own device during
     * sync (BUILD_PLAN 3.3). Null when they have no data in that window —
     * absent, not zero, which is what makes someone who joined on Friday
     * ineligible for "most improved" rather than its biggest loser.
     *
     * They ride in this document rather than in a query of their own so the
     * award costs no reads: fourteen days x N members per board view would have
     * undone 2.2c several times over.
     */
    val last7Km: Float? = null,
    val prev7Km: Float? = null
)

/**
 * A group's board for one day, and whether it came off the wire or off the disk
 * (BUILD_PLAN 2.7a).
 *
 * Firestore keeps local persistence on by default, so a read with no network
 * succeeds from cache rather than failing — which is why 2.7's premise ("being
 * on the train reads as an error") turned out to be wrong on device. The board
 * is right to render. It was wrong to render *silently*: "1 of 1 synced today"
 * is a claim about right now, and from cache it can be hours old.
 *
 * The flag is returned with the data rather than exposed as a separate query so
 * it cannot drift from the rows it describes, and so a caller has to decide what
 * to do about it instead of not knowing there was anything to decide.
 */
data class GroupLeaderboard(
    val totals: List<MemberDailyTotal>,
    /** True when Firestore served this from disk — no server round trip. */
    val isFromCache: Boolean
)

/** A group's all-time best (lowest) single day, from the group metadata document. */
data class GroupRecord(
    val recordKm: Float,
    val recordHolder: String,
    val recordDate: String
)

class GroupRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        const val DELETED_HOLDER = "[deleted]"

        /** Matches the bound enforced in isGroupRename(). Both exist: the rule
         *  is the only one a hostile client cannot skip. */
        const val MAX_GROUP_NAME_LENGTH = 40
        private const val TAG = "GroupRepository"
        private const val GROUP_CODE_LENGTH = 6
    }

    private fun generateGroupCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..GROUP_CODE_LENGTH)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }

    /**
     * Creates a new group, adding the creator as the first member.
     * Returns the 6-digit group code on success.
     */
    suspend fun createGroup(userId: String, displayName: String, groupName: String): Result<String> {
        return try {
            var groupCode = generateGroupCode()
            
            // Basic collision check
            var attempts = 0
            while (firestore.collection("groups").document(groupCode).get().await().exists() && attempts < 5) {
                groupCode = generateGroupCode()
                attempts++
            }

            // 1. Create group metadata document
            val groupData = mapOf(
                "groupName" to groupName,
                "groupCode" to groupCode,
                "createdBy" to userId,
                "createdAt" to FieldValue.serverTimestamp(),
                "members" to listOf(userId) // Maintain array per DATA_CONTRACT §3.2
            )

            firestore.collection("groups").document(groupCode).set(groupData).await()

            // 2. Determine if this should be their primary group (true if it's their first)
            val userGroups = firestore.collection("users").document(userId).collection("groups").get().await()
            val isPrimary = userGroups.isEmpty

            // 3. Create user membership document
            val membershipData = mapOf(
                "joinedAt" to FieldValue.serverTimestamp(),
                "isPrimary" to isPrimary,
                "displayName" to displayName
            )

            firestore.collection("users").document(userId)
                .collection("groups").document(groupCode)
                .set(membershipData).await()

            Log.d(TAG, "Successfully created group: $groupCode for user: $userId")
            Result.success(groupCode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create group", e)
            Result.failure(e)
        }
    }

    /**
     * Joins an existing group using its 6-digit code.
     */
    suspend fun joinGroup(userId: String, displayName: String, groupCode: String): Result<Unit> {
        return try {
            val code = groupCode.trim().uppercase()
            val groupRef = firestore.collection("groups").document(code)
            
            // 1. Validate the group exists
            val groupDoc = groupRef.get().await()
            if (!groupDoc.exists()) {
                return Result.failure(Exception("Group not found — check the code"))
            }

            // 2. Refuse a re-join rather than silently rewriting the membership.
            // Rejoining recomputed isPrimary from "do I have any groups", which is
            // false once you have one — so re-entering your own code cleared the
            // primary flag and with it the widget's group.
            val existing = firestore.collection("users").document(userId)
                .collection("groups").document(code).get().await()
            if (existing.exists()) {
                return Result.failure(AlreadyInGroupException())
            }

            // 3. Add user to group's members array
            groupRef.update("members", FieldValue.arrayUnion(userId)).await()

            // 4. First group becomes the primary one (the widget reads it).
            val userGroups = firestore.collection("users").document(userId).collection("groups").get().await()
            val isPrimary = userGroups.isEmpty

            // 5. Create user membership document
            val membershipData = mapOf(
                "joinedAt" to FieldValue.serverTimestamp(),
                "isPrimary" to isPrimary,
                "displayName" to displayName
            )

            firestore.collection("users").document(userId)
                .collection("groups").document(code)
                .set(membershipData).await()

            Log.d(TAG, "Successfully joined group: $code for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join group", e)
            Result.failure(e)
        }
    }

    /**
     * Every group the user belongs to, joined with each group's metadata so the
     * switcher and profile can show a name and a member count.
     */
    suspend fun getUserGroups(userId: String): Result<List<GroupMembership>> {
        return try {
            val memberships = firestore.collection("users").document(userId)
                .collection("groups").get().await()

            val groups = memberships.documents.mapNotNull { membership ->
                val groupId = membership.id
                val groupDoc = firestore.collection("groups").document(groupId).get().await()
                if (!groupDoc.exists()) {
                    // Group deleted out from under the membership — skip rather than
                    // render a ghost row.
                    Log.w(TAG, "Membership for missing group $groupId, skipping")
                    return@mapNotNull null
                }
                GroupMembership(
                    groupId = groupId,
                    groupName = groupDoc.getString("groupName") ?: groupId,
                    displayName = membership.getString("displayName") ?: "Unknown",
                    isPrimary = membership.getBoolean("isPrimary") ?: false,
                    memberCount = (groupDoc.get("members") as? List<*>)?.size ?: 0,
                    joinedAt = membership.getTimestamp("joinedAt")?.toDate()?.time
                )
            }
            Result.success(groups)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load groups for user: $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Which groups this user belongs to — **one read**, no group documents
     * (BUILD_PLAN 2.2c).
     *
     * This is what a sync pass wants. [getUserGroups] answers the same question
     * and then reads `groups/{id}` for each one to get a name and a member
     * count, which a screen needs and a sync does not.
     *
     * A failure here is a failure, not an empty list. The distinction matters:
     * the caller must not read "no groups" out of "could not tell", mark the
     * pass a success and leave the user off every board for the day.
     */
    suspend fun getMemberships(userId: String): Result<List<GroupMembershipRef>> {
        return try {
            val memberships = firestore.collection("users").document(userId)
                .collection("groups").get().await()

            Result.success(
                memberships.documents.map { doc ->
                    GroupMembershipRef(
                        groupId = doc.id,
                        displayName = doc.getString("displayName") ?: "Unknown",
                        joinedAt = doc.getTimestamp("joinedAt")?.toDate()?.time
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load memberships for user: $userId", e)
            Result.failure(e)
        }
    }

    /**
     * One day's totals for a group, ranked ascending — lowest km wins, per
     * `scrolla_project_summary.md`. A one-shot `get()`, never `onSnapshot()`
     * (SPRINT_LOG S2.4), so a leaderboard tab cannot hold an open listener.
     *
     * B only ever reads this collection; A's sync owns the writes
     * (`DATA_CONTRACT.md` §3.3).
     */
    suspend fun getGroupLeaderboard(groupId: String, date: String): Result<GroupLeaderboard> {
        return try {
            val snapshot = firestore.collection("groups").document(groupId)
                .collection("dailyTotals")
                .whereEqualTo("date", date)
                .get().await()

            val totals = snapshot.documents.mapNotNull { doc ->
                val userId = doc.getString("userId") ?: return@mapNotNull null
                MemberDailyTotal(
                    userId = userId,
                    displayName = doc.getString("displayName") ?: "Unknown",
                    totalKm = doc.getDouble("totalKm")?.toFloat() ?: return@mapNotNull null,
                    // Absent on every document written before 2026-09-15, and on
                    // any member still running an older build. getDouble returns
                    // null for a missing field, which is exactly the "no
                    // baseline" case the award already refuses to rank.
                    last7Km = doc.getDouble("last7Km")?.toFloat(),
                    prev7Km = doc.getDouble("prev7Km")?.toFloat()
                )
            }.sortedBy { it.totalKm }

            // Logged because the whole of 2.7a rests on this flag being true
            // when the server is unreachable, and that is a claim about
            // Firestore's behaviour on a real phone rather than about this
            // code. Verified on device with the radios off, 2026-09-14.
            if (snapshot.metadata.isFromCache) {
                Log.d(TAG, "Leaderboard served from CACHE for $groupId on $date (${totals.size} rows)")
            }

            Result.success(
                GroupLeaderboard(totals = totals, isFromCache = snapshot.metadata.isFromCache)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load leaderboard for group: $groupId on $date", e)
            Result.failure(e)
        }
    }

    /** The group's all-time record, or null if no record has been set yet. */
    suspend fun getGroupRecord(groupId: String): Result<GroupRecord?> {
        return try {
            val doc = firestore.collection("groups").document(groupId).get().await()
            val recordKm = doc.getDouble("recordKm")?.toFloat()
            val record = if (recordKm == null) {
                null
            } else {
                GroupRecord(
                    recordKm = recordKm,
                    recordHolder = doc.getString("recordHolder") ?: "Unknown",
                    recordDate = doc.getString("recordDate") ?: ""
                )
            }
            Result.success(record)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load record for group: $groupId", e)
            Result.failure(e)
        }
    }

    /**
     * Renames a group. Any member may do it — see `isGroupRename()` in
     * `firestore.rules` for why it is not creator-only.
     *
     * The name lives only on the group document; `getUserGroups()` reads it from
     * there rather than from the per-user membership record, so one write is
     * enough and no copies can drift.
     */
    suspend fun renameGroup(groupId: String, newName: String): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_GROUP_NAME_LENGTH) {
            return Result.failure(IllegalArgumentException("Group name must be 1–$MAX_GROUP_NAME_LENGTH characters"))
        }
        return try {
            firestore.collection("groups").document(groupId)
                .update("groupName", trimmed).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename group: $groupId", e)
            Result.failure(e)
        }
    }

    /**
     * Leaves a single group, taking this user's data in it with them.
     *
     * Removing only the `members` entry would leave their `dailyTotals`
     * documents behind, and the leaderboard reads those — so someone who had
     * left would keep appearing on the board with a live number, which is the
     * opposite of leaving.
     *
     * Shares [clearUserFromGroup] with account deletion, so the two cannot drift
     * apart: whatever "remove me from this group" means, it means the same thing
     * in both places.
     */
    suspend fun leaveGroup(groupId: String, userId: String, displayName: String): Result<Unit> {
        return try {
            val lastOut = isLastMember(groupId, userId)

            clearUserFromGroup(groupId, userId, displayName)
            firestore.collection("users").document(userId)
                .collection("groups").document(groupId)
                .delete().await()

            if (lastOut) deleteEmptyGroup(groupId)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to leave group: $groupId", e)
            Result.failure(e)
        }
    }

    /**
     * True when this user is the only member left, so leaving empties the group
     * (BUILD_PLAN 3.6).
     *
     * Read **before** the `arrayRemove`, not after. Afterwards the members array
     * no longer contains the caller, and the delete rule — which requires the
     * caller be the last member — could never pass again. The group would be
     * unreachable by anyone, forever, which is exactly the state 3.6 exists to
     * stop creating.
     */
    private suspend fun isLastMember(groupId: String, userId: String): Boolean {
        return try {
            val members = firestore.collection("groups").document(groupId)
                .get().await().get("members") as? List<*> ?: return false
            members.size == 1 && members.firstOrNull() == userId
        } catch (e: Exception) {
            // Fail closed: if we cannot tell, do not try to delete. A group left
            // behind is untidy; a group deleted out from under people is not
            // recoverable.
            Log.w(TAG, "Could not check membership before leaving $groupId: ${e.message}")
            false
        }
    }

    /**
     * Deletes a group the last member has just walked out of (BUILD_PLAN 3.6).
     *
     * Without this, every group anyone ever abandons stays in Firestore forever,
     * holding a six-character join code that can never be reached and can never
     * be reissued. Four group documents exist in this project today and two are
     * already in that state.
     *
     * **Failure here is deliberately not an error.** The `allow delete` rule
     * this needs is written but not yet deployed (REVIEW_LOG), so on current
     * production this call is denied — and a permission error must not turn a
     * successful "leave group" into a failure the user sees. They did leave. The
     * group being tidied up afterwards is housekeeping, and it is logged rather
     * than surfaced.
     *
     * The `dailyTotals` subcollection is already empty by this point:
     * `clearUserFromGroup` deletes the departing member's documents, and every
     * member before them deleted theirs on the way out. Firestore does not
     * cascade, so a group emptied any other way would leave those behind.
     */
    private suspend fun deleteEmptyGroup(groupId: String) {
        try {
            firestore.collection("groups").document(groupId).delete().await()
            Log.i(TAG, "Deleted group $groupId — its last member left")
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete empty group $groupId: ${e.message}")
        }
    }

    /**
     * Removes one user's presence from one group: their name off the record if
     * they hold it, their daily totals, then themselves from `members`.
     *
     * The order is not incidental. `isRecordImprovement()` requires the caller be
     * a member, so the record scrub must happen before the `arrayRemove` — after
     * it, that write is denied forever and a departed user's name is stuck on the
     * group's Hall of Fame permanently.
     *
     * Throws on failure so callers can decide; account deletion collects the
     * failures, leaving a group surfaces them.
     */
    private suspend fun clearUserFromGroup(groupId: String, userId: String, displayName: String) {
        val record = getGroupRecord(groupId).getOrNull()
        if (record != null && record.recordHolder == displayName) {
            firestore.collection("groups").document(groupId)
                .update(
                    mapOf(
                        "recordKm" to record.recordKm,
                        "recordHolder" to DELETED_HOLDER,
                        "recordDate" to record.recordDate
                    )
                ).await()
        }

        val totals = firestore.collection("groups").document(groupId)
            .collection("dailyTotals")
            .whereEqualTo("userId", userId)
            .get().await()
        for (doc in totals.documents) {
            doc.reference.delete().await()
        }

        firestore.collection("groups").document(groupId)
            .update("members", FieldValue.arrayRemove(userId))
            .await()
    }

    /**
     * Erases everything this user has in Firestore, ahead of deleting the
     * account itself.
     *
     * **Order is not incidental.** Every rule in `firestore.rules` is gated on
     * `signedIn()` and `request.auth.uid`, so all of this must happen while the
     * account still exists. Delete the Firebase user first and the data becomes
     * permanently unreachable — orphaned rows nobody can see or remove, in an
     * app whose deletion promise is the reason the screen exists.
     *
     * Removes, in order:
     *  1. every `dailyTotals` document the user wrote, in every group
     *  2. the user from each group's `members` array (`isSelfLeave()`)
     *  3. the user's own membership records under `/users/{uid}/groups/`
     *  4. the `/users/{uid}` profile document
     *
     * Best-effort per group: one group failing must not strand the rest. The
     * failures are collected and returned so the caller can decide whether the
     * account deletion should still proceed.
     */
    suspend fun deleteAllUserData(userId: String, displayName: String): Result<List<String>> {
        val problems = mutableListOf<String>()
        return try {
            val groups = getUserGroups(userId).getOrElse {
                return Result.failure(it)
            }

            for (group in groups) {
                try {
                    // Shared with leaveGroup(), so "remove me from this group"
                    // cannot come to mean two different things. Includes the
                    // record-name scrub, which must precede the arrayRemove:
                    // isRecordImprovement() requires membership, so afterwards
                    // that write is denied forever.
                    clearUserFromGroup(group.groupId, userId, displayName)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear user data from group ${group.groupId}", e)
                    problems += group.groupId
                }

                // 4. The user's own record of this membership. Deleted even if
                // the group-side cleanup failed, so the app stops showing a
                // group the user believes they have left.
                try {
                    firestore.collection("users").document(userId)
                        .collection("groups").document(group.groupId)
                        .delete().await()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete membership record ${group.groupId}", e)
                    problems += group.groupId
                }
            }

            // 5. The user's own backup of their daily distances.
            //
            // Must happen before the profile document goes, and must be counted
            // as a problem if it fails. Every rule on this collection is gated on
            // request.auth.uid, so a backup left behind after the account is gone
            // is unreachable by the user and undeletable by anyone — a permanent
            // orphan of the most complete record of their history the server
            // holds, created by the flow whose entire purpose is removal.
            try {
                val removed = BackupRepository(firestore).deleteAll(userId).getOrThrow()
                Log.d(TAG, "Deleted $removed backup day(s) for $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete personal backup for $userId", e)
                problems += "backup"
            }

            // 6. The profile document itself.
            firestore.collection("users").document(userId).delete().await()

            Result.success(problems.distinct())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user data for $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Writes a new group record if [candidateKm] beats the stored one.
     *
     * Lowest wins, so "beats" means strictly lower. Deliberately strict rather
     * than `<=`: an equal value is not an improvement, and rewriting the
     * document to change nothing but the holder's name would take the record
     * away from whoever set it first.
     *
     * The caller must have already established that [day] is eligible — see
     * [RecordEligibility]. This function does not re-check, because it cannot:
     * it has a number and a date, not the local history behind them.
     *
     * Returns true only when a write actually happened.
     *
     * `firestore.rules`' `isRecordImprovement()` permits this write only for a
     * member of the group, only on these three keys, and only downward. A
     * failure here is normal and not worth surfacing — someone else may have
     * improved the record between the read and the write.
     */
    suspend fun updateGroupRecordIfBetter(
        groupId: String,
        displayName: String,
        day: String,
        candidateKm: Float
    ): Result<Boolean> {
        return try {
            val existing = getGroupRecord(groupId).getOrElse { return Result.failure(it) }
            if (existing != null && candidateKm >= existing.recordKm) {
                return Result.success(false)
            }
            firestore.collection("groups").document(groupId)
                .update(
                    mapOf(
                        "recordKm" to candidateKm,
                        "recordHolder" to displayName,
                        "recordDate" to day
                    )
                )
                .await()
            Log.d(TAG, "Group record for $groupId set to $candidateKm km ($day)")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update record for group: $groupId", e)
            Result.failure(e)
        }
    }

    /**
     * Moves the primary flag to [groupId]. The primary group is the one the
     * widget shows, so exactly one membership may carry it.
     */
    suspend fun setPrimaryGroup(userId: String, groupId: String): Result<Unit> {
        return try {
            val memberships = firestore.collection("users").document(userId)
                .collection("groups").get().await()

            val batch = firestore.batch()
            memberships.documents.forEach { doc ->
                batch.update(doc.reference, "isPrimary", doc.id == groupId)
            }
            batch.commit().await()

            Log.d(TAG, "Primary group set to $groupId for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set primary group $groupId for user: $userId", e)
            Result.failure(e)
        }
    }
}
