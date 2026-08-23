package com.scrolla.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrolla.model.DistanceFormatter
import com.scrolla.room.ScrollRepository
import com.scrolla.ui.ScrollaGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * What the Home screen renders.
 *
 * [hasSensorData] is the difference between "the sensor has never recorded
 * anything" and "today is genuinely zero so far" — the screen must not show a
 * confident 0.0 km when the truth is that tracking has not started yet
 * (SPRINT_LOG Sprint 2 goal: never a plausible fake number).
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val hasSensorData: Boolean = false,
    val todayKm: Float = 0f,
    val yesterdayKm: Float? = null,
    val landmarkText: String? = null,
    val peakHour: Int? = null
)

class HomeViewModel(
    private val scrollRepository: ScrollRepository = ScrollaGraph.scrollRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads today's figures. Called on init and whenever the app returns to foreground. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // None of these throw — ScrollRepository returns documented safe
            // defaults and logs internally (AGENTS.md §4.8).
            val todayKm = scrollRepository.getTodayTotalKm()
            val history = scrollRepository.getRecentDailyTotals(7)
            val peakHour = scrollRepository.getTodayPeakHour()

            val yesterday = LocalDate.now().minusDays(1).toString()
            // getTotalKmForDate() returns 0f for a day it has no row for, which is
            // indistinguishable from a real zero — so only show a yesterday
            // comparison when a row for yesterday actually exists.
            val yesterdayKm = if (history.any { it.day == yesterday }) {
                scrollRepository.getTotalKmForDate(yesterday)
            } else {
                null
            }

            _uiState.value = HomeUiState(
                isLoading = false,
                hasSensorData = todayKm > 0f || history.isNotEmpty(),
                todayKm = todayKm,
                yesterdayKm = yesterdayKm,
                landmarkText = DistanceFormatter.nearestLandmark(todayKm)
                    ?.let { (name, _) -> "≈ $name" },
                peakHour = peakHour
            )
        }
    }
}
