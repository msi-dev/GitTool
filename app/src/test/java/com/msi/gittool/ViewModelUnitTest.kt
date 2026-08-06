package com.msi.gittool

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.msi.gittool.data.local.TokenManager
import com.msi.gittool.data.local.db.*
import com.msi.gittool.data.remote.*
import com.msi.gittool.data.repository.AuthRepository
import com.msi.gittool.data.repository.RepoRepository
import com.msi.gittool.ui.main.RepoFilter
import com.msi.gittool.ui.main.RepoListViewModel
import com.msi.gittool.ui.main.RepoSortOrder
import com.msi.gittool.ui.search.SearchUiState
import com.msi.gittool.ui.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ViewModelUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var apiService: FakeApiService
    private lateinit var tokenManager: TokenManager
    private lateinit var gitToolDao: FakeGitToolDao
    private lateinit var repoRepository: RepoRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var repoViewModel: RepoListViewModel
    private lateinit var searchViewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        apiService = FakeApiService()
        tokenManager = TokenManager(context)
        gitToolDao = FakeGitToolDao()
        
        repoRepository = RepoRepository(apiService, tokenManager, gitToolDao)
        authRepository = AuthRepository(apiService, tokenManager)
        
        repoViewModel = RepoListViewModel(repoRepository, authRepository)
        searchViewModel = SearchViewModel(repoRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testRepoListFiltersAndSorting() = runTest {
        // Use Mock Login mode for 100% deterministic, ultra-fast main thread state tracking
        tokenManager.saveIsMockLogin(true)
        tokenManager.saveUsername("local_test_user")

        // Collect filtered repos in a list to activate WhileSubscribed StateFlow
        val filteredStates = mutableListOf<List<GitHubRepo>>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            repoViewModel.filteredRepos.collect { filteredStates.add(it) }
        }

        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            repoViewModel.loadUserAndRepos(context, false)
            testScheduler.advanceUntilIdle()

            // Test Default Filter is PUBLIC and sorts by NAME alphabetically
            // Mock login brings 2 public repos: "esoteric-compiler-rust" and "gittool-companion"
            var filtered = repoViewModel.filteredRepos.value
            assertEquals(2, filtered.size)
            assertEquals("esoteric-compiler-rust", filtered[0].name)
            assertEquals("gittool-companion", filtered[1].name)

            // Test Sort by STAR_COUNT (Descending, highest first)
            // "esoteric-compiler-rust" has 112 stars, "gittool-companion" has 42 stars
            repoViewModel.selectSortOrder(RepoSortOrder.STAR_COUNT)
            testScheduler.advanceUntilIdle()
            filtered = repoViewModel.filteredRepos.value
            assertEquals("esoteric-compiler-rust", filtered[0].name)
            assertEquals("gittool-companion", filtered[1].name)
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun testSearchDebounceAndResults() = runTest {
        // Collect search results in a list to test state transition
        val results = mutableListOf<SearchUiState>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            searchViewModel.searchResults.collect { results.add(it) }
        }

        try {
            // Initially Idle
            assertEquals(SearchUiState.Idle, searchViewModel.searchResults.value)

            // Enter a search query of length >= 2
            searchViewModel.onQueryChange("Alpha")
            
            // Advance virtual clock beyond the 400ms debounce window
            testScheduler.advanceTimeBy(500)
            testScheduler.advanceUntilIdle()

            // Verify success state containing our fake api search results
            val lastState = searchViewModel.searchResults.value
            if (lastState !is SearchUiState.Success) {
                fail("Expected SearchUiState.Success but got: $lastState. All states captured: $results")
            }
            
            val success = lastState as SearchUiState.Success
            assertEquals(2, success.repos.size)
            assertEquals("Alpha", success.repos[0].name)
        } finally {
            collectJob.cancel()
        }
    }
}

// --- Test Fakes ---

class FakeGitToolDao : GitToolDao {
    private val bookmarks = mutableListOf<BookmarkEntity>()
    private val cachedRepos = mutableListOf<CachedRepoEntity>()
    private val cachedUsers = mutableMapOf<String, CachedUserEntity>()

    override fun getBookmarksFlow() = kotlinx.coroutines.flow.flowOf(bookmarks)
    override suspend fun getBookmarks() = bookmarks
    override suspend fun insertBookmark(bookmark: BookmarkEntity) {
        bookmarks.removeAll { it.id == bookmark.id }
        bookmarks.add(bookmark)
    }
    override suspend fun deleteBookmarkById(id: Long) {
        bookmarks.removeAll { it.id == id }
    }
    override fun isBookmarkedFlow(id: Long) = kotlinx.coroutines.flow.flowOf(bookmarks.any { it.id == id })
    override suspend fun isBookmarked(id: Long) = bookmarks.any { it.id == id }

