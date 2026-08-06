package com.msi.gittool.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msi.gittool.data.repository.RepoRepository
import com.msi.gittool.data.remote.GitHubRepo
import com.msi.gittool.data.remote.GitHubUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val user: GitHubUser? = null,
    val repos: List<GitHubRepo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class UserProfileViewModel(
    private val repoRepository: RepoRepository,
    private val username: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val userRes = repoRepository.getUserDetails(username)
            val reposRes = repoRepository.getUserReposList(username)

            if (userRes.isSuccess && reposRes.isSuccess) {
                _uiState.update {
                    it.copy(
                        user = userRes.getOrNull(),
                        repos = reposRes.getOrNull() ?: emptyList(),
                        isLoading = false
                    )
                }
            } else {
                val errorMsg = userRes.exceptionOrNull()?.localizedMessage 
                    ?: reposRes.exceptionOrNull()?.localizedMessage
                    ?: "Failed to load user profile or repositories."
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = errorMsg
                    )
                }
            }
        }
    }

    fun toggleBookmark(repo: GitHubRepo) {
        viewModelScope.launch {
            repoRepository.toggleBookmark(repo)
        }
    }

    companion object {
        fun Factory(repoRepository: RepoRepository, username: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UserProfileViewModel(repoRepository, username) as T
            }
        }
    }
}
