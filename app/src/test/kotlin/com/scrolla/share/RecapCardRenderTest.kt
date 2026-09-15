package com.scrolla.share

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.scrolla.ui.ScreenTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the share card with real pixels and writes it to
 * `build/recap-card.png`, so the thing that ends up in someone's feed can be
 * looked at without tapping a button on a phone (BUILD_PLAN 3.2).
 *
 * MIUI refuses adb input injection, so nobody can drive the share button from a
 * script on the one device this project tests on; the alternative to this is
 * shipping a picture no one has seen. Type will fall back to the platform font
 * here — Robolectric has no font assets — so this proves geometry, colour and
 * content, and the typeface still has to be confirmed on hardware.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ScreenTestApplication::class)
class RecapCardRenderTest {

    private fun render(): Bitmap = RecapCard.render(
        context = ApplicationProvider.getApplicationContext(),
        weekKm = 3.05f,
        landmark = "that's about 10 Eiffel Towers"
    )

    @Test
    fun `renders at the declared size and is not blank`() {
        val bitmap = render()

        assertEquals(RecapCard.WIDTH_PX, bitmap.width)
        assertEquals(RecapCard.HEIGHT_PX, bitmap.height)

        // Dark, not an exact value. The glow tints every pixel on the card and
        // the dither moves each one a little further, so pinning the corner to
        // the flat card colour was a test that only passed before either
        // existed — it broke on the first real design change rather than on a
        // real defect.
        val corner = bitmap.getPixel(4, 4)
        assertTrue(
            "the ground should stay dark, was ${Integer.toHexString(corner)}",
            android.graphics.Color.red(corner) < 0x50 &&
                android.graphics.Color.green(corner) < 0x50 &&
                android.graphics.Color.blue(corner) < 0x50
        )

        // Counted across the whole card, not sampled at a fixed row. The first
        // version of this assertion read one scanline at y=560 and broke the
        // moment the layout was retimed — a test that fails when the design
        // changes rather than when the drawing stops working is worse than no
        // test, because the reflex is to move the number until it passes.
        //
        // What must be true is that the accent is on the card at all: the
        // figure and today's bar are the only things drawn in it, and if
        // neither appeared the card is a blank rectangle that every size and
        // colour assertion here would still pass.
        var accentPixels = 0
        for (x in 0 until bitmap.width step 4) {
            for (y in 0 until bitmap.height step 4) {
                if (bitmap.getPixel(x, y) == 0xFFED6A5A.toInt()) accentPixels++
            }
        }
        assertNotEquals("nothing was drawn in the accent colour", 0, accentPixels)
    }

    @Test
    fun `writes a png a human can look at`() {
        val out = File("build/recap-card.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { render().compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals(true, out.length() > 0)
    }
}
