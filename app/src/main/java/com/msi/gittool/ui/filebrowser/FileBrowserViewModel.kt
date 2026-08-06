package com.msi.gittool.ui.filebrowser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msi.gittool.data.repository.RepoRepository
import com.msi.gittool.data.remote.GitHubContentItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FileBrowserUiState(
    val owner: String = "",
    val repoName: String = "",
    val currentPath: String = "",
    val pathHistory: List<String> = emptyList(),
    val contents: List<GitHubContentItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val languages: Map<String, Float> = emptyMap(),
    val commitActivity: List<Int> = emptyList()
)

class FileBrowserViewModel(
    private val repoRepository: RepoRepository,
    private val owner: String,
    private val repoName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileBrowserUiState(owner = owner, repoName = repoName))
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    fun loadContents(path: String, forceRefresh: Boolean, context: Context) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            if (path.isEmpty()) {
                launch {
                    val langsRes = repoRepository.getRepoLanguages(owner, repoName, context)
                    langsRes.onSuccess { rawMap ->
                        val totalBytes = rawMap.values.sum().toFloat()
                        val floatMap = if (totalBytes > 0) {
                            rawMap.mapValues { it.value / totalBytes }
                        } else {
                            rawMap.mapValues { 0f }
                        }
                        _uiState.update { it.copy(languages = floatMap) }
                    }
                }
                launch {
                    val commitRes = repoRepository.getRepoCommitActivity(owner, repoName, context)
                    commitRes.onSuccess { list ->
                        _uiState.update { it.copy(commitActivity = list) }
                    }
                }
            }

            val result = repoRepository.getRepoContents(owner, repoName, path, forceRefresh, context)
            result.onSuccess { list ->
                _uiState.update { state ->
                    state.copy(
                        contents = list,
                        currentPath = path,
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = error.localizedMessage ?: "Failed to read directories."
                ) }
            }
        }
    }

    fun selectDirectory(path: String, context: Context) {
        val currentHistory = _uiState.value.pathHistory.toMutableList()
        currentHistory.add(_uiState.value.currentPath)
        _uiState.update { it.copy(pathHistory = currentHistory) }
        loadContents(path, forceRefresh = false, context = context)
    }

    fun goBack(context: Context): Boolean {
        val history = _uiState.value.pathHistory
        if (history.isEmpty()) return false

        val previousPath = history.last()
        val newHistory = history.dropLast(1)

        _uiState.update { it.copy(pathHistory = newHistory) }
        loadContents(previousPath, forceRefresh = false, context = context)
        return true
    }

    fun downloadZip(context: Context) {
        viewModelScope.launch {
            repoRepository.downloadRepoZip(context, owner, repoName, "main")
        }
    }

    companion object {
        fun Factory(repoRepository: RepoRepository, owner: String, repoName: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FileBrowserViewModel(repoRepository, owner, repoName) as T
            }
        }
    }
}
