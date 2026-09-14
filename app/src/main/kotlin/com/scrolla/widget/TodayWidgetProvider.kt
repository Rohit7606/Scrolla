package com.scrolla.widget

import android.content.Context
import android.widget.RemoteViews
import com.scrolla.R
import com.scrolla.model.DistanceFormatter

/**
 * Small widget — today's distance (BUILD_PLAN 3.1, SPRINT_LOG S2.6).
 *
 * **Why this size exists at all.** Everything else in Scrolla requires someone
 * to open it. A tracker you have to remember to open is a tracker you stop
 * opening, and the number this app exists to show is one someone should meet
 * without deciding to. This is the only passive surface in the product.
 *
 * Formats with the same [DistanceFormatter] Home uses, so the widget and the
 * app cannot disagree about the figure — which they would within a week if
 * either did its own rounding.
 */
class TodayWidgetProvider : ScrollaWidgetProvider() {

    override val layoutId = R.layout.widget_today
    override val tag = "TodayWidget"

    override fun bind(context: Context, views: RemoteViews, snapshot: WidgetSnapshot) {
        views.setTextViewText(
            R.id.widget_value,
            DistanceFormatter.formatDisplayValue(snapshot.todayKm)
        )
        views.setTextViewText(
            R.id.widget_unit,
            DistanceFormatter.formatDisplayUnit(snapshot.todayKm)
        )

        // `snapshot.landmark` is already the guarded comparison, never the raw
        // nearest one. The first version of this file called `nearestLandmark()`
        // directly and would have told someone ten metres of scrolling was the
        // Eiffel Tower — the rule was private to two ViewModels and this was the
        // third consumer. It now lives in DistanceFormatter.landmarkComparison.
        views.setTextViewText(
            R.id.widget_landmark,
            snapshot.landmark
                ?.let { context.getString(R.string.widget_landmark_prefix, it) }
                ?: context.getString(R.string.widget_empty_landmark)
        )
    }
}
