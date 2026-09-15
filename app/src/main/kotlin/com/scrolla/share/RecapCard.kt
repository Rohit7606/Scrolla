package com.scrolla.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.scrolla.R
import com.scrolla.model.DistanceFormatter
import com.scrolla.ui.screens.ScrollaStrings
import java.io.File

/**
 * The weekly recap as a picture someone can actually post (BUILD_PLAN 3.2).
 *
 * This is the only organic growth loop in the product, and until now the share
 * button sent a line of text — honest, and invisible in a feed. A reverse
 * leaderboard is a strange enough idea that it has to be *seen* to be explained;
 * "I scrolled 1.2 km this week" without the framing reads as a boast about the
 * wrong thing.
 *
 * **Drawn on a Canvas, not captured from Compose.** Capturing the screen would
 * tie the image to whatever the phone was rendering — the viewer's theme, their
 * font scale, their display cutout — and a share card has to look the same
 * coming off every device or it stops being recognisable as one thing.
 *
 * **It is the recap screen, frozen.** Not a second design of the same numbers:
 * same centred column, same glow, same enormous serif figure with the unit set
 * below it rather than beside it. A share card that looks like a different
 * product than the one it came from teaches the viewer nothing about the app.
 *
 * **Always dark.** The palette is the dark one from `values-night/colors.xml`,
 * hardcoded, regardless of the phone's theme: the card lands in someone else's
 * feed, where the only thing that makes it Scrolla's is that it looks like
 * Scrolla's. (Scrolla is dark-primary — decided 2026-09-02.)
 */
object RecapCard {

    /** 4:5. Fits Instagram's feed and stories without either one cropping it. */
    const val WIDTH_PX = 1080
    const val HEIGHT_PX = 1350

    private const val PAD = 90f

    // The surface and text ladders from the design system, by hand: a Canvas
    // has no theme to read. If Color.kt changes, change these.
    //
    // GROUND, not Card. The recap screen sits on colorScheme.background, and a
    // card that came out one rung up the ladder is a slightly different product
    // to the one the viewer would go and download.
    private const val GROUND = 0xFF121110.toInt()
    private const val TEXT_HI = 0xFFF5F1EE.toInt()
    private const val TEXT_MID = 0xFFA39D98.toInt()
    private const val TEXT_LOW = 0xFF857E78.toInt()
    private const val ACCENT = 0xFFED6A5A.toInt()

    /** The screen's glow: the accent at alpha 0.15. */
    private const val ACCENT_GLOW = 0x26ED6A5A

    /**
     * The caption that travels with the image.
     *
     * Pure, and separate from the drawing, because this is the half that is
     * actually assertable — whether the number in the text matches the number on
     * the card is a real defect and not one you would catch by looking.
     */
    fun shareText(weekKm: Float): String = String.format(
        ScrollaStrings.RECAP_SHARE_TEMPLATE,
        DistanceFormatter.formatDistance(weekKm)
    )

