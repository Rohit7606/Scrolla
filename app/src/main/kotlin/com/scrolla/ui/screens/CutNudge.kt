package com.scrolla.ui.screens

/**
 * "Cutting Instagram by 20% would put you in 2nd" (BUILD_PLAN 3.4).
 *
 * The App Breakdown screen is a list of facts. This is the one line that turns
 * it into something a person can act on, by joining the two halves of the
 * product that otherwise never meet: the per-app figures, which are local and
 * private, and the group board, which is the only place a number has stakes.
 *
 * **The two halves are joined at the screen, never in a ViewModel.**
 * `AppBreakdownViewModel` deliberately has no `GroupRepository` in it, so
 * per-app data has no code path to the cloud (`DATA_CONTRACT.md` §3.2). That
 * guard is worth more than the convenience of computing this in one place, so
 * `MainShell` passes the board in and the per-app figure stays where it is.
 * Nothing about an app name or an app's distance is used here — only one
 * number, the amount a cut would save.
 *
 * Reuses the rank arithmetic in [standingOf] rather than counting again. The
 * landmark rule was private to two ViewModels, the widget became the third
 * consumer, copied the call and dropped the guard, and shipped a claim that ten
 * metres was the Eiffel Tower. This is the third consumer of the rank rule.
 */

/** A fifth. Large enough to change a rank, small enough to sound possible. */
const val CUT_FRACTION = 0.2f

/**
 * The rank this user would hold if they cut [topAppKm] by [cutFraction], or
 * null when there is nothing honest or useful to say.
 *
 * Null in each of these cases:
 *
 *  - **No honest rank to begin with** — fewer than two members synced today, or
 *    this user has not synced. [standingOf] already refuses here and this
 *    inherits that refusal rather than re-deciding it.
 *  - **The cut changes nothing.** Offering a plan that lands you exactly where
 *    you already are is not a suggestion, it is filler.
 *  - **The cut would make things worse**, which cannot happen arithmetically but
 *    is checked anyway: a nudge that reads as a threat is worse than no nudge,
 *    and this line is rendered without anybody reviewing the number first.
 *
 * @param topAppKm the user's largest single app today, in km.
 * @param selfKm the user's own total today, or null if they have not synced.
 * @param memberKm every member who has synced today, self included.
 */
fun cutNudgeRank(
    topAppKm: Float,
    selfKm: Float?,
    memberKm: List<Float>,
    cutFraction: Float = CUT_FRACTION
): Int? {
    val current = standingOf(memberKm, selfKm, groupName = "") ?: return null
    if (selfKm == null) return null

    val saved = topAppKm * cutFraction
    if (saved <= 0f) return null

    val improved = (selfKm - saved).coerceAtLeast(0f)

    // Everyone else's figures — this user's own row removed exactly once, so a
    // group where two people happen to share a total does not lose the other
    // person from the comparison.
    val others = memberKm.toMutableList().apply { remove(selfKm) }
    val newRank = others.count { it < improved } + 1

    if (newRank >= current.rankPosition) return null
    return newRank
}
