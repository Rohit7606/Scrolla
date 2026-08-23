package com.scrolla.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.scrolla.R

/**
 * Two faces, six styles.
 *
 * The previous scale defined 15 styles and then overrode them inline at
 * more than forty call sites — fontWeight, letterSpacing and lineHeight
 * re-specified per screen, which is how the vertical rhythm drifted
 * apart. These six are correct at the source and are used verbatim.
 *
 * InstrumentSerif carries every number and title. It ships one weight by
 * design; do not declare Medium or Bold for it or Compose will
 * synthesise a fake bold.
 *
 * SpaceGrotesk carries every label, row and paragraph.
 */

val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold)
)

/**
 * The six. Everything on a redesigned screen is one of these, unmodified.
 */
object ScrollaType {

    /**
     * The hero figure. Tabular so digits do not jitter as the value
     * changes.
     *
     * `Trim.Both` plus `includeFontPadding = false` is what lets the
     * figure sit tight against the label above and the landmark below
     * without a line height smaller than the font size, which would
     * clip the glyphs at this scale.
     */
    val Figure = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 96.sp,
        lineHeight = 96.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = "tnum",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

    /** Any number in a row, card or stat strip. */
    val FigureSmall = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

    /**
     * The middle figure — a card's headline number, a chart readout.
     *
     * Exists so call sites stop writing `FigureSmall.copy(fontSize = x * 1.5f)`,
     * which silently leaves the 26sp line height behind and clips the glyph.
     */
    val FigureMedium = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

    /**
     * Multi-line display headlines — the sign-in question, the onboarding
     * question, recap headers.
     *
     * Kept well apart from [Figure]. Figure is a SINGLE-LINE numeral style
     * whose line height is tuned to its own cap height; using it for a
     * headline that wraps is what made the sign-in screen collide with
     * itself.
     */
    val Headline = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.5).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

    /** Screen titles and names. */
    val Display = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

    /** The landmark line — the best copy in the app, sized like it. */
    val Editorial = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 22.sp,
        lineHeight = 29.sp,
        letterSpacing = 0.sp
    )

    /** Paragraphs and card bodies. */
    val Body = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp
    )

    /** List rows — names, app names, settings labels. */
    val Row = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )

    /** Section labels. Uppercase at the call site, never in the style. */
    val Micro = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.98.sp
    )

    /** Captions, dates, units. */
    val Caption = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp
    )
}

/**
 * Material's slots, filled from the same two faces so the screens that
 * have not been reworked yet inherit the new voice rather than Inter.
 */
val Typography = Typography(
    // NOT Figure. Four screens use displayLarge for wrapping headlines;
    // Figure is opt-in, by name, for the one number that is the whole screen.
    displayLarge = ScrollaType.Headline,
    displayMedium = ScrollaType.Display.copy(fontSize = 44.sp, lineHeight = 48.sp),
    displaySmall = ScrollaType.Display.copy(fontSize = 34.sp, lineHeight = 38.sp),

    headlineLarge = ScrollaType.Display,
    headlineMedium = ScrollaType.Display.copy(fontSize = 28.sp, lineHeight = 32.sp),
    headlineSmall = ScrollaType.Display.copy(fontSize = 24.sp, lineHeight = 28.sp),

    titleLarge = ScrollaType.Row,
    titleMedium = ScrollaType.Body,
    titleSmall = ScrollaType.Body.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),

    bodyLarge = ScrollaType.Body,
    bodyMedium = ScrollaType.Caption.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
    bodySmall = ScrollaType.Caption,

    labelLarge = ScrollaType.Body.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = ScrollaType.Caption.copy(fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall = ScrollaType.Micro
)
