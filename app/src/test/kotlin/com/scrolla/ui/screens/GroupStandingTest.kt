package com.scrolla.ui.screens

import com.scrolla.firestore.GroupMembership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The first unit test in this project.
 *
 * `selfStanding()` is deliberately a pure function over UI state so it can be
 * tested without a device, a Firestore instance, or `ScrollaGraph` having been
 * initialised — see PREMIUM_CHECKLIST P0.1f for why the ViewModels themselves
 * are currently awkward to instantiate in a JVM test.
 *
 * Every case below is a way the Home standing card could show a number that is
 * not true, which is the specific failure this app has spent the most time
 * fixing.
 */
class GroupStandingTest {

    private fun group(name: String = "College Friends", members: Int = 3) = GroupMembership(
        groupId = "ABC123",
        groupName = name,
        displayName = "Rohit",
        isPrimary = true,
        memberCount = members
    )

    private fun state(
        entries: List<LeaderboardEntry>,
        activeGroup: GroupMembership? = group()
    ) = LeaderboardUiState(isLoading = false, activeGroup = activeGroup, entries = entries)

    private fun entry(km: Float, isSelf: Boolean = false, name: String = "Alex") =
        LeaderboardEntry(displayName = name, distanceKm = km, isSelf = isSelf)

    // ─── The honest-absence cases ────────────────────────────────────────

    @Test
    fun `no active group means no standing`() {
        val s = state(entries = listOf(entry(0.4f, isSelf = true), entry(0.9f)), activeGroup = null)
        assertNull(s.selfStanding())
    }

    @Test
    fun `empty board means no standing`() {
        assertNull(state(entries = emptyList()).selfStanding())
    }

    @Test
    fun `a single synced member is not ranked 1st of 1`() {
        // SOCIAL_PROGRESS section 7: "1st of 1" makes the reverse mechanic look
        // broken rather than won.
        val s = state(entries = listOf(entry(0.4f, isSelf = true)))
        assertNull(s.selfStanding())
    }

    @Test
    fun `user who has not synced today is not ranked`() {
        // Placing them last by default would be a fabricated position.
        val s = state(entries = listOf(entry(0.4f, name = "Alex"), entry(0.9f, name = "Sam")))
        assertNull(s.selfStanding())
    }

    // ─── Ranking, lowest wins ────────────────────────────────────────────

    @Test
    fun `lowest distance ranks first`() {
        val s = state(
            entries = listOf(
                entry(0.2f, isSelf = true),
                entry(0.6f, name = "Alex"),
                entry(1.1f, name = "Sam")
            )
        )
        assertEquals(1, s.selfStanding()?.rankPosition)
    }

    @Test
    fun `highest distance ranks last`() {
        val s = state(
            entries = listOf(
                entry(0.2f, name = "Alex"),
                entry(0.6f, name = "Sam"),
                entry(1.1f, isSelf = true)
            )
        )
        assertEquals(3, s.selfStanding()?.rankPosition)
    }

    @Test
    fun `group size counts members who synced, not group membership`() {
        // A five-person group where two have synced is "of 2", not "of 5" —
        // rank is computed among people who actually have a figure today.
        val s = state(
            entries = listOf(entry(0.2f, isSelf = true), entry(0.6f, name = "Alex")),
            activeGroup = group(members = 5)
        )
        assertEquals(2, s.selfStanding()?.groupSize)
    }

    @Test
    fun `group name is carried through`() {
        val s = state(entries = listOf(entry(0.2f, isSelf = true), entry(0.6f)))
        assertEquals("College Friends", s.selfStanding()?.groupName)
    }

    // ─── Ties ────────────────────────────────────────────────────────────

    @Test
    fun `tied members share the better rank`() {
        // Everyone sits at 0.0 first thing in the morning. Ordering by list
        // position would hand an arbitrary 3rd place to someone level with the
        // leader.
        val s = state(
            entries = listOf(
                entry(0f, name = "Alex"),
                entry(0f, isSelf = true),
                entry(0f, name = "Sam")
            )
        )
        assertEquals(1, s.selfStanding()?.rankPosition)
    }

    @Test
    fun `rank after a tie skips the shared positions`() {
        // Two people tied at 1st means the next person is 3rd, not 2nd.
        val s = state(
            entries = listOf(
                entry(0.2f, name = "Alex"),
                entry(0.2f, name = "Sam"),
                entry(0.9f, isSelf = true)
            )
        )
        assertEquals(3, s.selfStanding()?.rankPosition)
    }
}
