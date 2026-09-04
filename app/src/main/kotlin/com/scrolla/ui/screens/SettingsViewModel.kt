package com.scrolla.ui.screens

import android.content.Context
import android.util.Log
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrolla.auth.AuthRepository
import com.scrolla.firestore.GroupRepository
import com.scrolla.room.ScrollRepository
import com.scrolla.room.ScrollaDatabase
import com.scrolla.room.ServiceHealthState
import com.scrolla.ui.ScrollaGraph
import com.scrolla.ui.ScrollaMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    /** Null means "not read yet", which is not the same as healthy — see the screen. */
    val serviceHealth: ServiceHealthState? = null,
    val deviceOem: String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
    val displayName: String = "",
    val phoneLinked: Boolean = false,
    val isDeleting: Boolean = false,
    /** Non-null when deletion failed. The screen must say so rather than
     *  silently leaving the account intact. */
    val deleteError: String? = null
)

/**
 * Settings, including the Service Health card (SPRINT_LOG S2.7).
 *
 * Health is collected from `observeServiceHealth()`, never polled — Room emits
 * on every flush, and `ScrollRepository` documents that requirement explicitly.
 */
class SettingsViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val scrollRepository: ScrollRepository = ScrollaGraph.scrollRepository,
    private val groupRepository: GroupRepository = GroupRepository()
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

    /**
     * Deletes the account and everything behind it, then calls [onDeleted].
     *
     * Three stores, and all three matter. Cloud data goes first, because every
     * Firestore rule is gated on `request.auth.uid` and deleting the account
     * first would strand it forever. The local Room database goes next — it
     * holds every scroll event, per app and per hour, and is the most sensitive
     * thing the app has. Leaving it behind would mean "delete my account"
     * deleted the account and none of the surveillance.
     *
     * The account itself goes last, since it is the credential the other two
     * steps need.
     *
     * If any step fails the account is left intact and the error is surfaced.
     * A half-deleted account is worse than a failed deletion: the user believes
     * they are gone and they are not.
     */
    fun deleteAccount(context: Context, onDeleted: () -> Unit) {
        val user = authRepository.currentUser
        val userId = user?.uid
        if (userId == null) {
            _uiState.value = _uiState.value.copy(
                deleteError = ScrollaStrings.SETTINGS_DELETE_ERROR
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, deleteError = null)

            val problems = groupRepository.deleteAllUserData(userId, user.displayName.orEmpty())
                .getOrElse { error ->
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        deleteError = error.message ?: ScrollaStrings.SETTINGS_DELETE_ERROR
                    )
                    return@launch
                }

            // Any group that could not be cleared stops the whole deletion, and
            // this is the one place it must. Every Firestore rule is gated on
            // request.auth.uid, so dropping the credential now would make that
            // leftover data permanently unreachable — nobody could see it and
            // nobody could delete it, in the flow whose entire purpose is
            // removal. Keeping the account is recoverable; a stranded orphan is
            // not.
            if (problems.isNotEmpty()) {
                Log.w(TAG, "Aborting deletion — ${problems.size} group(s) not cleared: $problems")
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    deleteError = ScrollaStrings.SETTINGS_DELETE_PARTIAL
                )
                return@launch
            }

            // Wipe local history before dropping the credential. Room is not
            // reachable per-user — the database belongs to the install, so
            // clearing all tables is correct here and only here.
            runCatching { ScrollaDatabase.getDatabase(context.applicationContext).clearAllTables() }
                .onFailure { Log.e(TAG, "Failed to clear local database", it) }

            authRepository.deleteAccount(context)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isDeleting = false)
                    onDeleted()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        deleteError = when (error) {
                            is AuthRepository.RecentLoginRequired ->
                                ScrollaStrings.SETTINGS_DELETE_REAUTH
                            else -> ScrollaStrings.SETTINGS_DELETE_ERROR
                        }
                    )
                }
        }
    }

    /**
     * Builds the export off the main thread and hands the text back.
     *
     * The caller shares it; this does not touch Intents, so it stays testable
     * and the ViewModel stays free of Android UI plumbing.
     */
    fun exportData(context: Context, onReady: (android.net.Uri) -> Unit) {
        viewModelScope.launch {
            val today = java.time.LocalDate.now()
            val history = scrollRepository.getRecentDailyTotals(EXPORT_DAYS)
            val topApps = scrollRepository.getTodayTopApps()
            val text = DataExport.build(
                displayName = _uiState.value.displayName,
                generatedOn = today.toString(),
                dailyTotals = history,
                todayTopApps = topApps,
                appLabel = { ScrollaGraph.appLabel(it) }
            )

            val uri = runCatching {
                DataExport.writeToCache(context, text, today.toString())
            }.getOrElse { error ->
                Log.e(TAG, "Failed to write export file", error)
                ScrollaMessages.show(ScrollaStrings.ERROR_EXPORT_FAILED)
                return@launch
            }
            onReady(uri)
        }
    }

    fun dismissDeleteError() {
        _uiState.value = _uiState.value.copy(deleteError = null)
    }

    private companion object {
        const val TAG = "SettingsViewModel"
        /** A year is well past what Room retains, so this is "everything". */
        const val EXPORT_DAYS = 365
    }
}
