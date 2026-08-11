package com.msi.gittool.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msi.gittool.data.repository.RepoRepository
import com.msi.gittool.data.remote.GitHubRepo
import com.msi.gittool.data.remote.GitHubUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ExternalSearchResult(
    val title: String,
    val snippet: String,
    val url: String,
    val sourceDomain: String
)

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(
        val repos: List<GitHubRepo>, 
        val users: List<GitHubUser>,
        val ownRepos: List<GitHubRepo> = emptyList()
    ) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(
    private val repoRepository: RepoRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _popularRepos = MutableStateFlow<List<GitHubRepo>>(emptyList())
    val popularRepos: StateFlow<List<GitHubRepo>> = _popularRepos.asStateFlow()

    private val _isPopularLoading = MutableStateFlow(false)
    val isPopularLoading: StateFlow<Boolean> = _isPopularLoading.asStateFlow()

    val recentSearches: StateFlow<List<String>> = repoRepository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<SearchUiState> = _searchQuery
        .debounce(400)
        .flatMapLatest { query ->
            val trimmed = query.trim()
            if (trimmed.length >= 2) {
                // Save valid queries to Room database
                viewModelScope.launch {
                    repoRepository.saveRecentSearch(trimmed)
                }
                flow<SearchUiState> {
                    emit(SearchUiState.Loading)
                    
                    val reposResult = try {
                        repoRepository.searchRepositories(trimmed)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    
                    val usersResult = try {
                        repoRepository.searchUsers(trimmed)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    
                    val ownRepos = try {
                        repoRepository.getLocalCachedRepos().filter {
                            it.name.contains(trimmed, ignoreCase = true) ||
                            it.full_name.contains(trimmed, ignoreCase = true)
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val repos = reposResult.getOrNull() ?: emptyList()
                    val users = usersResult.getOrNull() ?: emptyList()

                    emit(
                        SearchUiState.Success(
                            repos = repos,
                            users = users,
                            ownRepos = ownRepos
                        )
                    )
                }
            } else {
                flowOf<SearchUiState>(SearchUiState.Idle)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState.Idle)

    init {
        loadPopularRepos()
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            repoRepository.deleteRecentSearch(query)
        }
    }

    fun clearAllRecentSearches() {
        viewModelScope.launch {
            repoRepository.clearAllRecentSearches()
        }
    }

    fun onQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun loadPopularRepos() {
        _isPopularLoading.value = true
        viewModelScope.launch {
            val result = repoRepository.getPopularRepositories()
            _isPopularLoading.value = false
            result.onSuccess { list ->
                _popularRepos.value = list
            }
        }
    }

    fun toggleBookmark(repo: GitHubRepo) {
        viewModelScope.launch {
            repoRepository.toggleBookmark(repo)
        }
    }

    fun getExternalSearchResults(query: String): List<ExternalSearchResult> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return listOf(
            ExternalSearchResult(
                title = "$q - Google Search",
                snippet = "Find global web results, technical discussions, documentation, and code samples on Google for '$q'.",
                url = "https://www.google.com/search?q=${android.net.Uri.encode(q)}",
                sourceDomain = "google.com"
            ),
            ExternalSearchResult(
                title = "$q on GitHub Code & Topics",
                snippet = "Explore GitHub code repositories, open source projects, issues, and discussions matching '$q'.",
                url = "https://github.com/search?q=${android.net.Uri.encode(q)}",
                sourceDomain = "github.com"
            ),
            ExternalSearchResult(
                title = "Stack Overflow: Questions tagged $q",
                snippet = "Search community answers, error resolutions, code snippets, and debugging solutions for '$q'.",
                url = "https://stackoverflow.com/search?q=${android.net.Uri.encode(q)}",
                sourceDomain = "stackoverflow.com"
            ),
            ExternalSearchResult(
                title = "Android Developers Documentation: $q",
                snippet = "Official Android developer guides, Kotlin API reference, and Jetpack Compose samples for '$q'.",
                url = "https://developer.android.com/s/results?q=${android.net.Uri.encode(q)}",
                sourceDomain = "developer.android.com"
            ),
            ExternalSearchResult(
                title = "Maven Central & Artifacts: $q",
                snippet = "Search Java & Kotlin dependency libraries, Gradle packages, and version artifacts for '$q'.",
                url = "https://mvnrepository.com/search?q=${android.net.Uri.encode(q)}",
                sourceDomain = "mvnrepository.com"
            )
        )
    }

    companion object {
        fun Factory(repoRepository: RepoRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(repoRepository) as T
            }
        }
    }
}
