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
     * The last seven days ending today, rolling — not the current calendar week.
     *
     * A Monday-anchored week throws away everything the user just did: on a Monday
     * the chart showed one real bar and six empty days, with Sunday's scrolling
     * invisible despite being yesterday. A trailing window always shows seven days
     * of actual history, and today is always the rightmost bar.
     *
     * Nothing is in the future here by construction, so no bar is ever a
     * placeholder for a day that has not happened.
     */
    private fun buildWeek(totalsByDay: Map<String, Float>): List<DayData> {
        val today = LocalDate.now()
        val start = today.minusDays((WINDOW_DAYS - 1).toLong())
        val yesterday = today.minusDays(1)
        return (0 until WINDOW_DAYS).map { offset ->
            val date = start.plusDays(offset.toLong())
            val isToday = date == today
            DayData(
                dayLabel = date.dayOfWeek.name.take(1),
                distanceKm = totalsByDay[date.toString()] ?: 0f,
                isToday = isToday,
                isFuture = false,
                fullLabel = when {
                    isToday -> "Today"
                    date == yesterday -> "Yesterday"
                    else -> date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                }
            )
        }
    }

    private companion object {
        const val WINDOW_DAYS = 7
    }
}
