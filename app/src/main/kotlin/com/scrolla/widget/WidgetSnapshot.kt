package com.scrolla.widget

import android.util.Log
import com.scrolla.auth.AuthRepository
import com.scrolla.firestore.GroupRepository
import com.scrolla.room.AppPackageCm
import com.scrolla.room.DailyTotal
import com.scrolla.ui.ScrollaGraph
import com.scrolla.ui.screens.GroupStanding
import com.scrolla.ui.screens.standingOf
import java.time.LocalDate

/**
 * Everything the three widget sizes can show, read once (BUILD_PLAN 3.1).
 *
 * One loader rather than one per size, because the alarm fires for all of them
 * together and three providers each doing their own Room and Firestore reads
 * would triple the cost of a surface that exists to be cheap. The small widget
 * ignores most of this; reading it is a single indexed query either way.
 *
 * Every field is nullable or empty-able on purpose. A widget that cannot get
 * its data should say less, never guess — the whole point of the honesty rules
 * in `landmarkComparison` and `standingOf` is that a home screen is the worst
 * place to put a number that is not true, because nobody is there to question
 * it.
 */
data class WidgetSnapshot(
    /** Today's distance in km. 0f is a real answer, not a missing one. */
    val todayKm: Float,
    /** The honest landmark comparison, or null when none is. */
    val landmark: String?,
    /** Where this user stands in their primary group, or null — see [standingOf]. */
    val standing: GroupStanding?,
    /** The last seven days, oldest first, for the chart. */
    val week: List<DailyTotal>,
    /** Today's apps by distance, already ordered. */
    val topApps: List<AppPackageCm>,
    /** The primary group's board today, lowest first. Empty when there is none. */
    val board: List<BoardRow>
) {
    data class BoardRow(val displayName: String, val km: Float, val isSelf: Boolean)

    data class WeekStats(val totalKm: Float, val averageKm: Float, val bestKm: Float)

    /**
     * The three numbers the large widget puts under the chart.
     *
     * **The average divides by seven, not by the rows that exist.** A day with
     * no scroll_events has no row, and dividing by rows-present would quietly
     * report the average of the days someone used their phone — which is always
     * higher, and is not what "daily average" means to the person reading it. A
     * quiet day is a real day and it counts against the average; on a reverse
     * leaderboard that is the direction that flatters, so getting it wrong
     * would flatter in the wrong direction too.
     *
     * Pure over [week] so it can be asserted on directly rather than read off a
     * home screen.
     */
    fun weekStats(): WeekStats {
        val total = week.sumOf { it.totalKm.toDouble() }.toFloat()
        return WeekStats(
            totalKm = total,
            averageKm = total / WEEK_DAYS,
            bestKm = week.maxOfOrNull { it.totalKm } ?: 0f
        )
    }

    companion object {
        /** The window the chart and the average both mean by "the week". */
        const val WEEK_DAYS = 7

        private const val TAG = "WidgetSnapshot"

        /**
         * Reads Room first and Firestore second, and never lets the second
         * failure cost the first.
         *
         * The local half — today's figure, the week, the app breakdown — is
         * what the widget is fundamentally about and it cannot fail for network
         * reasons. The group half needs Firestore, which on a phone with no
         * signal is served from its local cache (verified 2026-09-14 with the
         * network down), and if that fails too the widget simply shows no rank
         * rather than no widget.
         */
        suspend fun load(): WidgetSnapshot {
            val repo = ScrollaGraph.scrollRepository

            val todayKm = repo.getTodayTotalKm()
            val week = repo.getRecentDailyTotals(WEEK_DAYS).sortedBy { it.day }
            val topApps = repo.getTodayTopApps()

            var standing: GroupStanding? = null
            var board: List<BoardRow> = emptyList()

            try {
                val user = AuthRepository().currentUser
                if (user != null) {
                    val groups = GroupRepository().getUserGroups(user.uid).getOrNull().orEmpty()
                    // The primary group is what `setPrimaryGroup()` has been
                    // writing since 2026-09-04 for a widget that did not exist
                    // (BUILD_PLAN 3.9). This is the reader it was built for.
                    // Falls back to the first group so someone who never chose
                    // one still sees a board.
                    val primary = groups.firstOrNull { it.isPrimary } ?: groups.firstOrNull()
                    if (primary != null) {
                        val totals = GroupRepository()
                            .getGroupLeaderboard(primary.groupId, LocalDate.now().toString())
                            .getOrNull().orEmpty()

                        board = totals
                            .sortedBy { it.totalKm }
                            .map { BoardRow(it.displayName, it.totalKm, it.userId == user.uid) }

                        standing = standingOf(
                            memberKm = totals.map { it.totalKm },
                            selfKm = totals.firstOrNull { it.userId == user.uid }?.totalKm,
                            groupName = primary.groupName
                        )
                    }
                }
            } catch (e: Exception) {
                // Deliberately swallowed to a log. The group half is the
                // optional half; losing it must not blank a widget whose
                // primary number came from Room and is perfectly good.
                Log.d(TAG, "Group data unavailable for widget: ${e.message}")
            }

            return WidgetSnapshot(
                todayKm = todayKm,
                landmark = com.scrolla.model.DistanceFormatter.landmarkComparison(todayKm),
                standing = standing,
                week = week,
                topApps = topApps,
                board = board
            )
        }
    }
}
