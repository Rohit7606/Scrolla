package com.scrolla.firestore

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs at most one sync pass at a time, and drops a pass that arrives too soon
 * after the last one started (BUILD_PLAN 1.3).
 *
 * Separate from `SyncViewModel` so the behaviour can be asserted on directly.
 * The ViewModel's collaborators are concrete Firebase classes, so a test that
 * went through it could only ever check the log again — which is the exact
 * failure mode that let this bug sit in plain sight: doubled output read as
 * "sync is working, twice".
 *
 * The two halves are both needed and neither substitutes for the other:
 *
 * - The **mutex** stops passes overlapping. Overlap is what raced
 *   `hasReconciled` into uploading the backup twice on 2026-09-14.
 * - The **spacing rule** ([SyncCadence]) drops the redundant pass once the
 *   first has finished. Without it the mutex would merely queue the duplicate
 *   and run it a moment later, doing all the same work.
 */
class SingleFlightSync(
    private val clock: () -> Long = System::currentTimeMillis,
    private val minSpacingMs: Long = SyncCadence.MIN_SYNC_SPACING_MS
) {

    private val mutex = Mutex()

    /** When the last pass began. 0 means none has. */
    private var lastStartedAt = 0L

    /**
     * @return true if [block] ran, false if this pass was dropped as redundant.
     *   Returned rather than only logged so a caller — and a test — can tell the
     *   two apart. Every exit path in this file's neighbours had to be given a
     *   log line for the same reason; here it can be a value instead.
     */
    suspend fun run(block: suspend () -> Unit): Boolean = mutex.withLock {
        val now = clock()
        if (!SyncCadence.shouldRun(lastStartedAt, now, minSpacingMs)) {
            return@withLock false
        }
        lastStartedAt = now
        block()
        true
    }

    /** How long ago the last pass began, for logging a dropped pass. */
    fun millisSinceLastStart(now: Long = clock()): Long = now - lastStartedAt
}
