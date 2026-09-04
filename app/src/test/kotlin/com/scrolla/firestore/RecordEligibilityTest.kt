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
