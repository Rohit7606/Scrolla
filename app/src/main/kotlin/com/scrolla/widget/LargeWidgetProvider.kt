package com.scrolla.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.scrolla.R
import com.scrolla.model.DistanceFormatter
import com.scrolla.ui.ScrollaGraph

/**
 * Large widget — the week, what it went into, and where it puts you
 * (SPRINT_LOG S3.9).
 *
 * The extra area buys context rather than a bigger number. Today's figure alone
 * says nothing about whether it is a good day; the seven-day chart and the three
 * totals under it are what make it mean something, and the app breakdown is the
 * only view in the product that answers "where did it actually go".
 *
 * **The per-app column never leaves the device.** It is read from Room and
 * rendered here; nothing about it is uploaded, which is the promise the privacy
 * screen makes and the reason a widget is allowed to show it at all.
 */
class LargeWidgetProvider : ScrollaWidgetProvider() {

    override val layoutId = R.layout.widget_large
    override val tag = "LargeWidget"

    private val appRowIds = listOf(
        R.id.widget_app_1, R.id.widget_app_2, R.id.widget_app_3,
        R.id.widget_app_4, R.id.widget_app_5
    )

    private val boardRowIds = listOf(
        R.id.widget_board_1, R.id.widget_board_2, R.id.widget_board_3,
        R.id.widget_board_4, R.id.widget_board_5
    )

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
            WeeklyChart.forWidget(context, snapshot.week, widthDp = 150, heightDp = 52)
        )

        bindWeekStats(views, snapshot)
        bindApps(context, views, snapshot)
        bindBoard(context, views, snapshot)
    }

    /**
     * The chart shows the shape of the week; these show its size. Same seven
     * days, no extra query — see [WidgetSnapshot.weekStats] for why the average
     * divides by seven rather than by the days that have rows.
     */
    private fun bindWeekStats(views: RemoteViews, snapshot: WidgetSnapshot) {
        val stats = snapshot.weekStats()
        views.setTextViewText(R.id.widget_week_total, DistanceFormatter.formatDistance(stats.totalKm))
        views.setTextViewText(R.id.widget_week_avg, DistanceFormatter.formatDistance(stats.averageKm))
        views.setTextViewText(R.id.widget_week_best, DistanceFormatter.formatDistance(stats.bestKm))
    }

    private fun bindApps(context: Context, views: RemoteViews, snapshot: WidgetSnapshot) {
        val apps = snapshot.topApps.take(appRowIds.size)

        if (apps.isEmpty()) {
            views.setTextViewText(appRowIds[0], context.getString(R.string.widget_no_apps_yet))
            views.setViewVisibility(appRowIds[0], View.VISIBLE)
            appRowIds.drop(1).forEach { views.setViewVisibility(it, View.GONE) }
            return
        }

        appRowIds.forEachIndexed { index, id ->
            val app = apps.getOrNull(index)
            if (app == null) {
                // GONE, not an empty string. An empty TextView still occupies a
                // line, so two apps used to render as two rows and three blank
                // ones — a list that looked like it had failed to load the rest.
                views.setViewVisibility(id, View.GONE)
            } else {
                views.setViewVisibility(id, View.VISIBLE)
                // ScrollaGraph.appLabel turns com.instagram.android into
                // Instagram, and falls back sensibly when the app has since
                // been uninstalled — the history outlives the install.
                val name = ScrollaGraph.appLabel(app.appPackage)
                val km = DistanceFormatter.cmToKm(app.totalCm)
                views.setTextViewText(id, "$name  ${DistanceFormatter.formatDistance(km)}")
            }
        }
    }

    private fun bindBoard(context: Context, views: RemoteViews, snapshot: WidgetSnapshot) {
        // Hide the whole column rather than show an empty one. A solo user, or
        // one whose group has not synced, is not looking at a broken widget —
        // there is simply nothing to compare against yet, and the apps column
        // widens to use the space.
        if (snapshot.board.size < 2) {
            views.setViewVisibility(R.id.widget_board_column, View.GONE)
            // And the hairline goes with it. It has an id for exactly this: the
            // first version hid the column and left the rule standing alone
            // against the right edge of the card, which on a home screen reads
            // as a widget that failed halfway through drawing.
            views.setViewVisibility(R.id.widget_board_divider, View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_board_column, View.VISIBLE)
        views.setViewVisibility(R.id.widget_board_divider, View.VISIBLE)

        // Lowest first — this is a reverse leaderboard, so the top row is the
        // person winning. Ordering it the usual way round would invert the
        // entire point of the product.
        val rows = snapshot.board.take(boardRowIds.size)
        boardRowIds.forEachIndexed { index, id ->
            val row = rows.getOrNull(index)
            if (row == null) {
                views.setViewVisibility(id, View.GONE)
            } else {
                views.setViewVisibility(id, View.VISIBLE)
                val name = if (row.isSelf) context.getString(R.string.widget_board_self)
                else row.displayName
                views.setTextViewText(id, "$name  ${DistanceFormatter.formatDistance(row.km)}")
            }
        }
    }
}
