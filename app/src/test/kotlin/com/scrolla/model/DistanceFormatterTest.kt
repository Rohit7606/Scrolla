package com.scrolla.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PREMIUM_CHECKLIST P0.1b.
 *
 * Flagged on day one as the cheapest high-value test in the repo and written
 * last, which is its own small lesson. Three shipped bugs live in here:
 * the delta chip that labelled metres as km, the "0.0 km" that was a real
 * number faithfully rendered, and the "0 m" shown for apps that had genuinely
 * been scrolled.
 */
class DistanceFormatterTest {

    // ---- unit selection ----

    @Test
    fun `below a rounded kilometre the unit is metres`() {
        assertEquals("m", DistanceFormatter.formatDisplayUnit(0.5f))
        assertEquals("m", DistanceFormatter.formatDisplayUnit(0.999f))
    }

    @Test
    fun `at a rounded kilometre the unit flips to km`() {
        // 999.5 m rounds to "1000 m", which should read as 1.0 km instead.
        assertEquals("km", DistanceFormatter.formatDisplayUnit(0.9995f))
        assertEquals("km", DistanceFormatter.formatDisplayUnit(1f))
    }

    @Test
    fun `the crossover is not off by one at either side`() {
        assertEquals("m", DistanceFormatter.formatDisplayUnit(0.99949f))
        assertEquals("km", DistanceFormatter.formatDisplayUnit(0.99951f))
    }

    // ---- value and unit must agree ----

    @Test
    fun `formatDistance never labels metres as km`() {
        // The delta chip shipped "$magnitude km" with a metre magnitude, so a
        // 20 m change read "20 km". Value and unit come from one call now.
        for (km in listOf(0.0001f, 0.02f, 0.5f, 0.9f, 0.9994f, 1f, 2.8f, 42.2f)) {
            val combined = DistanceFormatter.formatDistance(km)
            val value = DistanceFormatter.formatDisplayValue(km)
            val unit = DistanceFormatter.formatDisplayUnit(km)
            assertEquals("$value $unit", combined)
        }
    }

    @Test
    fun `a real day of scrolling reads in metres, not as zero point zero km`() {
        // 1,764.5 cm was the first real device reading. "0.0 km" was correct
        // and useless; metres is the readable form.
        assertEquals("18 m", DistanceFormatter.formatDistance(DistanceFormatter.cmToKm(1764.5f)))
    }

    // ---- sub-metre honesty ----

    @Test
    fun `a sub-metre distance is not rendered as zero`() {
        // From a real export: Telegram 0.4 m, launcher 0.3 m, both shown as
        // "0 m" and so indistinguishable from never having been opened.
        assertEquals("<1 m", DistanceFormatter.formatDistance(0.0004f))
        assertEquals("<1 m", DistanceFormatter.formatDistance(0.0003f))
    }

    @Test
    fun `a genuine zero is still zero`() {
        assertEquals("0 m", DistanceFormatter.formatDistance(0f))
    }

    @Test
    fun `half a metre and above rounds normally`() {
        assertEquals("1 m", DistanceFormatter.formatDistance(0.0005f))
        assertEquals("1 m", DistanceFormatter.formatDistance(0.0009f))
    }

    @Test
    fun `the spoken form stays grammatical below a metre`() {
        // TalkBack would otherwise read the literal "<1 metres".
        assertEquals("less than one metre", DistanceFormatter.formatDistanceSpoken(0.0004f))
        assertTrue(DistanceFormatter.formatDistanceSpoken(0.05f).endsWith("metres"))
        assertTrue(DistanceFormatter.formatDistanceSpoken(2f).endsWith("kilometres"))
    }

    // ---- conversion ----

    @Test
    fun `cmToKm divides by a hundred thousand`() {
        assertEquals(1f, DistanceFormatter.cmToKm(100_000f), 0.00001f)
        assertEquals(0f, DistanceFormatter.cmToKm(0f), 0.00001f)
    }

    @Test
    fun `formatting pins to a dot decimal regardless of locale`() {
        // A comma decimal would corrupt the CSV export and break parsing.
        assertTrue(DistanceFormatter.formatKm(1.25f).contains('.'))
        assertTrue(DistanceFormatter.formatDisplayValue(2.8f).contains('.'))
    }

    // ---- landmarks: the contract, and the gate that hides it ----

    @Test
    fun `nearestLandmark returns the nearest entry however absurd the match`() {
        // This is contract, not a bug. HomeViewModel gates it by ratio; if the
        // gate ever moves into here, that test will fail and this one will too.
        val (name, _) = DistanceFormatter.nearestLandmark(0.001f)!!
        assertEquals("Eiffel Tower height", name)
    }

    @Test
    fun `nearestLandmark returns null at or below zero`() {
        assertNull(DistanceFormatter.nearestLandmark(0f))
        assertNull(DistanceFormatter.nearestLandmark(-1f))
    }

    @Test
    fun `a heavy real day lands on a landmark that is actually true`() {
        // 830 m is the Burj Khalifa, the onboarding hook worth using.
        val (name, _) = DistanceFormatter.nearestLandmark(0.83f)!!
        assertEquals("Burj Khalifa height", name)
    }
}
