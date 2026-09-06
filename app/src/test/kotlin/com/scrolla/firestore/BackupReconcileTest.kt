package com.scrolla.firestore

import com.scrolla.room.DailyTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the device-wins merge rule.
 *
 * The stake here is higher than "a number is briefly wrong". Scrolla is a
 * reverse leaderboard, so a restore that overwrites a real day with a smaller
 * one hands the user a record they did not earn — and `isRecordImprovement()`
 * only ever permits equal-or-lower, so no client can raise it back afterwards.
 * A bad value is permanent short of editing Firestore by hand.
 */
class BackupReconcileTest {

    private fun row(day: String, km: Float, lastUpdated: Long = 0L) =
        DailyTotal(day = day, totalCm = km * 100_000f, totalKm = km, lastUpdated = lastUpdated)

    /** 2026-09-04 at 21:30 local — a day that finished after the 18:00 cutoff. */
    private val eveningStamp = 1_788_000_000_000L

    // ---- the reinstall case, which is the whole point ----

    @Test
    fun `an empty device restores the entire backup`() {
        val cloud = listOf(row("2026-09-04", 0.021f), row("2026-09-05", 0.068f))
        val plan = BackupReconcile.plan(local = emptyList(), cloud = cloud)

        assertEquals(listOf("2026-09-04", "2026-09-05"), plan.toRestore.map { it.day })
        assertTrue("nothing to send up from an empty device", plan.toUpload.isEmpty())
    }

    @Test
    fun `a first backup uploads everything and restores nothing`() {
        val local = listOf(row("2026-09-04", 0.021f), row("2026-09-05", 0.068f))
        val plan = BackupReconcile.plan(local = local, cloud = emptyList())

        assertEquals(2, plan.toUpload.size)
        assertTrue(plan.toRestore.isEmpty())
    }

    // ---- the rule that protects the record ----

    @Test
    fun `when both sides know a day the device value wins`() {
        // The cloud holds a stale mid-morning sync; the device has the finished
        // day. Restoring the cloud value would replace 21.5 m with 4 m — and on
        // a lowest-wins leaderboard that is a permanent fake record.
        val local = listOf(row("2026-09-04", 0.0215f))
        val cloud = listOf(row("2026-09-04", 0.0040f))

        val plan = BackupReconcile.plan(local = local, cloud = cloud)

        assertTrue("a known day must never be restored over", plan.toRestore.isEmpty())
        assertEquals(1, plan.toUpload.size)
        assertEquals(0.0215f, plan.toUpload.single().totalKm, 1e-7f)
    }

    @Test
    fun `a smaller cloud value does not sneak in even though lower wins`() {
        val local = listOf(row("2026-08-26", 0.205f))
        val cloud = listOf(row("2026-08-26", 0.001f))

        val plan = BackupReconcile.plan(local = local, cloud = cloud)

        assertTrue(plan.toRestore.isEmpty())
        assertEquals(0.205f, plan.toUpload.single().totalKm, 1e-7f)
    }

    // ---- the partial case: some days each side ----

    @Test
    fun `days are reconciled independently in both directions`() {
        val local = listOf(row("2026-09-05", 0.068f), row("2026-09-06", 0.007f))
        val cloud = listOf(row("2026-09-04", 0.021f), row("2026-09-05", 0.068f))

        val plan = BackupReconcile.plan(local = local, cloud = cloud)

        assertEquals("only the day the device lacks", listOf("2026-09-04"), plan.toRestore.map { it.day })
        assertEquals("only the day the cloud lacks", listOf("2026-09-06"), plan.toUpload.map { it.day })
    }

    // ---- the float round-trip, which decides whether this is quiet or noisy ----

    @Test
    fun `an unchanged history uploads nothing`() {
        // Values make a round trip Float -> Firestore double -> Float. Comparing
        // with != would find a difference in nearly every row on nearly every
        // launch and re-upload the whole history each time.
        val local = listOf(row("2026-08-23", 0.1516044f), row("2026-08-24", 0.1062977f))
        val cloud = local.map { row(it.day, it.totalKm.toDouble().toFloat()) }

        val plan = BackupReconcile.plan(local = local, cloud = cloud)

        assertTrue("a settled history must be silent", plan.isEmpty)
    }

    @Test
    fun `a real change is still detected`() {
        // Today's total grows through the day; the tolerance must not swallow it.
        val local = listOf(row("2026-09-06", 0.0068f))
        val cloud = listOf(row("2026-09-06", 0.0067f))

        val plan = BackupReconcile.plan(local = local, cloud = cloud)

        assertEquals(1, plan.toUpload.size)
    }

    @Test
    fun `two identical empty sides produce no work`() {
        assertTrue(BackupReconcile.plan(emptyList(), emptyList()).isEmpty)
    }

    // ---- lastUpdated, which decides whether a restored day can ever win ----

    @Test
    fun `a cloud row missing lastUpdated is re-uploaded even when the distance matches`() {
        // The repair path for documents written before that field was carried.
        // Without it the distances match forever, the row is never fixed, and
        // RecordEligibility silently rejects the day after every restore.
        val local = listOf(row("2026-09-04", 0.021f, lastUpdated = eveningStamp))
        val cloud = listOf(row("2026-09-04", 0.021f, lastUpdated = 0L))

        val plan = BackupReconcile.plan(local = local, cloud = cloud)

        assertEquals(1, plan.toUpload.size)
        assertEquals(eveningStamp, plan.toUpload.single().lastUpdated)
    }

    @Test
    fun `a restored day keeps the timestamp that makes it record-eligible`() {
        val cloud = listOf(row("2026-09-04", 0.021f, lastUpdated = eveningStamp))
        val plan = BackupReconcile.plan(local = emptyList(), cloud = cloud)

        assertEquals(eveningStamp, plan.toRestore.single().lastUpdated)
    }

    @Test
    fun `matching rows with matching timestamps stay silent`() {
        val local = listOf(row("2026-09-04", 0.021f, lastUpdated = eveningStamp))
        val cloud = listOf(row("2026-09-04", 0.021f, lastUpdated = eveningStamp))

        assertTrue(BackupReconcile.plan(local = local, cloud = cloud).isEmpty)
    }

    @Test
    fun `a local row with no timestamp does not trigger endless re-upload`() {
        // Local 0 vs cloud 0 is equal anyway; local 0 vs a cloud value must not
        // loop, since uploading a 0 would not change what the cloud holds.
        val local = listOf(row("2026-09-04", 0.021f, lastUpdated = 0L))
        val cloud = listOf(row("2026-09-04", 0.021f, lastUpdated = eveningStamp))

        assertTrue(BackupReconcile.plan(local = local, cloud = cloud).isEmpty)
    }
}
