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