    fun render(
        context: Context,
        weekKm: Float,
        landmark: String?
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(GROUND)

        // Two faces, and only two. Inter is not one of them — it is retired from
        // the app type system and survives only in the widget layouts, which is
        // a separate debt. Instrument Serif carries numbers, titles and the one
        // lyrical line; Space Grotesk carries every label and paragraph.
        val serif = font(context, R.font.instrument_serif_regular)
        val serifItalic = font(context, R.font.instrument_serif_italic)
        val grotesk = font(context, R.font.space_grotesk_medium)
        val groteskRegular = font(context, R.font.space_grotesk_regular)

        val centreX = WIDTH_PX / 2f

        // ─── ambient glow ───────────────────────────────────────
        // The recap screen's glow, frozen. On screen it drifts between 0.3 and
        // 0.7 of the height over eight seconds and its radius breathes between
        // 1.0x and 1.5x the width; a still picture has to pick one frame, so it
        // picks the middle of both. Alpha 0.15 over the card colour, exactly as
        // the screen composes it.
        canvas.drawPaint(
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    centreX,
                    HEIGHT_PX * 0.5f,
                    WIDTH_PX * 1.25f,
                    ACCENT_GLOW,
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
        )

        // **A knowing exception to "no grain".** The design system bans texture
        // outright, and this is texture — at alpha 8/255, under a gradient, to
        // stop it banding. A 38-step alpha ramp across 675 px lands on visible
        // concentric rings in 8-bit colour, which is the artefact that makes a
        // picture look cheap for no reason anyone can name.
        //
        // The rule exists to keep backgrounds flat and undecorated, and this
        // serves that rather than defying it. But it is a rule and it is being
        // bent, so it is written down rather than smuggled in: delete this one
        // line to comply strictly, and the banding comes back.
        dither(canvas)

        // ─── section label ──────────────────────────────────────
        // Text-mid, not coral. The design system names this exact mistake by
        // name: section labels are "never the accent. Coral section labels were
        // the original accent leak." The recap screen still makes it — it paints
        // this label with colorScheme.primary and the figure with onBackground,
        // which is the rule inverted. Mirrored faithfully, the card would have
        // carried the leak into everyone else's feed.
        val sectionLabel = paint(grotesk, 34f, TEXT_MID).apply {
            letterSpacing = 0.18f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(ScrollaStrings.RECAP_HEADLINE.uppercase(), centreX, 300f, sectionLabel)

        // ─── the figure ─────────────────────────────────────────
        // Coral, because this is the hero figure — one of the four things in the
        // whole product allowed to carry the accent. Unit below the number
        // rather than beside it, as the screen sets it.
        val valuePaint = paint(serif, 300f, ACCENT).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(DistanceFormatter.formatDisplayValue(weekKm), centreX, 620f, valuePaint)

        val unitPaint = paint(groteskRegular, 80f, TEXT_MID).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(DistanceFormatter.formatDisplayUnit(weekKm), centreX, 720f, unitPaint)

        val suffix = paint(groteskRegular, 44f, TEXT_MID).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(ScrollaStrings.RECAP_STAT_SUFFIX, centreX, 800f, suffix)

        // ─── landmark ───────────────────────────────────────────
        // Serif italic: this is the landmark line, the one lyrical line a screen
        // is allowed, and the system sets it in Editorial italic while
        // everything else stays plain.
        //
        // Only drawn when `landmarkComparison` allowed one. A card is the worst
        // place to overstate a comparison: it outlives the app that made it.
        if (landmark != null) {
            drawWrapped(
                canvas = canvas,
                text = landmark,
                // No textAlign on the paint: StaticLayout centres via its own
                // Alignment, and setting both shifts the block by half a line
                // width on top of that.
                paint = textPaint(serifItalic, 58f, TEXT_HI),
                x = PAD,
                top = 930f,
                width = (WIDTH_PX - PAD * 2).toInt(),
                alignment = Layout.Alignment.ALIGN_CENTER
            )
        }

        // ─── footer ─────────────────────────────────────────────
        // There is no wordmark lettering asset in the repo, so a standalone
        // wordmark is set in Instrument Serif italic — the system's own
        // instruction for exactly this case. The mark is never redrawn.
        val footer = paint(serifItalic, 46f, TEXT_LOW).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(ScrollaStrings.RECAP_FOOTER, centreX, 1180f, footer)

        // The one line the screen does not have and the card must. On screen
        // the rules of the game are the app around it; in someone else's feed
        // there is no app around it, and a distance with no framing reads as a
        // boast about the wrong thing.
        val tagline = paint(groteskRegular, 34f, TEXT_MID).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(ScrollaStrings.RECAP_SHARE_TAGLINE, centreX, 1258f, tagline)

        return bitmap
    }

    /**
     * Writes the card into `cacheDir/share` and returns a shareable
     * `content://` URI.
     *
     * Cache and not files, and the directory is wiped first — both for the same
     * reasons as `DataExport.writeToCache`: this is a hand-off rather than
     * storage, and last week's card must not be attachable by a stale chooser
     * entry. `file_paths.xml` exposes this one subdirectory and not the cache
     * root, which also holds Firestore's local persistence.
     */
    fun writeToCache(context: Context, bitmap: Bitmap, dateStamp: String): Uri {
        val dir = File(context.cacheDir, "share")
        if (dir.exists()) dir.listFiles()?.forEach { it.delete() } else dir.mkdirs()
        val file = File(dir, "scrolla-week-$dateStamp.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Tiles a small square of low-alpha noise over the whole canvas.
     *
     * A tile rather than a full-size noise bitmap: 128x128 is enough to break
     * up banding and is 90x less memory than generating 1080x1350 of random
     * pixels for something nobody is meant to see.
     */
    private fun dither(canvas: Canvas) {
        val size = 128
        val pixels = IntArray(size * size)
        val random = java.util.Random(7) // Fixed seed: the same card twice is the same card.
        for (i in pixels.indices) {
            // Alternating black and white specks, so the noise averages out to
            // the colour underneath instead of lightening it.
            val a = random.nextInt(9)
            pixels[i] = if (random.nextBoolean()) {
                (a shl 24) or 0xFFFFFF
            } else {
                a shl 24
            }
        }
        val tile = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        canvas.drawPaint(
            Paint().apply {
                shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
        )
    }

    private fun font(context: Context, id: Int): Typeface? =
        // Null falls back to the platform default rather than throwing. A card
        // in the wrong face is worth shipping; a crash on the share button is
        // not, and a missing font is exactly the kind of thing that survives
        // every debug build and breaks in a minified one.
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()

    private fun paint(typeface: Typeface?, size: Float, colorInt: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            color = colorInt
        }

    private fun textPaint(typeface: Typeface?, size: Float, colorInt: Int) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            color = colorInt
        }

    /** Draws wrapped text and returns the y coordinate just below it. */
    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        top: Float,
        width: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): Float {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1.15f)
            .build()
        canvas.save()
        canvas.translate(x, top)
        layout.draw(canvas)
        canvas.restore()
        return top + layout.height
    }
}
