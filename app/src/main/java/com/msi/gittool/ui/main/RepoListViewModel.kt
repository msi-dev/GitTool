package com.msi.gittool.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msi.gittool.data.repository.AuthRepository
import com.msi.gittool.data.repository.RepoRepository
import com.msi.gittool.data.remote.GitHubRepo
import com.msi.gittool.data.remote.GitHubUser
import com.msi.gittool.util.GitHubUrlParser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class RepoFilter {
    PUBLIC, BOOKMARKS, PRIVATE, FORKED
}

enum class RepoSortOrder {
    NAME, LAST_UPDATED, STAR_COUNT
}

data class RepoListUiState(
    val user: GitHubUser? = null,
    val publicRepos: List<GitHubRepo> = emptyList(),
    val privateRepos: List<GitHubRepo> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

data class UpdateCheckState(
    val latestRelease: com.msi.gittool.data.remote.GitHubRelease? = null,
    val isChecking: Boolean = false,
    val error: String? = null,
    val hasUpdate: Boolean = false
)

class RepoListViewModel(
    private val repoRepository: RepoRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepoListUiState())
    val uiState: StateFlow<RepoListUiState> = _uiState.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateCheckState())
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    private val _currentFilter = MutableStateFlow(RepoFilter.PUBLIC)
    val currentFilter: StateFlow<RepoFilter> = _currentFilter.asStateFlow()

    val unreadActivityCount: StateFlow<Int> = repoRepository.unreadActivityCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val _currentSortOrder = MutableStateFlow(RepoSortOrder.NAME)
    val currentSortOrder: StateFlow<RepoSortOrder> = _currentSortOrder.asStateFlow()

    private val _showPrivateWarning = MutableStateFlow(false)
    val showPrivateWarning: StateFlow<Boolean> = _showPrivateWarning.asStateFlow()

    private var privateWarningAccepted = false

    val bookmarksFlow: StateFlow<List<GitHubRepo>> = repoRepository.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredRepos: StateFlow<List<GitHubRepo>> = combine(
        uiState,
        _currentFilter,
        bookmarksFlow,
        _currentSortOrder
    ) { state, filter, bookmarks, sortOrder ->
        val repos = when (filter) {
            RepoFilter.PUBLIC -> state.publicRepos
            RepoFilter.PRIVATE -> state.privateRepos
            RepoFilter.BOOKMARKS -> bookmarks
            RepoFilter.FORKED -> {
                (state.publicRepos + state.privateRepos).filter { it.fork }.distinctBy { it.id }
            }
        }
        when (sortOrder) {
            RepoSortOrder.NAME -> repos.sortedBy { it.name.lowercase() }
            RepoSortOrder.LAST_UPDATED -> repos.sortedByDescending { it.updated_at ?: "" }
            RepoSortOrder.STAR_COUNT -> repos.sortedByDescending { it.stargazers_count }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectSortOrder(order: RepoSortOrder) {
        _currentSortOrder.value = order
    }

    fun selectFilter(filter: RepoFilter) {
        if (filter == RepoFilter.PRIVATE && !privateWarningAccepted) {
            _showPrivateWarning.value = true
        } else {
            _currentFilter.value = filter
        }
    }

    fun onPrivateWarningResult(accepted: Boolean) {
        _showPrivateWarning.value = false
        if (accepted) {
            privateWarningAccepted = true
            _currentFilter.value = RepoFilter.PRIVATE
        }
    }

    fun loadUserAndRepos(context: Context, forceRefresh: Boolean = false) {
        _uiState.update { it.copy(isLoading = !forceRefresh, isRefreshing = forceRefresh, errorMessage = null) }
        viewModelScope.launch {
            // Read User details
            val userResult = repoRepository.getCurrentUser()
            userResult.onSuccess { user ->
                _uiState.update { it.copy(user = user) }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = "Profile load failed: ${error.localizedMessage}") }
            }

            // Retrieve Public Repos
            val publicResult = repoRepository.getUserRepos(isPrivateFeed = false, forceRefresh = forceRefresh, context = context)
            publicResult.onSuccess { repos ->
                _uiState.update { it.copy(publicRepos = repos) }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = "Failed to load public repositories: ${error.localizedMessage}") }
            }

            // Retrieve Private Repos
            val privateResult = repoRepository.getUserRepos(isPrivateFeed = true, forceRefresh = forceRefresh, context = context)
            privateResult.onSuccess { repos ->
                _uiState.update { it.copy(privateRepos = repos) }
            }

            _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
        }
    }

    fun toggleBookmark(repo: GitHubRepo) {
        viewModelScope.launch {
            repoRepository.toggleBookmark(repo)
        }
    }

    fun isBookmarkedFlow(id: Long): Flow<Boolean> = repoRepository.isBookmarkedFlow(id)

    fun updateProfile(
        name: String?,
        bio: String?,
        blog: String?,
        location: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = repoRepository.updateProfile(name, bio, blog, location)
            _uiState.update { it.copy(isLoading = false) }
            result.onSuccess { updatedUser ->
                _uiState.update { it.copy(user = updatedUser) }
                onResult(true, "Profile updated successfully.")
            }.onFailure { error ->
                onResult(false, error.localizedMessage ?: "Failed to update profile.")
            }
        }
    }

    fun forkRepoByLink(
        input: String,
        context: Context,
        onResult: (Boolean, String) -> Unit
    ) {
        val parsed = GitHubUrlParser.parse(input)
        if (parsed == null) {
            onResult(false, "Invalid link or repository format. Please enter a link like https://github.com/owner/repo.git or owner/repo")
            return
        }
        forkRepo(parsed.owner, parsed.repoName, context, onResult)
    }

    fun forkRepo(owner: String, repoName: String, context: Context, onResult: (Boolean, String) -> Unit) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val notificationId = 1884
            com.msi.gittool.util.NotificationHelper.showProgressNotification(
                context,
                "Forking Repository",
                "Locating and forking $owner/$repoName...",
                -1,
                100,
                notificationId
            )
            val result = repoRepository.createFork(owner, repoName)
            _uiState.update { it.copy(isLoading = false) }
            result.onSuccess { forkRepo ->
                loadUserAndRepos(context, forceRefresh = true)
                com.msi.gittool.util.NotificationHelper.showNotification(
                    context,
                    "Fork Succeeded",
                    "Forked $owner/$repoName to your account.",
                    notificationId
                )
                onResult(true, "Successfully forked: ${forkRepo.full_name}")
            }.onFailure { error ->
                com.msi.gittool.util.NotificationHelper.showNotification(
                    context,
                    "Fork Failed",
                    "Could not fork $owner/$repoName: ${error.localizedMessage}",
                    notificationId
                )
                onResult(false, error.localizedMessage ?: "Failed to fork repository.")
            }
        }
    }

    fun importExternalRepo(
        url: String, 
        context: Context, 
        onResult: (Boolean, String) -> Unit
    ) {
        val parsed = GitHubUrlParser.parse(url)
        if (parsed == null) {
            onResult(false, "Invalid GitHub repository link. Example: https://github.com/msi-dev/Music.git")
            return
        }

        viewModelScope.launch {
            val owner = parsed.owner
            val repo = parsed.repoName
            val notificationId = 1885
            com.msi.gittool.util.NotificationHelper.showProgressNotification(
                context,
                "Importing Repository",
                "Finding $owner/$repo on GitHub and importing to your account...",
                -1,
                100,
                notificationId
            )
            val result = repoRepository.createFork(owner, repo)
            result.onSuccess { importedRepo ->
                loadUserAndRepos(context, forceRefresh = true)
                com.msi.gittool.util.NotificationHelper.showNotification(
                    context,
                    "Import Succeeded",
                    "Imported $owner/$repo as ${importedRepo.full_name}.",
                    notificationId
                )
                onResult(true, "Successfully imported '${importedRepo.full_name}' to your account!")
            }.onFailure { error ->
                com.msi.gittool.util.NotificationHelper.showNotification(
                    context,
                    "Import Failed",
                    "Could not import $owner/$repo: ${error.localizedMessage}",
                    notificationId
                )
                onResult(false, error.localizedMessage ?: "Failed to import project.")
            }
        }
    }

    fun deleteRepository(context: Context, owner: String, repoName: String, onResult: (Boolean, String) -> Unit) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val notificationId = 1883
            com.msi.gittool.util.NotificationHelper.showProgressNotification(
                context,
                "Deleting Repository",
                "Deleting $owner/$repoName...",
                -1,
                100,
                notificationId
            )
            val result = repoRepository.deleteRepo(owner, repoName, context)
            _uiState.update { it.copy(isLoading = false) }
            result.onSuccess {
                loadUserAndRepos(context, forceRefresh = true)
                com.msi.gittool.util.NotificationHelper.showNotification(
                    context,
                    "Repository Deleted",
                    "Successfully deleted $owner/$repoName from your accounts.",
                    notificationId
                )
                onResult(true, "Successfully deleted $owner/$repoName")
            }.onFailure { error ->
                com.msi.gittool.util.NotificationHelper.showNotification(
                    context,
                    "Delete Failed",
                    "Could not delete $owner/$repoName: ${error.localizedMessage}",
                    notificationId
                )
                onResult(false, error.localizedMessage ?: "Failed to delete repository.")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repoRepository.clearAllCache()
            authRepository.logout()
            _uiState.update { RepoListUiState() }
        }
    }

    fun downloadRepositoryZip(context: Context, owner: String, repoName: String, branch: String) {
        viewModelScope.launch {
            repoRepository.downloadRepoZip(context, owner, repoName, branch)
        }
    }

    fun checkForUpdates() {
        _updateState.update { it.copy(isChecking = true, error = null) }
        viewModelScope.launch {
            val result = repoRepository.getLatestRelease(
                com.msi.gittool.util.Constants.APP_REPO_OWNER,
                com.msi.gittool.util.Constants.APP_REPO_NAME
            )
            result.onSuccess { release ->
                val hasNewVersion = isUpdateAvailable(
                    latestTagName = release.tag_name,
                    currentVersionCode = com.msi.gittool.BuildConfig.VERSION_CODE,
                    currentVersionName = com.msi.gittool.BuildConfig.VERSION_NAME
                )
                _updateState.update {
                    it.copy(
                        latestRelease = release,
                        isChecking = false,
                        hasUpdate = hasNewVersion
                    )
                }
            }.onFailure { error ->
                _updateState.update {
                    it.copy(
                        isChecking = false,
                        error = error.localizedMessage
                    )
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateState.update { it.copy(hasUpdate = false) }
    }

    private fun isUpdateAvailable(
        latestTagName: String,
        currentVersionCode: Int,
        currentVersionName: String
    ): Boolean {
        // Try parsing tag as robust version code (e.g. tag is plain number like "21")
        val tagAsInt = latestTagName.trim().removePrefix("v").substringBefore("-").toIntOrNull()
        if (tagAsInt != null && tagAsInt > 0 && tagAsInt <= 500) {
            // Sane threshold: if it's a solid integer version code, compare directly
            if (tagAsInt > currentVersionCode) {
                return true
            }
        }
        
        // Semantic version comparisons (e.g., "5.1.8" vs "5.1.7")
        val parsedTag = latestTagName.trim().removePrefix("v")
        val tagParts = parsedTag.split(".")
        val currentParts = currentVersionName.split(".")
        
        val length = maxOf(tagParts.size, currentParts.size)
        for (i in 0 until length) {
            val tagPart = tagParts.getOrNull(i)?.toIntOrNull() ?: 0
            val currentPart = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (tagPart > currentPart) return true
            if (tagPart < currentPart) return false
        }
        return false
    }

    companion object {
        fun Factory(repoRepository: RepoRepository, authRepository: AuthRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RepoListViewModel(repoRepository, authRepository) as T
            }
        }
    }
}
