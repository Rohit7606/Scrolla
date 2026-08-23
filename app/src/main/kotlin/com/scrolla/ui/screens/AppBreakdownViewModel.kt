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

data class AppBreakdownUiState(
    val isLoading: Boolean = true,
    val hasData: Boolean = false,
    val topApp: String = "",
    val apps: List<BreakdownAppUsage> = emptyList()
)

/**
 * Today's per-app breakdown — the most privacy-sensitive screen in the app.
 *
 * This data is local-only and must never reach Firestore
 * (`DATA_CONTRACT.md` §3.2). This ViewModel therefore touches
 * [ScrollRepository] and nothing else: there is deliberately no
 * `GroupRepository` here, so per-app figures have no path to the cloud.
 */
class AppBreakdownViewModel(
    private val scrollRepository: ScrollRepository = ScrollaGraph.scrollRepository,
    private val appLabel: (String) -> String = ScrollaGraph::appLabel
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppBreakdownUiState())
    val uiState: StateFlow<AppBreakdownUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val topApps = scrollRepository.getTodayTopApps()
            val totalCm = topApps.sumOf { it.totalCm.toDouble() }.toFloat()

            _uiState.value = AppBreakdownUiState(
                isLoading = false,
                hasData = topApps.isNotEmpty(),
                topApp = topApps.firstOrNull()?.let { appLabel(it.appPackage) }.orEmpty(),
                apps = topApps.map {
                    BreakdownAppUsage(
                        appName = appLabel(it.appPackage),
                        distanceKm = DistanceFormatter.cmToKm(it.totalCm),
                        // Share of the apps shown, not of the whole day — the DAO
                        // returns the top five only, so these are honest relative
                        // to each other and would not sum to 100% of everything.
                        percentage = if (totalCm > 0f) it.totalCm / totalCm else 0f
                    )
                }
            )
        }
    }
}
