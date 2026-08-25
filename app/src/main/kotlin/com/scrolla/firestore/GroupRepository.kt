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
    val memberCount: Int
)

/** One member's total for a single day, as stored under /groups/{id}/dailyTotals. */
data class MemberDailyTotal(
    val userId: String,
    val displayName: String,
    val totalKm: Float
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
                    memberCount = (groupDoc.get("members") as? List<*>)?.size ?: 0
                )
            }
            Result.success(groups)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load groups for user: $userId", e)
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
    suspend fun getGroupLeaderboard(groupId: String, date: String): Result<List<MemberDailyTotal>> {
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
                    totalKm = doc.getDouble("totalKm")?.toFloat() ?: return@mapNotNull null
                )
            }.sortedBy { it.totalKm }

            Result.success(totals)
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
                    // 1. Scrub the user's name off the group record if they hold
                    // it. SETTINGS_DELETE_BODY promises exactly this ("your name
                    // in group history will be replaced with '[deleted]'"), and
                    // without it a deleted account's name lives on indefinitely
                    // on the Hall of Fame of every group they were in.
                    //
                    // Must happen BEFORE leaving the group: isRecordImprovement()
                    // requires `request.auth.uid in resource.data.members`, so
                    // once the arrayRemove below lands, this write is denied
                    // forever. The record value itself is preserved — the rule
                    // permits an equal recordKm, so the group keeps its history
                    // and loses only the name.
                    val record = getGroupRecord(group.groupId).getOrNull()
                    if (record != null && record.recordHolder == displayName) {
                        firestore.collection("groups").document(group.groupId)
                            .update(
                                mapOf(
                                    "recordKm" to record.recordKm,
                                    "recordHolder" to DELETED_HOLDER,
                                    "recordDate" to record.recordDate
                                )
                            ).await()
                    }

                    // 2. This user's daily totals in this group. Queried rather
                    // than guessed by id, because the id encodes a date and we
                    // do not know which dates exist.
                    val totals = firestore.collection("groups")
                        .document(group.groupId)
                        .collection("dailyTotals")
                        .whereEqualTo("userId", userId)
                        .get().await()
                    for (doc in totals.documents) {
                        doc.reference.delete().await()
                    }

                    // 3. Leave the group.
                    firestore.collection("groups").document(group.groupId)
                        .update("members", FieldValue.arrayRemove(userId))
                        .await()
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

            // 5. The profile document itself.
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
