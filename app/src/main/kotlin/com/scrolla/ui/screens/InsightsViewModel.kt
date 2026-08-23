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
import java.time.DayOfWeek
import java.time.LocalDate

data class InsightsUiState(
    val isLoading: Boolean = true,
    val hasSensorData: Boolean = false,
    val weekData: List<DayData> = emptyList(),
    val topApps: List<AppUsage> = emptyList(),
    val peakTimeText: String? = null
)

class InsightsViewModel(
    private val scrollRepository: ScrollRepository = ScrollaGraph.scrollRepository,
    private val appLabel: (String) -> String = ScrollaGraph::appLabel
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val history = scrollRepository.getRecentDailyTotals(7)
            val topApps = scrollRepository.getTodayTopApps()
            val peakHour = scrollRepository.getTodayPeakHour()

            _uiState.value = InsightsUiState(
                isLoading = false,
                hasSensorData = history.isNotEmpty() || topApps.isNotEmpty(),
                weekData = buildWeek(history.associate { it.day to it.totalKm }),
                topApps = topApps.map { AppUsage(appLabel(it.appPackage), DistanceFormatter.cmToKm(it.totalCm)) },
                peakTimeText = peakHour?.let {
                    "Most of it happens between ${ScrollaFormatters.formatHourRange(it)}."
                }
            )
        }
    }

    /**
     * The chart is a calendar week (Monday → Sunday), not the trailing seven days,
     * so "this week" means the same thing here as it does to the user. Days after
     * today are marked future rather than zero — an empty bar and a day that has
     * not happened yet are different things.
     */
    private fun buildWeek(totalsByDay: Map<String, Float>): List<DayData> {
        val today = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)
        return (0L..6L).map { offset ->
            val date = monday.plusDays(offset)
            val key = date.toString()
            val isToday = date == today
            DayData(
                dayLabel = date.dayOfWeek.name.take(1),
                distanceKm = totalsByDay[key] ?: 0f,
                isToday = isToday,
                isFuture = date.isAfter(today),
                fullLabel = if (isToday) {
                    "Today"
                } else {
                    date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                }
            )
        }
    }
}
