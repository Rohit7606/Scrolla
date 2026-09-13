package com.scrolla.firestore

/**
 * Decides whether a sync pass should actually run, given when the last one
 * started.
 *
 * Pure, so the rule can be asserted on rather than read off a log line. The
 * duplicate-pass bug this exists to close (BUILD_PLAN 1.3) was visible for days
 * as doubled log output that everyone read as "sync is working, twice" — the
 * decision was never anywhere a test could reach it.
 *
 * **The bug was two triggers, not two ViewModels.** `MainShell` starts
 * `runPeriodicSync()`, whose first pass fires immediately, *and* registers
 * `RefreshOnResume`. Adding a lifecycle observer to an already-RESUMED owner
 * dispatches `ON_RESUME` straight away, so both fire within ~400 ms of every
 * launch. Measured on device 2026-09-14 01:02:49.598 / 01:02:50.016 — one
 * `(foreground)` and one `(periodic)`, one process, one thread. The 01:17 and
 * 01:18 ticks each produced a single pass, which is what rules out a second
 * instance: a second ViewModel would run its own timer too.
 *
 * At one user that was harmless noise. At fifty it doubles every read, every
 * write, and the 82 % of Scrolla's battery cost that is already network.
 */
object SyncCadence {

    /**
     * How long after a pass starts another one is treated as redundant.
     *
     * Must comfortably exceed how long a pass takes, because the caller
     * serialises passes: the second trigger waits for the first to finish and
     * only then asks this question, so it measures from the first pass's
     * *start*. A full pass took ~6 s on device (01:02:49.598 → 01:02:55.525),
     * and that is with two groups and a year of backup history.
     *
     * Sixty seconds is that with room to spare, and still far below
     * `FIRESTORE_SYNC_INTERVAL_MS`, so the fifteen-minute timer is never
     * throttled by it. The cost of the window is that backgrounding the app and
     * returning inside a minute does not re-sync — which is nothing, because
     * nothing it reads can meaningfully have changed in that minute.
     */
    const val MIN_SYNC_SPACING_MS = 60_000L

    /**
     * @param lastStartedAt when the previous pass began, or 0 if none has.
     * @param now current time, same clock as [lastStartedAt].
     * @param minSpacingMs see [MIN_SYNC_SPACING_MS].
     *
     * The first pass of a session always runs: `lastStartedAt == 0L` is "never
     * synced", not "synced at the epoch". Without that case an app launched
     * when the clock is near zero — or in a test using small numbers — would
     * skip the one pass that matters most.
     *
     * A clock that moves backwards (NTP correction, user edit) also runs rather
     * than skips. Running twice costs one redundant pass; skipping wrongly can
     * strand a user off their group's board until the next tick.
     */
    fun shouldRun(
        lastStartedAt: Long,
        now: Long,
        minSpacingMs: Long = MIN_SYNC_SPACING_MS
    ): Boolean {
        if (lastStartedAt == 0L) return true
        val elapsed = now - lastStartedAt
        if (elapsed < 0L) return true
        return elapsed >= minSpacingMs
    }
}
