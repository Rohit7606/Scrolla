package com.scrolla.firestore

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.scrolla.room.DailyTotal
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * The user's own backup of their daily distances — `users/{uid}/dailyTotals`.
 *
 * **Why this exists.** Sign-in used to buy the user groups and a leaderboard and
 * nothing else. Their totals did reach Firestore, but only as a side effect of
 * belonging to a group: the sync loops over the user's groups, so someone in no
 * group synced nothing at all, and the documents lived under the group rather
 * than under the person. Nothing ever read them back. An uninstall — including
 * an accidental one — took the entire history with it.
 *
 * **What is deliberately not here.** `scroll_events` (which apps, at which hour)
 * never leaves the device. Only daily distance totals are backed up, which is
 * the same data already published to the group leaderboard, so this adds no new
 * category of information to the server. Android Auto Backup covers the per-app
 * history separately, into the user's own Drive quota rather than ours.
 */
class BackupRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private fun collection(userId: String) =
        firestore.collection("users").document(userId).collection("dailyTotals")

    /**
     * Reads the user's whole cloud backup.
     *
     * Rows that cannot be parsed are skipped rather than failing the read: a
     * single malformed document must not cost the user every other day of their
     * history, and there is nothing they could do about it if it did.
     */
    suspend fun fetchAll(userId: String): Result<List<DailyTotal>> = runCatching {
        withTimeout(TIMEOUT_MS) {
            collection(userId).get().await().documents.mapNotNull { doc ->
                val day = doc.getString("date") ?: doc.id
                val km = doc.getDouble("totalKm") ?: return@mapNotNull null
                DailyTotal(
                    day = day,
                    totalCm = (km * CM_PER_KM).toFloat(),
                    totalKm = km.toFloat(),
                    // The backup does not carry lastUpdated. It is a local
                    // health signal — "when did this device last flush" — and
                    // restoring another moment's value would be a fabrication.
                    lastUpdated = 0L
                )
            }
        }
    }.onFailure { Log.e(TAG, "fetchAll() failed for $userId", it) }

    /**
     * Writes days up in one batch.
     *
     * Batched because this runs on the first sync after an install with a full
     * history behind it — one round trip instead of a few hundred. Firestore
     * caps a batch at 500 operations, so longer histories are chunked.
     */
    suspend fun upload(userId: String, totals: List<DailyTotal>): Result<Int> = runCatching {
        if (totals.isEmpty()) return@runCatching 0
        withTimeout(TIMEOUT_MS) {
            var written = 0
            for (chunk in totals.chunked(BATCH_LIMIT)) {
                val batch = firestore.batch()
                for (row in chunk) {
                    batch.set(
                        collection(userId).document(row.day),
                        mapOf(
                            "date" to row.day,
                            "totalKm" to row.totalKm,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                }
                batch.commit().await()
                written += chunk.size
            }
            written
        }
    }.onFailure { Log.e(TAG, "upload() failed for $userId", it) }

    /**
     * Deletes the whole backup. Called from account deletion.
     *
     * Adding a cloud store means account deletion has to cover it, or "delete my
     * account" quietly becomes a lie — the user's full day-by-day history would
     * outlive the account it belonged to, unreachable by them and undeletable by
     * anyone, since every rule here is gated on `request.auth.uid`.
     */
    suspend fun deleteAll(userId: String): Result<Int> = runCatching {
        withTimeout(TIMEOUT_MS) {
            val docs = collection(userId).get().await().documents
            var deleted = 0
            for (chunk in docs.chunked(BATCH_LIMIT)) {
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
                deleted += chunk.size
            }
            deleted
        }
    }.onFailure { Log.e(TAG, "deleteAll() failed for $userId", it) }

    private companion object {
        const val TAG = "BackupRepository"
        const val TIMEOUT_MS = 15_000L
        const val BATCH_LIMIT = 500
        const val CM_PER_KM = 100_000.0
    }
}
