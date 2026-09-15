package com.scrolla.ui.screens

/**
 * Who cut their scrolling most this week (BUILD_PLAN 3.3).
 *
 * **This exists because of the shape of a reverse leaderboard.** The person in
 * last place is the one scrolling most, and on a board they cannot climb
 * quickly there is nothing for them to win — the rank rewards the people who
 * were already low. Improvement is the one prize that is open to the person at
 * the bottom, and is in fact easiest for them.
 *
 * Every member's two week-totals are written into their own daily document by
 * their own device during sync, so reading the board reads these too and this
 * costs **no extra Firestore reads** (see BUILD_PLAN 2.2c for why that matters).
 *
 * Pure, and it refuses to answer rather than guess — the same contract as
 * [standingOf], and for the same reason: a name on this line is a public claim
 * about somebody else.
 */

/** One member as the improvement calculation sees them. */
data class ImprovementCandidate(
    val displayName: String,
    val isSelf: Boolean,
    /** Their total over the seven days ending today, or null if they have none. */
    val last7Km: Float?,
    /** Their total over the seven days before that, or null if they have none. */
    val prev7Km: Float?
)

/** The winner, and by how much they cut. */
data class MostImproved(
    val displayName: String,
    val isSelf: Boolean,
    val droppedKm: Float
)

/**
 * A hundred metres. Below this, "most improved" is measuring noise: a single
 * evening of not opening Instagram moves a week by more than some people's
 * whole daily figure, and crowning someone for a rounding difference makes the
 * award mean nothing the first time anyone checks the numbers.
 */
const val MIN_IMPROVEMENT_KM = 0.1f

/**
 * The most improved member, or null when there is no honest answer.
 *
 * Null in every one of these cases, each of which is a way of inventing a
 * result:
 *
 *  - **Fewer than two people have both weeks.** "Most improved" is a
 *    comparison; with one eligible person there was no contest, only a
 *    participant. This is the same refusal as a group of one on the board.
 *  - **Someone has no previous week.** A member who joined four days ago has no
 *    baseline, and treating their missing week as zero would make them look
 *    like the worst backslider in the group. Absent is not zero — the rule the
 *    whole product is built on.
 *  - **Their previous week was zero.** Nothing to improve on, and the
 *    percentage is undefined.
 *  - **Nobody cut more than [minImprovementKm].** A week where everyone scrolled
 *    slightly more has no most-improved, and saying otherwise rewards the person
 *    who got worse the least.
 *  - **A tie at the top.** Two people cannot both be the one who improved most,
 *    and picking whichever sorted first would be arbitrary and invisible.
 */
fun mostImprovedOf(
    candidates: List<ImprovementCandidate>,
    minImprovementKm: Float = MIN_IMPROVEMENT_KM
): MostImproved? {
    val eligible = candidates.mapNotNull { candidate ->
        val last = candidate.last7Km ?: return@mapNotNull null
        val prev = candidate.prev7Km ?: return@mapNotNull null
        if (prev <= 0f) return@mapNotNull null
        MostImproved(
            displayName = candidate.displayName,
            isSelf = candidate.isSelf,
            droppedKm = prev - last
        )
    }

    if (eligible.size < 2) return null

    val ranked = eligible.sortedByDescending { it.droppedKm }
    val best = ranked.first()
    if (best.droppedKm < minImprovementKm) return null

    val runnerUp = ranked[1]
    // Compared at the precision the board is displayed in rather than exactly:
    // two floats that differ in the fourth decimal are a tie to everyone
    // reading the screen, and breaking it silently would name one of two people
    // for no reason either of them could see.
    if (best.droppedKm - runnerUp.droppedKm < TIE_EPSILON_KM) return null

    return best
}

/** One metre. Below this the two figures render identically. */
private const val TIE_EPSILON_KM = 0.001f
