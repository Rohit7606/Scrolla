package com.scrolla.firestore

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

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
    suspend fun createGroup(userId: String, displayName: String): Result<String> {
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

            // 2. Add user to group's members array
            groupRef.update("members", FieldValue.arrayUnion(userId)).await()

            // 3. Determine if this should be their primary group
            val userGroups = firestore.collection("users").document(userId).collection("groups").get().await()
            val isPrimary = userGroups.isEmpty

            // 4. Create user membership document
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
}
