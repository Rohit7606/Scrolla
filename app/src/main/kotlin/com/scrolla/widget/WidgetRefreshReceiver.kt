package com.scrolla.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * What the refresh alarm actually wakes up (BUILD_PLAN 3.1).
 *
 * **One alarm, three widgets.** A `PendingIntent` names a single component, so
 * pointing the alarm straight at a provider refreshes that size and no other —
 * the small widget would have kept current while a medium or large one sat
 * frozen on the same home screen, with nothing logged and nothing to notice
 * except a number that stopped moving. This receiver exists so the tick fans
 * out to every size instead.
 *
 * Three alarms would have worked too and been worse: three wakeups to redraw
 * the same figure from the same query.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ScrollaWidgetProvider.refreshAll(context)
    }
}
