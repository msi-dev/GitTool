package com.msi.gittool.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msi.gittool.data.local.db.ActivityLogEntity
import com.msi.gittool.data.repository.RepoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationUiState(
    val activityLogs: List<ActivityLogEntity> = emptyList(),
    val selectedLog: ActivityLogEntity? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val unreadCount: Int = 0,
    val errorMessage: String? = null
)

class NotificationViewModel(
    private val repoRepository: RepoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repoRepository.seedInitialActivityLogsIfEmpty()
        }
        observeActivityLogs()
    }

    private fun observeActivityLogs() {
        viewModelScope.launch {
            repoRepository.activityLogs.collectLatest { logs ->
                _uiState.update { state ->
                    state.copy(
                        activityLogs = logs,
                        unreadCount = logs.count { !it.isRead },
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }

    fun refreshLogs() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun selectLog(log: ActivityLogEntity) {
        _uiState.update { it.copy(selectedLog = log) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedLog = null) }
    }

    fun removeLog(id: Long) {
        viewModelScope.launch {
            repoRepository.deleteActivityLog(id)
            if (_uiState.value.selectedLog?.id == id) {
                clearSelection()
            }
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repoRepository.clearAllActivityLogs()
            clearSelection()
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repoRepository.markAllActivityLogsAsRead()
        }
    }

    companion object {
        fun Factory(repoRepository: RepoRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NotificationViewModel(repoRepository) as T
            }
        }
    }
}
