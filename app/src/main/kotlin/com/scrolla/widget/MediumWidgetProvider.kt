package com.scrolla.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.scrolla.R
import com.scrolla.model.DistanceFormatter
import com.scrolla.ui.screens.ScrollaFormatters

/**
 * Medium widget — today's distance and where it puts you (SPRINT_LOG S3.9).
 *
 * The extra width over the small size buys exactly one thing, so it shows
 * exactly one more: the group standing. Not a second number about the same
 * person — a reverse leaderboard is a social object, and "147 m" means
 * something different when it is also "2nd of 4".
 *
 * The standing column disappears rather than degrades when there is no honest
 * rank. `standingOf` refuses one when nobody else has synced, when the user
 * has not synced, and when the group is effectively solo — all cases where a
 * position would be invented. An empty right half is better than "1st of 1",
 * which makes the reverse mechanic look broken rather than won.
 */
class MediumWidgetProvider : ScrollaWidgetProvider() {

    override val layoutId = R.layout.widget_medium
    override val tag = "MediumWidget"

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

        val standing = snapshot.standing
        if (standing == null) {
            // Hide the divider too, or the card carries a rule with nothing on
            // the other side of it.
            views.setViewVisibility(R.id.widget_standing_column, View.GONE)
            views.setViewVisibility(R.id.widget_divider, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_standing_column, View.VISIBLE)
            views.setViewVisibility(R.id.widget_divider, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_rank,
                ScrollaFormatters.formatOrdinal(standing.rankPosition)
            )
            // "of 4 today in Flatmates" — the denominator is people who have
            // synced today, not group size. Saying "of 8" when five have not
            // reported would be a lie about both numbers; see GroupStanding.
            views.setTextViewText(
                R.id.widget_rank_detail,
                context.getString(
                    R.string.widget_rank_detail,
                    standing.groupSize,
                    standing.groupName
                )
            )
        }
    }
}
