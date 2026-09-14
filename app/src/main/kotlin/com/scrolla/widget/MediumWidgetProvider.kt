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
 * The standing never degrades into a guess. `standingOf` refuses a rank when
 * nobody else has synced, when the user has not synced, and when the group is
 * effectively solo — all cases where a position would be invented. "1st of 1"
 * makes the reverse mechanic look broken rather than won.
 *
 * What replaces it is the week, not a blank. Refusing the rank is right;
 * leaving the right half of a 4x2 empty because of it is not, and that is the
 * state every user is in before their first friend joins.
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
            // The week takes the space instead of nothing taking it. Hiding the
            // column was the first version and it left the right sixty per cent
            // of the card empty on device — a correct refusal to invent a rank,
            // expressed as a hole in the layout.
            views.setViewVisibility(R.id.widget_standing_column, View.GONE)
            views.setViewVisibility(R.id.widget_week_column, View.VISIBLE)
            views.setImageViewBitmap(
                R.id.widget_week_chart,
                WeeklyChart.forWidget(context, snapshot.week, widthDp = 150, heightDp = 56)
            )
        } else {
            views.setViewVisibility(R.id.widget_standing_column, View.VISIBLE)
            views.setViewVisibility(R.id.widget_week_column, View.GONE)
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
