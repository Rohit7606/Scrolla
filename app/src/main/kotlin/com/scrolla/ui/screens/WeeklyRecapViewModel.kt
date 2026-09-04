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

data class WeeklyRecapUiState(
    val isLoading: Boolean = true,
    val hasData: Boolean = false,
    val weeklyDistanceKm: Float = 0f,
    val landmarkText: String? = null
)

class WeeklyRecapViewModel(
    private val scrollRepository: ScrollRepository = ScrollaGraph.scrollRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyRecapUiState())
    val uiState: StateFlow<WeeklyRecapUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // The repository returns the seven most recent *rows*, and a day
            // with no scrolling has no row — so seven rows can span any amount
            // of calendar time. Summing them unfiltered made this screen report
            // "595 m this week" for distance accumulated over thirteen days.
            val window = WeeklyWindow.rowsWithin(
                scrollRepository.getRecentDailyTotals(WeeklyWindow.WINDOW_DAYS),
                LocalDate.now()
            )
            val total = window.map { it.totalKm }.sum()

            _uiState.value = WeeklyRecapUiState(
                isLoading = false,
                // Rows outside the window are not this week's data, so a week
                // with nothing in it must reach the empty state rather than
                // render a hero figure borrowed from a fortnight ago.
                hasData = window.isNotEmpty(),
                weeklyDistanceKm = total,
                landmarkText = landmarkFor(total)
            )
        }
    }

    /**
     * Same honesty rule as Home: `nearestLandmark()` returns the closest entry
     * however far off it is, so only claim a comparison when the figure is
     * genuinely in that landmark's neighbourhood.
     */
    private fun landmarkFor(km: Float): String? {
        val (name, landmarkKm) = DistanceFormatter.nearestLandmark(km) ?: return null
        if (landmarkKm <= 0f) return null
        val ratio = km / landmarkKm
        return if (ratio in 0.5f..2.0f) "that's about $name" else null
    }
}
