package com.scrolla.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrolla.auth.AuthRepository
import com.scrolla.firestore.AlreadyInGroupException
import com.scrolla.firestore.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GroupViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val groupRepository: GroupRepository = GroupRepository()
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    fun createGroup(groupName: String, onSuccess: (String) -> Unit) {
        val user = authRepository.currentUser
        if (user == null) {
            _errorMessage.value = "You must be signed in to create a group."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val displayName = user.displayName ?: "Anonymous User"
            val result = groupRepository.createGroup(user.uid, displayName, groupName)
            
            _isLoading.value = false
            
            result.onSuccess { code ->
                onSuccess(code)
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to create group."
            }
        }
    }

    fun joinGroup(groupCode: String, onSuccess: () -> Unit) {
        val user = authRepository.currentUser
        if (user == null) {
            _errorMessage.value = "You must be signed in to join a group."
            return
        }

        if (groupCode.length != 6) {
            _errorMessage.value = "Group code must be 6 characters."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val displayName = user.displayName ?: "Anonymous User"
            val result = groupRepository.joinGroup(user.uid, displayName, groupCode)
            
            _isLoading.value = false
            
            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                _errorMessage.value = when (e) {
                    is AlreadyInGroupException -> ScrollaStrings.JOIN_GROUP_ERROR_ALREADY
                    else -> e.message ?: "Failed to join group."
                }
            }
        }
    }
}
