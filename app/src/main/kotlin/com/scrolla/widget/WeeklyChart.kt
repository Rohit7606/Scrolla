package com.scrolla.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.scrolla.room.DailyTotal
import java.time.LocalDate

/**
 * The seven-day bar chart for the large widget (SPRINT_LOG S3.9).
 *
 * **Drawn to a Bitmap rather than built from Views.** RemoteViews cannot size a
 * child at runtime below API 31 (`setViewLayoutHeight` is 31+), and `minSdk` is
 * 24 — so a bar chart made of weighted Views would silently render every bar
 * the same height on a third of the devices this ships to. A Bitmap behaves
 * identically everywhere.
 *
 * Kept free of Android UI state so the geometry can be reasoned about: it takes
 * the days and the colours and returns a picture.
 */
object WeeklyChart {

    /**
     * @param days up to seven totals, oldest first. Missing days are drawn as
     *   empty slots rather than skipped, because a gap in a week is information
     *   — a bar chart that closes up around absent days shows a tidier week
     *   than the one that happened.
     * @param today used to tint the current day differently; it is the only bar
     *   still moving.
     */
    fun render(
        days: List<DailyTotal>,
        today: LocalDate,
        widthPx: Int,
        heightPx: Int,
        barColor: Int,
        todayColor: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            widthPx.coerceAtLeast(1),
            heightPx.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val slots = 7
        val byDay = days.associateBy { it.day }
        // The week ending today, so the rightmost bar is always now. Building
        // from whatever rows exist would slide the axis around as days go
        // missing, and "the last seven days" would stop meaning that.
        val window = (0 until slots).map { today.minusDays((slots - 1 - it).toLong()) }

        val max = window.mapNotNull { byDay[it.toString()]?.totalKm }.maxOrNull() ?: 0f

        val gap = widthPx * 0.035f
        val barWidth = (widthPx - gap * (slots - 1)) / slots
        val radius = barWidth * 0.28f

        window.forEachIndexed { index, date ->
            val km = byDay[date.toString()]?.totalKm ?: 0f
            val left = index * (barWidth + gap)

            // A floor of two pixels so an empty day is a visible slot rather
            // than a hole. Zero is a fact about that day, not an absence of one.
            val barHeight = if (max <= 0f) {
                2f
            } else {
                (km / max * (heightPx - 2f)).coerceAtLeast(2f)
            }

            paint.color = if (date == today) todayColor else barColor
            // Past days sit back so today reads first; they are context, not
            // the subject.
            paint.alpha = if (date == today) 255 else 150

            canvas.drawRoundRect(
                RectF(left, heightPx - barHeight, left + barWidth, heightPx.toFloat()),
                radius,
                radius,
                paint
            )
        }

        paint.alpha = 255
        paint.color = Color.TRANSPARENT
        return bitmap
    }
}
