package com.scrolla.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrolla.auth.AuthRepository
import com.scrolla.firestore.GroupRepository
import com.scrolla.room.ScrollRepository
import com.scrolla.ui.ScrollaGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ProfileUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val personalBestKm: Float? = null,
    val personalBestDate: String? = null,
    val sevenDayAvgKm: Float? = null,
    val previousSevenDayAvgKm: Float? = null,
    val groupCount: Int = 0,
    val primaryGroupName: String? = null,
    val hallOfFameGapKm: Float? = null,
    val isRecordHolder: Boolean = false
)

class ProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val groupRepository: GroupRepository = GroupRepository(),
    private val scrollRepository: ScrollRepository = ScrollaGraph.scrollRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val user = authRepository.currentUser
            val bestDay = scrollRepository.getPersonalBestDay()

            // 14 days, split into this week and the one before, so the profile can
            // say "down from" without a second query.
            val fortnight = scrollRepository.getRecentDailyTotals(14)
            val thisWeek = fortnight.take(7)
            val lastWeek = fortnight.drop(7)

            val groups = user?.uid
                ?.let { groupRepository.getUserGroups(it).getOrNull() }
                .orEmpty()
            val primary = groups.firstOrNull { it.isPrimary } ?: groups.firstOrNull()

            val record = primary?.groupId
                ?.let { groupRepository.getGroupRecord(it).getOrNull() }

            _uiState.value = ProfileUiState(
                isLoading = false,
                displayName = user?.displayName.orEmpty(),
                personalBestKm = bestDay?.totalKm,
                personalBestDate = bestDay?.day?.let(::formatDay),
                sevenDayAvgKm = thisWeek.averageKmOrNull(),
                previousSevenDayAvgKm = lastWeek.averageKmOrNull(),
                groupCount = groups.size,
                primaryGroupName = primary?.groupName,
                // Distance between the user's best day and the group's record.
                // Null when either side is unknown — never a fabricated gap.
                hallOfFameGapKm = if (record != null && bestDay != null) {
                    (bestDay.totalKm - record.recordKm).coerceAtLeast(0f)
                } else {
                    null
                },
                isRecordHolder = record != null && bestDay != null && bestDay.totalKm <= record.recordKm
            )
        }
    }

    private fun List<com.scrolla.room.DailyTotal>.averageKmOrNull(): Float? =
        if (isEmpty()) null else map { it.totalKm }.average().toFloat()

    private fun formatDay(day: String): String = try {
        LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d MMMM"))
    } catch (e: Exception) {
        day
    }
}