    override suspend fun getCachedRepos(isPrivate: Boolean) = cachedRepos.filter { it.isPrivateList == isPrivate }
    override suspend fun getCachedUser(login: String) = cachedUsers[login]
    override suspend fun insertCachedUser(user: CachedUserEntity) {
        cachedUsers[user.login] = user
    }
    override suspend fun clearCachedUser(login: String) {
        cachedUsers.remove(login)
    }
    override suspend fun insertCachedRepos(repos: List<CachedRepoEntity>) {
        cachedRepos.addAll(repos)
    }
    override suspend fun clearCachedRepos(isPrivate: Boolean) {
        cachedRepos.removeAll { it.isPrivateList == isPrivate }
    }
    override suspend fun deleteCachedRepoByName(fullName: String) {
        cachedRepos.removeAll { it.fullName == fullName }
    }

    override suspend fun getCachedDirectoryContents(fullName: String, parentPath: String) = emptyList<CachedFileEntity>()
    override suspend fun getCachedFile(pathId: String) = null
    override suspend fun insertCachedFiles(files: List<CachedFileEntity>) {}
    override suspend fun insertCachedFile(file: CachedFileEntity) {}
    override suspend fun clearCachedFilesForRepo(fullName: String) {}
}

class FakeApiService : GitHubApiService {
    var currentUser = GitHubUser("test_user", 123L, "https://avatar.url", "Test User", "https://html.url", null, null, null, null, 10, 5, 2)
    var searchReposResponse = GitHubSearchResponse(2, false, listOf(
        GitHubRepo(1L, "Alpha", "test_user/Alpha", false, "https://html.url/Alpha", "Description Alpha", 50, 5, "Kotlin", "https://clone.url/Alpha", "main", "2026-06-03T10:00:00Z"),
        GitHubRepo(2L, "Beta", "test_user/Beta", false, "https://html.url/Beta", "Description Beta", 500, 15, "Java", "https://clone.url/Beta", "main", "2026-06-01T10:00:00Z")
    ))
    var searchUsersResponse = GitHubSearchResponse(1, false, listOf(currentUser))
    var userReposList = listOf(
        GitHubRepo(1L, "Alpha", "test_user/Alpha", false, "https://html.url/Alpha", "Description Alpha", 50, 5, "Kotlin", "https://clone.url/Alpha", "main", "2026-06-03T10:00:00Z"),
        GitHubRepo(2L, "Beta", "test_user/Beta", false, "https://html.url/Beta", "Description Beta", 500, 15, "Java", "https://clone.url/Beta", "main", "2026-06-01T10:00:00Z"),
        GitHubRepo(3L, "Gamma", "test_user/Gamma", true, "https://html.url/Gamma", "Description Gamma", 10, 1, "Kotlin", "https://clone.url/Gamma", "main", "2026-06-02T10:00:00Z")
    )

    override suspend fun getCurrentUser() = currentUser
    override suspend fun getUserRepos(perPage: Int, page: Int, sort: String) = userReposList
    override suspend fun createRepo(request: CreateRepoRequest) = TODO()
    override suspend fun createBlob(owner: String, repo: String, request: CreateBlobRequest) = TODO()
    override suspend fun createTree(owner: String, repo: String, request: CreateTreeRequest) = TODO()
    override suspend fun createCommit(owner: String, repo: String, request: CreateCommitRequest) = TODO()
    override suspend fun updateReference(owner: String, repo: String, branch: String, request: UpdateRefRequest) = TODO()
    override suspend fun getReference(owner: String, repo: String, branch: String) = TODO()
    override suspend fun createReference(owner: String, repo: String, body: CreateRefRequest) = TODO()
    override suspend fun searchRepositories(query: String, sort: String?, order: String?, perPage: Int) = searchReposResponse
    override suspend fun searchUsers(query: String, perPage: Int) = searchUsersResponse
    override suspend fun getRepoContents(owner: String, repo: String, path: String) = emptyList<GitHubContentItem>()
    override suspend fun getRepoRootContents(owner: String, repo: String) = emptyList<GitHubContentItem>()
    override suspend fun getRepoFileContent(owner: String, repo: String, path: String) = TODO()
    override suspend fun createFork(owner: String, repo: String) = TODO()
    override suspend fun updateCurrentUser(body: UpdateUserRequest) = TODO()
    override suspend fun getNotifications(all: Boolean, perPage: Int) = emptyList<GitHubNotification>()
    override suspend fun markNotificationAsRead(id: String) = TODO()
    override suspend fun markAllNotificationsAsRead(body: MarkAllReadRequest) = TODO()
    override suspend fun deleteRepo(owner: String, repo: String) = TODO()
    override suspend fun getUserDetails(username: String) = currentUser
    override suspend fun getUserReposList(username: String, perPage: Int) = userReposList
}
