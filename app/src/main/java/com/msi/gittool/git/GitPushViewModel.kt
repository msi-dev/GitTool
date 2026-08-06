package com.msi.gittool.git

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface GitPushUiState {
    object Idle : GitPushUiState

    data class PreChecking(
        val message: String
    ) : GitPushUiState

    data class Pushing(
        val stage: String,
        val progressPercent: Int,
        val detailText: String
    ) : GitPushUiState

    data class Success(
        val summary: String,
        val commitHash: String?,
        val totalFiles: Int,
        val repoUrl: String
    ) : GitPushUiState

    data class Error(
        val message: String
    ) : GitPushUiState
}

class GitPushViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val secureTokenManager: SecureTokenManager,
    private val gitManager: GitManager = GitManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow<GitPushUiState>(GitPushUiState.Idle)
    val uiState: StateFlow<GitPushUiState> = _uiState.asStateFlow()

    private var currentPushJob: Job? = null

    companion object {
        private const val KEY_LAST_REPO_URL = "last_repo_url"
        private const val KEY_LAST_STAGE = "last_stage"
        private const val KEY_LAST_PROGRESS = "last_progress"
    }

    init {
        // Restore state if savedStateHandle contains previous progress
        val lastStage = savedStateHandle.get<String>(KEY_LAST_STAGE)
        val lastProgress = savedStateHandle.get<Int>(KEY_LAST_PROGRESS)
        if (lastStage != null && lastProgress != null && lastProgress in 1..99) {
            _uiState.value = GitPushUiState.Pushing(
                stage = lastStage,
                progressPercent = lastProgress,
                detailText = "Restored push operation"
            )
        }
    }

    fun startPush(
        projectDir: File,
        remoteUrl: String,
        tokenOverride: String? = null,
        branchName: String = "main",
        commitMessage: String = "Automated commit via GitTool",
        authorName: String = "GitTool User",
        authorEmail: String = "user@gittool.app"
    ) {
        val token = tokenOverride?.takeIf { it.isNotBlank() } ?: secureTokenManager.getToken()
        if (token.isNullOrBlank()) {
            _uiState.value = GitPushUiState.Error("GitHub Access Token is missing or invalid. Please enter your token in Settings.")
            return
        }

        savedStateHandle[KEY_LAST_REPO_URL] = remoteUrl

        currentPushJob?.cancel()
        currentPushJob = viewModelScope.launch {
            _uiState.value = GitPushUiState.PreChecking("Checking directory structure...")

            val result = gitManager.pushProject(
                projectDir = projectDir,
                remoteUrl = remoteUrl,
                token = token,
                branchName = branchName,
                commitMessage = commitMessage,
                authorName = authorName,
                authorEmail = authorEmail,
                onProgress = { stage, percent, detail ->
                    savedStateHandle[KEY_LAST_STAGE] = stage
                    savedStateHandle[KEY_LAST_PROGRESS] = percent
                    _uiState.value = GitPushUiState.Pushing(
                        stage = stage,
                        progressPercent = percent,
                        detailText = detail
                    )
                },
                isCancelled = { currentPushJob?.isCancelled == true }
            )

            if (result.isSuccess) {
                savedStateHandle.remove<String>(KEY_LAST_STAGE)
                savedStateHandle.remove<Int>(KEY_LAST_PROGRESS)
                _uiState.value = GitPushUiState.Success(
                    summary = result.summaryMessage,
                    commitHash = result.commitHash,
                    totalFiles = result.totalFilesPushed,
                    repoUrl = remoteUrl
                )
            } else {
                _uiState.value = GitPushUiState.Error(
                    message = result.errorMessage ?: "Failed to upload project."
                )
            }
        }
    }

    fun cancelPush() {
        currentPushJob?.cancel()
        currentPushJob = null
        savedStateHandle.remove<String>(KEY_LAST_STAGE)
        savedStateHandle.remove<Int>(KEY_LAST_PROGRESS)
        _uiState.value = GitPushUiState.Error("Upload cancelled by user.")
    }

    fun resetState() {
        currentPushJob?.cancel()
        currentPushJob = null
        savedStateHandle.remove<String>(KEY_LAST_STAGE)
        savedStateHandle.remove<Int>(KEY_LAST_PROGRESS)
        _uiState.value = GitPushUiState.Idle
    }

    class Factory(
        private val savedStateHandle: SavedStateHandle,
        private val secureTokenManager: SecureTokenManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GitPushViewModel::class.java)) {
                return GitPushViewModel(savedStateHandle, secureTokenManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
        }
    }
}
