package com.scrolla.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.scrolla.MainActivity
import com.scrolla.R
import com.scrolla.diagnostics.CrashReporter
import com.scrolla.model.DistanceFormatter
import com.scrolla.ui.ScrollaGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Today's distance on the home screen (BUILD_PLAN 3.1, SPRINT_LOG S2.6).
 *
 * **Why this is worth building at all.** Everything else in Scrolla requires
 * someone to open it. A tracker you have to remember to open is a tracker you
 * stop opening, and the number this app exists to show is one someone should
 * meet without deciding to. This is the only passive surface in the product.
 *
 * Reads the same `getTodayTotalKm()` the Home screen reads, and formats it with
 * the same `DistanceFormatter`, so the widget and the app can never disagree
 * about the figure — which they would within a week if either did its own
 * rounding.
 */
class TodayWidgetProvider : AppWidgetProvider() {

    /**
     * Handles [ACTION_REFRESH] ourselves, and lets the framework handle the
     * rest.
     *
     * This override is load-bearing rather than decorative.
     * `AppWidgetProvider.onReceive` only calls [onUpdate] when an
     * `ACTION_APPWIDGET_UPDATE` broadcast carries `EXTRA_APPWIDGET_IDS` — and a
     * repeating alarm cannot know those ids, because they change as widgets are
     * added and removed. A scheduled `ACTION_APPWIDGET_UPDATE` with no ids
     * therefore arrives, matches, and does **nothing**: the alarm fires on time
     * forever and the number never moves, with no error anywhere. That is the
     * exact failure shape this project keeps paying for, so the refresh path
     * looks the ids up at delivery time instead.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TodayWidgetProvider::class.java)
            )
            render(context, manager, ids)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        render(context, appWidgetManager, appWidgetIds)
    }

    /**
     * First instance placed — start the tick.
     *
     * Idempotent in [ScrollaWidgets], so it does not matter that this can fire
     * alongside an app launch that also schedules it.
     */
    override fun onEnabled(context: Context) {
        ScrollaWidgets.scheduleRefresh(context)
    }

    /** Last instance removed. Nothing is reading the alarm any more. */
    override fun onDisabled(context: Context) {
        ScrollaWidgets.cancelRefresh(context)
    }

    private fun render(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return

        // goAsync: a broadcast receiver's onUpdate must return promptly, and
        // reading Room is a suspending call. Without this the process can be
        // torn down mid-query and the widget keeps whatever it last showed —
        // which for a number that is supposed to be today's is worse than
        // showing nothing.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val km = ScrollaGraph.scrollRepository.getTodayTotalKm()
                val views = buildViews(context, km)
                appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
                Log.d(TAG, "Rendered ${appWidgetIds.size} widget(s) with ${km} km")
            } catch (e: Exception) {
                // A widget that throws is a widget Android replaces with
                // "Problem loading widget" on the user's home screen, which
                // looks like the app is broken even when tracking is fine.
                Log.e(TAG, "Widget render failed", e)
                CrashReporter.nonFatal(CrashReporter.Where.WIDGET_RENDER, e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun buildViews(context: Context, km: Float): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)

        // Same formatter as Home, so the widget cannot round differently.
        views.setTextViewText(R.id.widget_value, DistanceFormatter.formatDisplayValue(km))
        views.setTextViewText(R.id.widget_unit, DistanceFormatter.formatDisplayUnit(km))

        // `landmarkComparison`, not `nearestLandmark`. The first version of this
        // called the latter and shipped two bugs at once, both of which the app
        // had already solved and neither of which was visible until the widget
        // sat on a real home screen:
        //
        //  1. It rendered "about the height of Eiffel Tower height". The
        //     landmark names already carry their own descriptor, and the
        //     `HOME_LANDMARK_PREFIX` string that suggested otherwise had no
        //     callers — dead copy that read like the app's phrasing and was not.
        //  2. It showed the nearest landmark unconditionally, so ten metres of
        //     scrolling would have claimed the Eiffel Tower. Home and the recap
        //     card both guard against exactly that; the guard lived privately in
        //     each of them, so this third consumer never saw it.
        //
        // Null means no honest comparison yet — either nothing is measured, or
        // the figure is not near any landmark. Both are better said plainly
        // than compared to something it is not.
        val landmark = DistanceFormatter.landmarkComparison(km)
        views.setTextViewText(
            R.id.widget_landmark,
            if (landmark == null) {
                context.getString(R.string.widget_empty_landmark)
            } else {
                context.getString(R.string.widget_landmark_prefix, landmark)
            }
        )

        // Tapping opens Home — S2.6. FLAG_IMMUTABLE is required from API 31 and
        // is correct here regardless: nothing should be able to rewrite this
        // intent's extras.
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)

        return views
    }

    companion object {
        private const val TAG = "TodayWidget"

        /**
         * Our own action, delivered explicitly to this component.
         *
         * Not `ACTION_APPWIDGET_UPDATE`: that one is only acted on when it
         * carries `EXTRA_APPWIDGET_IDS`, which neither a repeating alarm nor a
         * flush-time push can supply. See [onReceive].
         */
        const val ACTION_REFRESH = "com.scrolla.widget.ACTION_REFRESH"

        /**
         * Repaints every placed instance. Safe to call from anywhere, including
         * when no widget exists — [render] returns immediately on an empty id
         * array, so the flush path does not need to know or care whether the
         * user has placed one.
         */
        fun refresh(context: Context) {
            context.sendBroadcast(
                Intent(context, TodayWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                }
            )
        }
    }
}
