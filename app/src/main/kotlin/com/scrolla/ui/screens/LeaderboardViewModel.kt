package com.scrolla.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrolla.auth.AuthRepository
import com.scrolla.firestore.GroupMembership
import com.scrolla.firestore.GroupRepository
import com.scrolla.model.DistanceFormatter
import com.scrolla.model.ScrollaConstants
import com.scrolla.room.ScrollRepository
import com.scrolla.ui.ScrollaGraph
import com.scrolla.ui.ScrollaMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LeaderboardUiState(
    val isLoading: Boolean = true,
    val groups: List<GroupMembership> = emptyList(),
    val activeGroup: GroupMembership? = null,
    val entries: List<LeaderboardEntry> = emptyList(),
    val groupStats: GroupStats? = null,
    val groupBestDay: String? = null,
    val errorMessage: String? = null,
    /** True only for a user-initiated pull, so the spinner is the gesture's and
     *  not the tab-open read's. */
    val isRefreshing: Boolean = false
)

/**
 * Backs both the Group tab and the group switcher.
 *
 * Reads are one-shot `get()` calls behind a staleness window
 * (`ScrollaConstants.LEADERBOARD_CACHE_STALE_MS`) rather than a live listener,
 * so re-entering the tab inside the window costs no Firestore reads — the quota
 * math in `AGENTS.md` depends on this.
 */
class LeaderboardViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val groupRepository: GroupRepository = GroupRepository(),
    private val scrollRepository: ScrollRepository = ScrollaGraph.scrollRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    private var lastLoadedAt: Long = 0L
    private var lastLoadedGroupId: String? = null

    init {
        refresh()
    }

    /**
     * A deliberate pull-to-refresh. Ignores the staleness window on purpose:
     * the cache exists to stop *incidental* reads costing quota, not to overrule
     * someone who has explicitly asked.
     */
    fun refreshFromPull() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        refresh()
    }

    /** Called when the Group tab opens. No-op if the cached data is still fresh. */
    fun refreshIfStale() {
        val age = System.currentTimeMillis() - lastLoadedAt
        if (age < ScrollaConstants.LEADERBOARD_CACHE_STALE_MS && _uiState.value.entries.isNotEmpty()) {
            Log.d(TAG, "Leaderboard cache still fresh (${age}ms) — skipping read")
            return
        }
        refresh()
    }

    fun selectGroup(groupId: String) {
        val group = _uiState.value.groups.firstOrNull { it.groupId == groupId } ?: return
        _uiState.value = _uiState.value.copy(activeGroup = group)
        lastLoadedGroupId = null
        refresh()
    }

    /** Moves the primary flag, which is what the widget reads. */
    fun setPrimaryGroup(groupId: String) {
        val userId = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            groupRepository.setPrimaryGroup(userId, groupId)
                .onSuccess { refresh() }
                // Not uiState.errorMessage: that field is what a failed *load*
                // writes to, and LeaderboardScreen renders it instead of the
                // rows. A failed tap would blank a board that was on screen and
                // perfectly valid, reporting a button's failure as the data's.
                .onFailure {
                    ScrollaMessages.show(
                        text = ScrollaStrings.ERROR_PRIMARY_GROUP,
                        actionLabel = ScrollaStrings.ERROR_RETRY_ACTION,
                        action = { setPrimaryGroup(groupId) }
                    )
                }
        }
    }

    /** Renames a group. Any member may — see `isGroupRename()` in the rules. */
    fun renameGroup(groupId: String, newName: String) {
        viewModelScope.launch {
            groupRepository.renameGroup(groupId, newName)
                .onSuccess { refresh() }
                .onFailure {
                    ScrollaMessages.show(
                        text = ScrollaStrings.GROUP_RENAME_FAILED,
                        actionLabel = ScrollaStrings.ERROR_RETRY_ACTION,
                        action = { renameGroup(groupId, newName) }
                    )
                }
        }
    }

    /**
     * Leaves a group, taking this user's totals in it with them.
     *
     * If the group left was the active one, `refresh()` falls back to their
     * primary and then to whatever remains, so the board never points at a group
     * they are no longer in.
     */
    fun leaveGroup(groupId: String) {
        val user = authRepository.currentUser ?: return
        viewModelScope.launch {
            groupRepository.leaveGroup(groupId, user.uid, user.displayName.orEmpty())
                .onSuccess {
                    if (_uiState.value.activeGroup?.groupId == groupId) {
                        _uiState.value = _uiState.value.copy(activeGroup = null)
                    }
                    refresh()
                }
                // No retry action: a partial leave may already have removed the
                // totals, so "try again" is not the reassurance it looks like.
                // A refresh shows the real current state.
                .onFailure { ScrollaMessages.show(ScrollaStrings.GROUP_LEAVE_FAILED) }
        }
    }

    fun refresh() {
        val userId = authRepository.currentUser?.uid
        if (userId == null) {
            _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "You must be signed in.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val groups = groupRepository.getUserGroups(userId).getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false, isRefreshing = false, errorMessage = error.message
                )
                return@launch
            }

            // Keep the user's chosen group across refreshes; otherwise fall back to
            // their primary, then to whatever exists.
            val active = groups.firstOrNull { it.groupId == _uiState.value.activeGroup?.groupId }
                ?: groups.firstOrNull { it.isPrimary }
                ?: groups.firstOrNull()

            if (active == null) {
                _uiState.value = LeaderboardUiState(isLoading = false, groups = emptyList())
                return@launch
            }

            val today = LocalDate.now().toString()
            val totals = groupRepository.getGroupLeaderboard(active.groupId, today).getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    groups = groups,
                    activeGroup = active,
                    errorMessage = error.message
                )
                return@launch
            }

            val record = groupRepository.getGroupRecord(active.groupId).getOrNull()

            _uiState.value = LeaderboardUiState(
                isLoading = false,
                groups = groups,
                activeGroup = active,
                entries = totals.map {
                    LeaderboardEntry(
                        displayName = if (it.userId == userId) "You" else it.displayName,
                        distanceKm = it.totalKm,
                        isSelf = it.userId == userId
                    )
                },
                groupStats = buildStats(),
                groupBestDay = record?.let {
                    "${DistanceFormatter.formatDistance(it.recordKm)} by ${it.recordHolder}"
                }
            )

            lastLoadedAt = System.currentTimeMillis()
            lastLoadedGroupId = active.groupId
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    /** The three-figure strip is the user's own local history, not group data. */
    private suspend fun buildStats(): GroupStats {
        val today = scrollRepository.getTodayTotalKm()
        val history = scrollRepository.getRecentDailyTotals(7)
        val yesterday = LocalDate.now().minusDays(1).toString()
        return GroupStats(
            todayKm = today,
            yesterdayKm = history.firstOrNull { it.day == yesterday }?.totalKm ?: 0f,
            weekAvgKm = if (history.isEmpty()) 0f else history.map { it.totalKm }.average().toFloat()
        )
    }

    private companion object {
        const val TAG = "LeaderboardViewModel"
    }
}
