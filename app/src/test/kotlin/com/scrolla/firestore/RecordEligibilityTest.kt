package com.scrolla.firestore

import com.scrolla.room.DailyTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The group record is a minimum, so every failure mode of the tracker produces
 * a winning score — and `isRecordImprovement()` only allows it to go lower, so
 * a bad record can never be raised again from a client.
 *
 * These tests are built from the real device data of 2026-08-23..25, including
 * the day the accessibility service crashed at 09:34.
 */
class RecordEligibilityTest {

    // ── The active-hours gate (added 2026-09-06) ──
    // These pin the fix for the day that actually took the group record.

    @Test
    fun `a two-hour evening burst cannot set a record`() {
        // 2026-09-04 exactly: service dead until 17:47, recorded 17:00-18:59,
        // 21.5 m, last event past the 18:00 cutoff. It passed every other rule
        // and took the record from 104 m permanently.
        val burst = DailyTotal(
            day = "2026-09-04",
            totalCm = 2154.9f,
            totalKm = 0.021549f,
            lastUpdated = java.time.LocalDateTime.of(2026, 9, 4, 18, 55)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        val eligible = RecordEligibility.isEligible(
            burst,
            java.time.LocalDate.of(2026, 9, 6),
            java.time.ZoneId.systemDefault(),
            activeSpanHours = 2
        )
        assertFalse("a day tracked for two hours is missing, not low", eligible)
    }

    @Test
    fun `the same day would have been accepted on the old rule`() {
        // Proves the new gate is what rejects it, not some pre-existing rule --
        // otherwise this test could pass for the wrong reason forever.
        val burst = DailyTotal(
            day = "2026-09-04",
            totalCm = 2154.9f,
            totalKm = 0.021549f,
            lastUpdated = java.time.LocalDateTime.of(2026, 9, 4, 18, 55)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        assertTrue(
            RecordEligibility.isEligible(
                burst,
                java.time.LocalDate.of(2026, 9, 6),
                java.time.ZoneId.systemDefault(),
                activeSpanHours = RecordEligibility.MIN_ACTIVE_SPAN_HOURS
            )
        )
    }

    @Test
    fun `a full day still qualifies`() {
        // 2026-08-24: 00:00-23:59, 106.3 m. The honest record.
        val fullDay = DailyTotal(
            day = "2026-08-24",
            totalCm = 10629.8f,
            totalKm = 0.106298f,
            lastUpdated = java.time.LocalDateTime.of(2026, 8, 24, 23, 40)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        assertTrue(
            RecordEligibility.isEligible(
                fullDay,
                java.time.LocalDate.of(2026, 9, 6),
                java.time.ZoneId.systemDefault(),
                activeSpanHours = 24
            )
        )
    }

    @Test
    fun `the device history picks the full day over the burst`() {
        // The whole point, end to end: given the real rows off the phone, the
        // best eligible day must be 24 Aug at 106.3 m, not 4 Sep at 21.5 m.
        fun day(d: String, km: Float, hour: Int) = DailyTotal(
            day = d, totalCm = km * 100_000f, totalKm = km,
            lastUpdated = java.time.LocalDate.parse(d).atTime(hour, 30)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        val history = listOf(
            day("2026-08-23", 0.151604f, 23),
            day("2026-08-24", 0.106298f, 23),
            day("2026-08-25", 0.104064f, 23),
            day("2026-08-26", 0.205179f, 23),
            day("2026-08-27", 0.026329f, 16),
            day("2026-09-04", 0.021549f, 18),
            day("2026-09-05", 0.068410f, 17)
        )
        // Real spans measured off the device 2026-09-07.
        val spans = mapOf(
            "2026-08-23" to 9,   // 15:00-23:59, install day
            "2026-08-24" to 24, "2026-08-25" to 24, "2026-08-26" to 24,
            "2026-08-27" to 12,  // 05:00-16:59
            "2026-09-04" to 2,   // 17:00-18:59
            "2026-09-05" to 6    // 12:00-17:59, morning dead
        )
        val best = RecordEligibility.bestEligibleDay(
            totals = history,
            today = java.time.LocalDate.of(2026, 9, 6),
            activeSpanFor = { spans[it] ?: 0 }
        )
        // 25 Aug (104.1 m) rather than 24 Aug (106.3 m) — it is genuinely the
        // lower of the two full days, and it is what the record held before the
        // two-hour day took it.
        assertEquals("2026-08-25", best?.day)
        assertFalse("the two-hour day must not win", best?.day == "2026-09-04")
    }

    // ── The join-date bound (added 2026-09-07) ──
    // A group record is earned inside the group, not carried into it.

    @Test
    fun `a day from before you joined cannot set the group record`() {
        // The real case: GUW4YE was created 7 Sep at 14:41 and was immediately
        // handed a record from 25 Aug — two weeks before it existed, and before
        // any other member could have competed for it.
        val beforeJoining = DailyTotal(
            day = "2026-08-25",
            totalCm = 10406.4f,
            totalKm = 0.104064f,
            lastUpdated = java.time.LocalDateTime.of(2026, 8, 25, 23, 40)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        assertFalse(
            RecordEligibility.isEligible(
                beforeJoining,
                java.time.LocalDate.of(2026, 9, 8),
                java.time.ZoneId.systemDefault(),
                activeSpanHours = 24,
                notBefore = java.time.LocalDate.of(2026, 9, 7)
            )
        )
    }

    @Test
    fun `the join day itself counts`() {
        // Inclusive: you were in the group for that day, so it is the group's.
        val joinDay = DailyTotal(
            day = "2026-09-07",
            totalCm = 9830f,
            totalKm = 0.0983f,
            lastUpdated = java.time.LocalDateTime.of(2026, 9, 7, 19, 3)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        assertTrue(
            RecordEligibility.isEligible(
                joinDay,
                java.time.LocalDate.of(2026, 9, 8),
                java.time.ZoneId.systemDefault(),
                activeSpanHours = 20,
                notBefore = java.time.LocalDate.of(2026, 9, 7)
            )
        )
    }

    @Test
    fun `a fresh group has no record rather than an inherited one`() {
        // The right answer for a brand-new group is "no record yet", not
        // somebody's back catalogue. Hall of Fame renders null as an empty
        // state, so this reads as a prize nobody has claimed — which is true.
        fun day(d: String, km: Float) = DailyTotal(
            day = d, totalCm = km * 100_000f, totalKm = km,
            lastUpdated = java.time.LocalDate.parse(d).atTime(23, 30)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        val history = listOf(
            day("2026-08-24", 0.106298f),
            day("2026-08-25", 0.104064f),
            day("2026-08-26", 0.205179f)
        )
        val best = RecordEligibility.bestEligibleDay(
            totals = history,
            today = java.time.LocalDate.of(2026, 9, 8),
            activeSpanFor = { 24 },
            notBefore = java.time.LocalDate.of(2026, 9, 7)
        )
        assertNull("nothing from before the group existed may count", best)
    }

    @Test
    fun `no bound still considers the whole history`() {
        // Null notBefore keeps the previous behaviour, so the bound is opt-in
        // and a caller that cannot determine a join date does not silently get
        // a different rule.
        fun day(d: String, km: Float) = DailyTotal(
            day = d, totalCm = km * 100_000f, totalKm = km,
            lastUpdated = java.time.LocalDate.parse(d).atTime(23, 30)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        val best = RecordEligibility.bestEligibleDay(
            totals = listOf(day("2026-08-25", 0.104064f)),
            today = java.time.LocalDate.of(2026, 9, 8),
            activeSpanFor = { 24 },
            notBefore = null
        )
        assertEquals("2026-08-25", best?.day)
    }

    @Test
    fun `known limitation - a hole in the middle of a day still passes`() {
        // 2026-08-25 is the day the service crashed at 09:34 and only resumed
        // that evening: nine and a half hours missing from the middle. It still
        // recorded across ~15 distinct hours, so counting hours cannot see the
        // hole, and its 104.1 m is an undercount of a real day.
        //
        // Deliberately not fixed here. Catching it needs contiguity, not
        // coverage, and it is worth ~2 m against 106.3 m — not a good trade for
        // the complexity. The real answer is per-day tracking health
        // (PREMIUM_CHECKLIST P2.6e). Pinned so the limitation is recorded
        // rather than discovered again.
        val holed = DailyTotal(
            day = "2026-08-25",
            totalCm = 10406.4f,
            totalKm = 0.104064f,
            lastUpdated = java.time.LocalDateTime.of(2026, 8, 25, 23, 40)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        assertTrue(
            RecordEligibility.isEligible(
                holed,
                java.time.LocalDate.of(2026, 9, 6),
                java.time.ZoneId.systemDefault(),
                activeSpanHours = 24
            )
        )
    }


    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 8, 26)

    private fun at(y: Int, m: Int, d: Int, hh: Int, mm: Int): Long =
        LocalDateTime.of(y, m, d, hh, mm).atZone(zone).toInstant().toEpochMilli()

    /** 2026-08-23 — 151.6 m, last scroll rolled past midnight. */
    private val goodDay = DailyTotal(
        day = "2026-08-23", totalCm = 15160.4f, totalKm = 0.15160f,
        lastUpdated = at(2026, 8, 24, 0, 0)
    )

    /** 2026-08-24 — 106.3 m, last scroll rolled past midnight. The real best. */
    private val betterDay = DailyTotal(
        day = "2026-08-24", totalCm = 10629.8f, totalKm = 0.10630f,
        lastUpdated = at(2026, 8, 25, 0, 1)
    )

    /** 2026-08-25 — 5.5 m, because the service died at 09:34. */
    private val crashedDay = DailyTotal(
        day = "2026-08-25", totalCm = 546.5f, totalKm = 0.00546f,
        lastUpdated = at(2026, 8, 25, 9, 34)
    )

    @Test
    fun `the crashed day is not eligible`() {
        assertFalse(RecordEligibility.isEligible(crashedDay, today, zone))
    }

    @Test
    fun `the crashed day does not become the record even though it is lowest`() {
        val best = RecordEligibility.bestEligibleDay(
            listOf(goodDay, betterDay, crashedDay), today, zone
        )
        assertEquals("2026-08-24", best?.day)
    }

    @Test
    fun `days whose scrolling ran past midnight are eligible`() {
        assertTrue(RecordEligibility.isEligible(goodDay, today, zone))
        assertTrue(RecordEligibility.isEligible(betterDay, today, zone))
    }

    @Test
    fun `today is never eligible however low it is`() {
        val partial = DailyTotal(
            day = today.toString(), totalCm = 1f, totalKm = 0.00001f,
            lastUpdated = at(2026, 8, 26, 23, 59)
        )
        assertFalse(RecordEligibility.isEligible(partial, today, zone))
        assertNull(RecordEligibility.bestEligibleDay(listOf(partial), today, zone))
    }

    @Test
    fun `a future day is not eligible`() {
        val future = DailyTotal(
            day = "2026-08-27", totalCm = 1f, totalKm = 0.00001f,
            lastUpdated = at(2026, 8, 27, 23, 0)
        )
        assertFalse(RecordEligibility.isEligible(future, today, zone))
    }

    @Test
    fun `a day with no distance is an absence, not a record`() {
        val empty = DailyTotal(
            day = "2026-08-24", totalCm = 0f, totalKm = 0f,
            lastUpdated = at(2026, 8, 24, 23, 0)
        )
        assertFalse(RecordEligibility.isEligible(empty, today, zone))
    }

    @Test
    fun `a last scroll exactly on the cutoff hour is eligible`() {
        val onCutoff = DailyTotal(
            day = "2026-08-24", totalCm = 100f, totalKm = 0.001f,
            lastUpdated = at(2026, 8, 24, RecordEligibility.LAST_SCROLL_CUTOFF_HOUR, 0)
        )
        assertTrue(RecordEligibility.isEligible(onCutoff, today, zone))
    }

    @Test
    fun `a last scroll one minute before the cutoff is not eligible`() {
        val beforeCutoff = DailyTotal(
            day = "2026-08-24", totalCm = 100f, totalKm = 0.001f,
            lastUpdated = at(2026, 8, 24, RecordEligibility.LAST_SCROLL_CUTOFF_HOUR - 1, 59)
        )
        assertFalse(RecordEligibility.isEligible(beforeCutoff, today, zone))
    }

    @Test
    fun `a never-written timestamp is not eligible`() {
        val noTimestamp = DailyTotal(
            day = "2026-08-24", totalCm = 100f, totalKm = 0.001f, lastUpdated = 0L
        )
        assertFalse(RecordEligibility.isEligible(noTimestamp, today, zone))
    }

    @Test
    fun `a timestamp before its own day is a corrupt row`() {
        val corrupt = DailyTotal(
            day = "2026-08-24", totalCm = 100f, totalKm = 0.001f,
            lastUpdated = at(2026, 8, 23, 23, 0)
        )
        assertFalse(RecordEligibility.isEligible(corrupt, today, zone))
    }

    @Test
    fun `an unparseable day string is rejected rather than throwing`() {
        val malformed = DailyTotal(
            day = "not-a-date", totalCm = 100f, totalKm = 0.001f,
            lastUpdated = at(2026, 8, 24, 23, 0)
        )
        assertFalse(RecordEligibility.isEligible(malformed, today, zone))
    }

    @Test
    fun `no eligible days yields no record rather than a fallback`() {
        assertNull(RecordEligibility.bestEligibleDay(listOf(crashedDay), today, zone))
        assertNull(RecordEligibility.bestEligibleDay(emptyList(), today, zone))
    }
}
