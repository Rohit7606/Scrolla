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

    private fun row(day: String, km: Float) =
        DailyTotal(day = day, totalCm = km * 100_000f, totalKm = km, lastUpdated = 0L)

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
}
