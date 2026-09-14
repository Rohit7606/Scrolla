package com.scrolla.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceHealthDao {

    // --- Row lifecycle ---

    /**
     * Ensure the singleton row exists with safe defaults; no-op if the row is
     * already present (IGNORE strategy). Call this before any targeted UPDATE
     * so the UPDATE has a row to act on.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensureRowExists(state: ServiceHealthState)

    // A8 (AUDIT_A_TRACK.md): `upsert()` and `getOnce()` were removed here.
    //
    // Both were left over from the fetch-then-copy pattern of A's decision log
    // #4, and both had zero callers once the targeted updates below replaced
    // it. They are not neutral dead code: together they are precisely the
    // read-then-write pair that caused the lost-update race on this singleton
    // row, so keeping them available invites reintroducing the bug the targeted
    // queries exist to prevent. Row creation is `ensureRowExists` (IGNORE, so
    // it cannot clobber), and reading is `observe()`.

    // --- Reads ---

    @Query("SELECT * FROM service_health WHERE id = 1")
    fun observe(): Flow<ServiceHealthState?>

    // --- Targeted column updates ---
    // Each writer touches only the columns it owns. No read-then-write, no
    // race — a concurrent UPDATE on a different column set cannot clobber
    // this one's fields.

    /** Service lifecycle: set by onServiceConnected / onDestroy / onUnbind. */
    @Query("UPDATE service_health SET isServiceRunning = :running WHERE id = 1")
    suspend fun updateServiceRunning(running: Boolean)

    /** Accessibility master-switch state: set by MainActivity / BootCompletedReceiver. */
    @Query("UPDATE service_health SET isAccessibilityServiceEnabled = :enabled WHERE id = 1")
    suspend fun updateAccessibilityEnabled(enabled: Boolean)

    /**
     * Successful Room flush: stamps both timestamps (from different sources)
     * and clears any prior degraded reason.
     * - [flushTs]: when the flush completed (System.currentTimeMillis at flush time)
     * - [eventTs]: when the most recent event was received (@Volatile in-memory field)
     */
    @Query("UPDATE service_health SET lastRoomFlushTimestamp = :flushTs, lastEventTimestamp = :eventTs, degradedReason = null WHERE id = 1")
    suspend fun markFlushSuccess(flushTs: Long, eventTs: Long)

    /**
     * Failed Room flush: records the reason and still stamps lastEventTimestamp
     * so staleness detection knows events were arriving even though writes failed.
     */
    @Query("UPDATE service_health SET degradedReason = :reason, lastEventTimestamp = :eventTs WHERE id = 1")
    suspend fun markFlushFailed(reason: String, eventTs: Long)

    /** Successful Firestore sync: stamps the sync timestamp and clears degraded. */
    @Query("UPDATE service_health SET lastFirestoreSyncTimestamp = :timestamp, degradedReason = null WHERE id = 1")
    suspend fun markSyncSuccess(timestamp: Long)

    /**
     * Deleted, not deprecated (BUILD_PLAN 2.7).
     *
     * `markSyncFailed` wrote a *network* failure into `degradedReason`, which
     * the Settings health card reads as a *tracking* failure — so a user with
     * no signal was told "Tracking may be limited ... this may be a battery
     * restriction" and pointed at battery settings, while tracking was working
     * perfectly. That is finding A6 with a different caller; see the comment in
     * `ScrollRepository.triggerFirestoreSync()`.
     *
     * Removed rather than left unused, because an unused DAO method that writes
     * the wrong field is an invitation. A failed sync is visible in the two
     * places it should be: `lastFirestoreSyncTimestamp` stops advancing, which
     * the card renders as "Last synced ...", and Crashlytics gets a non-fatal.
     *
     * If sync health ever needs its own user-facing state, it needs its own
     * column — P2.4e — not this one back.
     */
}