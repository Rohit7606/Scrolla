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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The plumbing every Scrolla widget size needs (BUILD_PLAN 3.1).
 *
 * Three sizes, one copy of: the custom refresh action, the async Room read, the
 * failure handling, and the tap target. Subclasses supply only their layout and
 * how to fill it — which is the only part that genuinely differs.
 *
 * Written as a base class rather than three providers after watching the
 * landmark rule get independently reimplemented by a third consumer and lose
 * its guard. Anything three widgets need is not something three widgets should
 * each own.
 */
abstract class ScrollaWidgetProvider : AppWidgetProvider() {

    /** The layout this size inflates. */
    protected abstract val layoutId: Int

    /** Fills [views] from [snapshot]. Runs off the main thread. */
    protected abstract fun bind(context: Context, views: RemoteViews, snapshot: WidgetSnapshot)

    /** For logs, and for the Crashlytics grouping key. */
    protected abstract val tag: String

    /**
     * Handles [ACTION_REFRESH] ourselves and lets the framework handle the rest.
     *
     * Load-bearing: `AppWidgetProvider.onReceive` only calls [onUpdate] for an
     * `ACTION_APPWIDGET_UPDATE` that carries `EXTRA_APPWIDGET_IDS`, and neither
     * a repeating alarm nor a flush-time push can know ids that change whenever
     * a widget is added or removed. Scheduling that action would give an alarm
     * firing on time forever while the number never moved, with no error
     * anywhere. The refresh path looks the ids up at delivery instead.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            render(context, manager, manager.getAppWidgetIds(ComponentName(context, javaClass)))
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

    override fun onEnabled(context: Context) = ScrollaWidgets.scheduleRefresh(context)

    /**
     * Only stops the alarm when *no* Scrolla widget of any size remains.
     *
     * `onDisabled` fires when the last instance of **this** provider goes, which
     * is not the same thing — removing the small widget while a large one stays
     * would otherwise cancel the shared tick and freeze the survivor.
     */
    override fun onDisabled(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val anyLeft = ALL_PROVIDERS.any { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
        }
        if (!anyLeft) ScrollaWidgets.cancelRefresh(context)
    }

    private fun render(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return

        // goAsync: a receiver's onUpdate must return promptly and reading Room
        // suspends. Without it the process can be torn down mid-query and the
        // widget keeps whatever it last showed — which for a figure that is
        // meant to be today's is worse than showing nothing.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = WidgetSnapshot.load()
                val views = RemoteViews(context.packageName, layoutId).also {
                    bind(context, it, snapshot)
                    it.setOnClickPendingIntent(R.id.widget_root, openApp(context))
                }
                appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
                Log.d(tag, "Rendered ${appWidgetIds.size} widget(s) with ${snapshot.todayKm} km")
            } catch (e: Exception) {
                // A widget that throws is replaced by "Problem loading widget"
                // on someone's home screen, which reads as the app being broken
                // even when tracking is fine.
                Log.e(tag, "Widget render failed", e)
                CrashReporter.nonFatal(CrashReporter.Where.WIDGET_RENDER, e)
            } finally {
                pending.finish()
            }
        }
    }

    /** Tapping any size opens Home — S2.6. */
    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        // FLAG_IMMUTABLE is required from API 31 and correct regardless:
        // nothing should be able to rewrite this intent's extras.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        /**
         * Our own action, delivered explicitly to a named component.
         *
         * Not `ACTION_APPWIDGET_UPDATE` — see [onReceive].
         */
        const val ACTION_REFRESH = "com.scrolla.widget.ACTION_REFRESH"

        private val ALL_PROVIDERS = listOf(
            TodayWidgetProvider::class.java,
            MediumWidgetProvider::class.java,
            LargeWidgetProvider::class.java
        )

        /**
         * Repaints every placed Scrolla widget, whatever its size.
         *
         * Safe to call when none is placed: each provider's render returns
         * immediately on an empty id array, so the flush path does not need to
         * know or care whether the user has one.
         */
        fun refreshAll(context: Context) {
            ALL_PROVIDERS.forEach { provider ->
                context.sendBroadcast(
                    Intent(context, provider).apply { action = ACTION_REFRESH }
                )
            }
        }
    }
}
