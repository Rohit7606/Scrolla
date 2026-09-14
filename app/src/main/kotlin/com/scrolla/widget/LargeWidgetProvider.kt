package com.scrolla.widget

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.scrolla.R
import com.scrolla.model.DistanceFormatter
import com.scrolla.ui.ScrollaGraph
import java.time.LocalDate

/**
 * Large widget — the week, what it went into, and where it puts you
 * (SPRINT_LOG S3.9).
 *
 * The extra area buys context rather than a bigger number. Today's figure alone
 * says nothing about whether it is a good day; the seven-day chart is what makes
 * it mean something, and the app breakdown is the only view in the product that
 * answers "where did it actually go".
 *
 * **The per-app column never leaves the device.** It is read from Room and
 * rendered here; nothing about it is uploaded, which is the promise the privacy
 * screen makes and the reason a widget is allowed to show it at all.
 */
class LargeWidgetProvider : ScrollaWidgetProvider() {

    override val layoutId = R.layout.widget_large
    override val tag = "LargeWidget"

    override fun bind(context: Context, views: RemoteViews, snapshot: WidgetSnapshot) {
        views.setTextViewText(
            R.id.widget_value,
            DistanceFormatter.formatDisplayValue(snapshot.todayKm)
        )
        views.setTextViewText(
            R.id.widget_unit,
            DistanceFormatter.formatDisplayUnit(snapshot.todayKm)
        )
        views.setTextViewText(
            R.id.widget_landmark,
            snapshot.landmark
                ?.let { context.getString(R.string.widget_landmark_prefix, it) }
                ?: context.getString(R.string.widget_empty_landmark)
        )

        views.setImageViewBitmap(
            R.id.widget_chart,
            WeeklyChart.render(
                days = snapshot.week,
                today = LocalDate.now(),
                widthPx = dp(context, 150),
                heightPx = dp(context, 46),
                barColor = context.getColor(R.color.widget_text_mid),
                todayColor = context.getColor(R.color.widget_accent)
            )
        )

        bindApps(context, views, snapshot)
        bindBoard(context, views, snapshot)
    }

    private fun bindApps(context: Context, views: RemoteViews, snapshot: WidgetSnapshot) {
        val ids = listOf(R.id.widget_app_1, R.id.widget_app_2, R.id.widget_app_3)
        val apps = snapshot.topApps.take(ids.size)

        if (apps.isEmpty()) {
            views.setTextViewText(ids[0], context.getString(R.string.widget_no_apps_yet))
            ids.drop(1).forEach { views.setTextViewText(it, "") }
            return
        }

        ids.forEachIndexed { index, id ->
            val app = apps.getOrNull(index)
            views.setTextViewText(
                id,
                if (app == null) {
                    // Blank, not a placeholder dash. Two apps is a complete
                    // answer when only two were used; a third row of "--" would
                    // imply missing data rather than a short list.
                    ""
                } else {
                    // ScrollaGraph.appLabel turns com.instagram.android into
                    // Instagram, and falls back sensibly when the app has since
                    // been uninstalled — the history outlives the install.
                    val name = ScrollaGraph.appLabel(app.appPackage)
                    val km = DistanceFormatter.cmToKm(app.totalCm)
                    "$name  ${DistanceFormatter.formatDistance(km)}"
                }
            )
        }
    }

    private fun bindBoard(context: Context, views: RemoteViews, snapshot: WidgetSnapshot) {
        val ids = listOf(R.id.widget_board_1, R.id.widget_board_2, R.id.widget_board_3)

        // Hide the whole column rather than show an empty one. A solo user, or
        // one whose group has not synced, is not looking at a broken widget —
        // there is simply nothing to compare against yet, and the apps column
        // widens to use the space.
        if (snapshot.board.size < 2) {
            views.setViewVisibility(R.id.widget_board_column, View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_board_column, View.VISIBLE)

        // Lowest first — this is a reverse leaderboard, so the top row is the
        // person winning. Ordering it the usual way round would invert the
        // entire point of the product.
        val rows = snapshot.board.take(ids.size)
        ids.forEachIndexed { index, id ->
            val row = rows.getOrNull(index)
            views.setTextViewText(
                id,
                if (row == null) "" else {
                    val name = if (row.isSelf) context.getString(R.string.widget_board_self)
                    else row.displayName
                    "$name  ${DistanceFormatter.formatDistance(row.km)}"
                }
            )
        }
    }

    /** RemoteViews works in pixels; the chart needs a real size to draw into. */
    private fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}
