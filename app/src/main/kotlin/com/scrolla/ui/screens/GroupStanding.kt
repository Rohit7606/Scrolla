package com.scrolla.ui.screens

/**
 * The user's position on the group leaderboard, as the Home standing card needs it.
 *
 * [groupSize] is how many members have **synced today**, not how many belong to
 * the group. Rank is computed among the people who actually have a figure today,
 * so "3rd of 8" when only three have synced would be a lie about both numbers.
 * The Leaderboard screen says "N of M synced today" in its own caption; Home
 * shows the honest denominator instead of the flattering one.
 */
data class GroupStanding(
    val rankPosition: Int,
    val groupSize: Int,
    val groupName: String
)

/**
 * The user's standing, or null when a rank would be meaningless or invented.
 *
 * Home reads this from the same activity-scoped [LeaderboardViewModel] the Group
 * tab uses, rather than fetching its own copy. That is deliberate on two counts:
 * it costs no extra Firestore reads (the quota math in `AGENTS.md` assumes one
 * read per staleness window, not one per screen), and it makes it impossible for
 * Home and the Leaderboard to disagree about the same rank — which they would
 * eventually do with two caches expiring independently.
 *
 * Returns null in four cases, each of which would otherwise put a number on
 * screen that is not true:
 *
 *  - no active group, so there is nothing to rank within;
 *  - nobody has synced today, so the board is empty;
 *  - the user themselves has not synced today — they cannot be ranked against
 *    figures they have not contributed to, and placing them last by default
 *    would be a fabricated position;
 *  - only one member has synced. "1st of 1" makes the reverse mechanic look
 *    broken rather than won (`SOCIAL_PROGRESS.md` §7, SPRINT_LOG S3.12), and
 *    the Leaderboard already shows personal stats in that case.
 *
 * Ties share the better rank, the way competition ranking normally works. This
 * matters more here than in a typical leaderboard: everyone sits at 0.0 km first
 * thing in the morning, and ordering by list position would hand out an
 * arbitrary 3rd place to someone level with the leader.
 */
fun LeaderboardUiState.selfStanding(): GroupStanding? {
    val group = activeGroup ?: return null
    val self = entries.firstOrNull { it.isSelf } ?: return null
    return standingOf(
        memberKm = entries.map { it.distanceKm },
        selfKm = self.distanceKm,
        groupName = group.groupName
    )
}

/**
 * The rule itself, with no UI state in it (BUILD_PLAN 3.1).
 *
 * Extracted so the home-screen widget can show a rank without reimplementing
 * this. That is not hypothetical tidiness: the landmark rule in
 * `DistanceFormatter` was private to two ViewModels, the widget became a
 * third consumer, copied the call and not the guard, and shipped a claim that
 * ten metres of scrolling was the Eiffel Tower. Every guard documented above
 * is one that a second implementation would silently drop — most of all the
 * "fewer than two members" case, which is the difference between a rank and a
 * participation trophy.
 *
 * @param memberKm every member who has synced today, self included.
 * @param selfKm this user's figure, or null if they have not synced today.
 */
fun standingOf(memberKm: List<Float>, selfKm: Float?, groupName: String): GroupStanding? {
    if (memberKm.size < 2) return null
    if (selfKm == null) return null

    val ahead = memberKm.count { it < selfKm }
    return GroupStanding(
        rankPosition = ahead + 1,
        groupSize = memberKm.size,
        groupName = groupName
    )
}
