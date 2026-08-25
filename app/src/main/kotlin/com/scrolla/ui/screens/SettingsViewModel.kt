package com.scrolla.ui.screens

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrolla.auth.AuthRepository
import com.scrolla.room.ScrollRepository
import com.scrolla.room.ServiceHealthState
import com.scrolla.ui.ScrollaGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    /** Null means "not read yet", which is not the same as healthy — see the screen. */
    val serviceHealth: ServiceHealthState? = null,
    val deviceOem: String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
    val displayName: String = "",
    val phoneLinked: Boolean = false
)

/**
 * Settings, including the Service Health card (SPRINT_LOG S2.7).
 *
 * Health is collected from `observeServiceHealth()`, never polled — Room emits
 * on every flush, and `ScrollRepository` documents that requirement explicitly.
 */
class SettingsViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val scrollRepository: ScrollRepository = ScrollaGraph.scrollRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val user = authRepository.currentUser
        _uiState.value = _uiState.value.copy(
            displayName = user?.displayName.orEmpty(),
            // A linked phone shows up as a second provider on the same account.
            phoneLinked = user?.providerData?.any { it.providerId == "phone" } == true
        )

        viewModelScope.launch {
            scrollRepository.observeServiceHealth().collect { health ->
                _uiState.value = _uiState.value.copy(serviceHealth = health)
            }
        }
    }

    fun signOut(context: Context) {
        authRepository.signOut(context)
    }
}
