package com.scrolla.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The landmark line, as each surface actually renders it.
 *
 * This exists because the bug it pins reached WhatsApp. On 2026-09-15 the
 * weekly recap shared an image reading **"that's about 1 km walk"** — the short
 * label, which is written to follow "≈ " where articles are dropped, pasted
 * into a sentence that needed one. An image cannot be patched after it is sent.
 *
 * It is the third time this seam has given way. The widget once rendered "about
 * the height of Eiffel Tower height" the same way. The honesty *guard* was
 * correctly centralised each time; the *wording* was what quietly forked, and
 * nothing was asserting on the finished sentence.
 *
 * So these assert the composed strings, exactly as a user reads them — not that
 * the function returns something non-null.
 */
class LandmarkPhraseTest {

    /** Each surface's lead-in, applied the way the ViewModel applies it. */
    private fun asCaption(km: Float) = DistanceFormatter.landmarkComparison(km)?.let { "≈ $it" }
    private fun asSentence(km: Float) = DistanceFormatter.landmarkPhrase(km)?.let { "that's about $it" }

    @Test
    fun `the sentence that went out broken now reads correctly`() {
        assertEquals("that's about a one-kilometre walk", asSentence(1.1f))
    }

    @Test
    fun `every landmark reads correctly in a sentence`() {
        // One distance inside each landmark's honest band. If a landmark is
        // added or reworded without a phrase, this is what fails.
        assertEquals("that's about the height of the Eiffel Tower", asSentence(0.163f))
        assertEquals("that's about the height of the Empire State Building", asSentence(0.443f))
        assertEquals("that's about the height of the Burj Khalifa", asSentence(0.830f))
        assertEquals("that's about a one-kilometre walk", asSentence(1.0f))
        assertEquals("that's about the height of two Burj Khalifas", asSentence(1.66f))
        assertEquals("that's about Everest base camp to the summit", asSentence(3.8f))
        assertEquals("that's about the full height of Mount Everest", asSentence(8.8f))
        assertEquals("that's about a marathon", asSentence(42.2f))
        assertEquals("that's about the altitude the ISS orbits at", asSentence(400.0f))
    }

    @Test
    fun `the caption form keeps dropping its articles`() {
        // The two forms are deliberately different. Fixing the sentence by
        // rewriting the labels would have broken every widget and Home, where
        // "≈ the height of the Eiffel Tower" is not how a caption is set.
        assertEquals("≈ Eiffel Tower height", asCaption(0.163f))
        assertEquals("≈ 1 km walk", asCaption(1.0f))
    }

    @Test
    fun `both forms refuse together`() {
        // The guard is shared, so a distance nowhere near a landmark must
        // produce nothing on either surface. Ten metres is the case that once
        // shipped as "the Eiffel Tower".
        assertNull(DistanceFormatter.landmarkComparison(0.01f))
        assertNull(DistanceFormatter.landmarkPhrase(0.01f))

        assertNull(DistanceFormatter.landmarkComparison(0f))
        assertNull(DistanceFormatter.landmarkPhrase(0f))
    }

    @Test
    fun `both forms agree on when there is an answer`() {
        // They must never disagree: a widget showing a comparison while the
        // recap shows none would be the same data contradicting itself across
        // two screens of one product.
        var km = 0.01f
        while (km < 500f) {
            val caption = DistanceFormatter.landmarkComparison(km)
            val sentence = DistanceFormatter.landmarkPhrase(km)
            assertEquals(
                "caption and sentence disagreed at $km km",
                caption == null,
                sentence == null
            )
            km *= 1.1f
        }
        assertNotNull(DistanceFormatter.landmarkPhrase(1.0f))
    }
}
