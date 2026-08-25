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

data class HallOfFameUiState(
    val isLoading: Boolean = true,
    val hasRecord: Boolean = false,
    val recordHolderName: String = "",
    val recordDistanceKm: Float = 0f,
    val recordDate: String = "",
    val isCurrentUserHolder: Boolean = false,
    val gapToRecordKm: Float = 0f,
    /** Non-null when the read failed, which is not the same as there being no
     *  record yet — the screen must not offer "be the first" because Firestore
     *  was unreachable. */
    val errorMessage: String? = null
)

/**
 * The group's all-time best day.
 *
 * Stays empty until Person A's `triggerFirestoreSync()` lands: the record lives
 * on the group metadata document, which is only written once daily totals are
 * syncing (`DATA_CONTRACT.md` §3.2).
 */
class HallOfFameViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val groupRepository: GroupRepository = GroupRepository(),
    private val scrollRepository: ScrollRepository = ScrollaGraph.scrollRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HallOfFameUiState())
    val uiState: StateFlow<HallOfFameUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val user = authRepository.currentUser
            val userId = user?.uid
            if (userId == null) {
                _uiState.value = HallOfFameUiState(isLoading = false, hasRecord = false)
                return@launch
            }

            // getOrNull() here used to make a failed read indistinguishable from an
            // empty one, so a dropped connection rendered as "no record set yet —
            // you could be first". Absence and failure are different states.
            val groups = groupRepository.getUserGroups(userId).getOrElse { error ->
                _uiState.value = HallOfFameUiState(
                    isLoading = false,
                    hasRecord = false,
                    errorMessage = error.message ?: ScrollaStrings.ERROR_GROUPS_UNAVAILABLE
                )
                return@launch
            }
            val primary = groups.firstOrNull { it.isPrimary } ?: groups.firstOrNull()

            val record = primary?.groupId?.let { groupId ->
                groupRepository.getGroupRecord(groupId).getOrElse { error ->
                    _uiState.value = HallOfFameUiState(
                        isLoading = false,
                        hasRecord = false,
                        errorMessage = error.message ?: ScrollaStrings.ERROR_GROUPS_UNAVAILABLE
                    )
                    return@launch
                }
            }

            if (record == null) {
                _uiState.value = HallOfFameUiState(isLoading = false, hasRecord = false)
                return@launch
            }

            val myBest = scrollRepository.getPersonalBestDay()?.totalKm
            val holdsRecord = myBest != null && myBest <= record.recordKm

            _uiState.value = HallOfFameUiState(
                isLoading = false,
                hasRecord = true,
                recordHolderName = record.recordHolder,
                recordDistanceKm = record.recordKm,
                recordDate = formatDay(record.recordDate),
                isCurrentUserHolder = holdsRecord,
                // Lowest wins, so the gap is how much further the user has to
                // fall, not how far they are ahead.
                gapToRecordKm = if (myBest == null) 0f else (myBest - record.recordKm).coerceAtLeast(0f)
            )
        }
    }

    private fun formatDay(day: String): String = try {
        LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d MMM"))
    } catch (e: Exception) {
        day
    }
}
