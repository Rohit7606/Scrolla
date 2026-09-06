package com.scrolla.firestore

import com.scrolla.room.DailyTotal

/**
 * Decides what to restore and what to upload when the device's daily totals and
 * the user's cloud backup disagree.
 *
 * Pure, so the merge rule can be tested rather than reasoned about. Getting this
 * wrong is not a cosmetic bug: this is a reverse leaderboard where the *lowest*
 * day wins, so a restore that overwrites a real day with a smaller one hands the
 * user a record they did not earn, and `isRecordImprovement()` in the rules only
 * permits equal-or-lower — meaning a bad value can never be corrected from any
 * client afterwards. That asymmetry is why the device wins every disagreement.
 */
object BackupReconcile {

    /**
     * @param toRestore cloud days the device has never seen — write these into
     *   Room. This is the uninstall/reinstall case, where local is empty.
     * @param toUpload local days the cloud is missing or has a different value
     *   for — write these up. Covers the first backup, days recorded while
     *   offline, and a value that changed after the day was first synced.
     */
    data class Plan(
        val toRestore: List<DailyTotal>,
        val toUpload: List<DailyTotal>
    ) {
        val isEmpty: Boolean get() = toRestore.isEmpty() && toUpload.isEmpty()
    }

    /**
     * **The device is authoritative for any day it has a row for.**
     *
     * When both sides know a day, the local value wins and is pushed up. The
     * cloud is a mirror, never a second opinion: the phone is where scrolling is
     * actually measured, and the backup only exists to survive that phone being
     * wiped. Preferring the cloud value would let a stale sync from an earlier
     * moment in the day overwrite the finished total.
     *
     * The consequence worth being explicit about: this is **not** multi-device
     * merge. Two phones tracking the same account would each claim their own
     * days and the last to sync would win. That is out of scope by design —
     * Scrolla measures one person's scrolling on the phone they scroll on.
     */
    fun plan(local: List<DailyTotal>, cloud: List<DailyTotal>): Plan {
        val localByDay = local.associateBy { it.day }
        val cloudByDay = cloud.associateBy { it.day }

        val toRestore = cloud.filter { it.day !in localByDay }

        val toUpload = local.filter { row ->
            val remote = cloudByDay[row.day]
            remote == null || !sameDistance(remote.totalKm, row.totalKm)
        }

        return Plan(toRestore = toRestore, toUpload = toUpload)
    }

    /**
     * Float equality with a tolerance, because these values make a round trip
     * through Firestore as doubles and back into a Kotlin Float. Comparing with
     * `!=` would find a difference in almost every row on almost every sync and
     * re-upload the entire history each time.
     *
     * A tenth of a millimetre, well below anything the sensor can resolve.
     */
    private fun sameDistance(a: Float, b: Float): Boolean =
        kotlin.math.abs(a - b) < TOLERANCE_KM

    const val TOLERANCE_KM = 0.0000001f
}
