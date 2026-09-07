package com.scrolla.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrolla.room.DailyTotal
import com.scrolla.room.ScrollRepository
import com.scrolla.ui.ScrollaGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class PersonalRecordsUiState(
    val isLoading: Boolean = true,
    val hasData: Boolean = false,
    val bestDayKm: Float = 0f,
    val bestDayDate: String = "",
    val bestAvgKm: Float? = null,
    val bestAvgDate: String? = null
)

/**
 * Personal records — lowest is best, throughout.
 */
class PersonalRecordsViewModel(
    private val scrollRepository: ScrollRepository = ScrollaGraph.scrollRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalRecordsUiState())
    val uiState: StateFlow<PersonalRecordsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Same rules as the group record — see PersonalBest.
            val bestDay = PersonalBest.of(scrollRepository)
            // There is no "all daily totals" call on the contract, so ask for a
            // year and work from that — comfortably more history than this app
            // has, and it keeps the query bounded.
            val history = scrollRepository.getRecentDailyTotals(365)
            val bestWeek = bestSevenDayWindow(history)

            _uiState.value = PersonalRecordsUiState(
                isLoading = false,
                hasData = bestDay != null,
                bestDayKm = bestDay?.totalKm ?: 0f,
                bestDayDate = bestDay?.day?.let(::formatDay).orEmpty(),
                bestAvgKm = bestWeek?.first,
                bestAvgDate = bestWeek?.second
            )
        }
    }

    /**
     * The user's quietest seven consecutive days, as (average km, date range).
     *
     * Windows are taken over consecutive calendar dates rather than consecutive
     * rows: a day with no scrolling has no row at all, so a row-based window
     * would silently span a gap and report a week that never happened. Returns
     * null until seven days of history exist.
     */
    private fun bestSevenDayWindow(history: List<DailyTotal>): Pair<Float, String>? {
        if (history.size < WINDOW_DAYS) return null

        val byDay = history.associate { it.day to it.totalKm }
        val dates = history.mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }
        if (dates.isEmpty()) return null

        var best: Pair<Float, String>? = null
        var start = dates.min()
        val lastStart = dates.max().minusDays((WINDOW_DAYS - 1).toLong())

        while (!start.isAfter(lastStart)) {
            val window = (0 until WINDOW_DAYS).map { start.plusDays(it.toLong()) }
            // Only score a window where every day is accounted for.
            if (window.all { byDay.containsKey(it.toString()) }) {
                val average = window.map { byDay.getValue(it.toString()) }.average().toFloat()
                if (best == null || average < best.first) {
                    val end = window.last()
                    best = average to "${formatDay(start.toString())} – ${formatDay(end.toString())}"
                }
            }
            start = start.plusDays(1)
        }
        return best
    }

    private fun formatDay(day: String): String = try {
        LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d MMM"))
    } catch (e: Exception) {
        day
    }

    private companion object {
        const val WINDOW_DAYS = 7
    }
}
