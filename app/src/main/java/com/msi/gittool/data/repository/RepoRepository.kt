package com.msi.gittool.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.msi.gittool.data.local.TokenManager
import com.msi.gittool.data.local.db.BookmarkEntity
import com.msi.gittool.data.local.db.CachedFileEntity
import com.msi.gittool.data.local.db.CachedRepoEntity
import com.msi.gittool.data.local.db.CachedUserEntity
import com.msi.gittool.data.local.db.GitToolDao
import com.msi.gittool.data.local.db.RecentSearchEntity
import com.msi.gittool.data.remote.*
import com.msi.gittool.util.NetworkUtil
import com.msi.gittool.util.NotificationHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream

class RepoRepository(
    private val apiService: GitHubApiService,
    private val tokenManager: TokenManager,
    private val gitToolDao: GitToolDao
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val repoListType = Types.newParameterizedType(List::class.java, GitHubRepo::class.java)
    private val listAdapter = moshi.adapter<List<GitHubRepo>>(repoListType)

    // --- Bookmark & Recent Searches Accessors ---
    val bookmarks: Flow<List<GitHubRepo>> = gitToolDao.getBookmarksFlow().map { list ->
        list.map { it.toGitHubRepo() }
    }

    val recentSearches: Flow<List<String>> = gitToolDao.getRecentSearchesFlow().map { list ->
        list.map { it.query }
    }

    suspend fun saveRecentSearch(query: String) = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            gitToolDao.insertRecentSearch(RecentSearchEntity(query = trimmed, timestamp = System.currentTimeMillis()))
        }
    }

    suspend fun deleteRecentSearch(query: String) = withContext(Dispatchers.IO) {
        gitToolDao.deleteRecentSearch(query)
    }

    suspend fun clearAllRecentSearches() = withContext(Dispatchers.IO) {
        gitToolDao.clearAllRecentSearches()
    }

    fun isBookmarkedFlow(id: Long): Flow<Boolean> = gitToolDao.isBookmarkedFlow(id)

    fun getAccessToken(): String? = tokenManager.getAccessToken()

    suspend fun toggleBookmark(repo: GitHubRepo) = withContext(Dispatchers.IO) {
        if (gitToolDao.isBookmarked(repo.id)) {
            gitToolDao.deleteBookmarkById(repo.id)
            logActivity(
                type = "BOOKMARK",
                title = "Removed Bookmark",
                message = "Removed '${repo.full_name}' from bookmarked repositories.",
                repoName = repo.full_name,
                status = "INFO"
            )
        } else {
            gitToolDao.insertBookmark(repo.toBookmarkEntity())
            logActivity(
                type = "BOOKMARK",
                title = "Bookmarked Repository",
                message = "Added '${repo.full_name}' to bookmarked repositories.",
                repoName = repo.full_name,
                status = "SUCCESS"
            )
        }
    }

    // --- Activity & Notification Logs Accessors ---
    val activityLogs: Flow<List<com.msi.gittool.data.local.db.ActivityLogEntity>> = gitToolDao.getActivityLogsFlow()
    val unreadActivityCount: Flow<Int> = gitToolDao.getUnreadActivityLogsCountFlow()

    suspend fun logActivity(
        type: String,
        title: String,
        message: String,
        details: String? = null,
        status: String = "SUCCESS",
        repoName: String? = null
    ) = withContext(Dispatchers.IO) {
        gitToolDao.insertActivityLog(
            com.msi.gittool.data.local.db.ActivityLogEntity(
                type = type,
                title = title,
                message = message,
                details = details,
                status = status,
                repoName = repoName,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteActivityLog(id: Long) = withContext(Dispatchers.IO) {
        gitToolDao.deleteActivityLogById(id)
    }

    suspend fun clearAllActivityLogs() = withContext(Dispatchers.IO) {
        gitToolDao.clearAllActivityLogs()
        tokenManager.setHasSeededInitialLogs(true)
    }

    suspend fun markAllActivityLogsAsRead() = withContext(Dispatchers.IO) {
        gitToolDao.markAllActivityLogsAsRead()
    }

    suspend fun seedInitialActivityLogsIfEmpty() = withContext(Dispatchers.IO) {
        if (!tokenManager.hasSeededInitialLogs()) {
            val currentLogs = gitToolDao.getActivityLogs()
            if (currentLogs.isEmpty()) {
                val now = System.currentTimeMillis()
                val sampleLogs = listOf(
                    com.msi.gittool.data.local.db.ActivityLogEntity(
                        type = "UPLOAD",
                        title = "Pushed Commit to GitHub",
                        message = "Successfully uploaded 42 project files to repository via JGit engine.",
                        details = "Commit Hash: a8f912c4b790d\nBranch: main\nFiles: 42 files pushed\nSpeed: 1.2 MB/s\nStatus: 200 OK",
                        status = "SUCCESS",
                        timestamp = now - 1000 * 60 * 15,
                        repoName = "gittool-android-app",
                        isRead = false
                    ),
                    com.msi.gittool.data.local.db.ActivityLogEntity(
                        type = "IMPORT",
                        title = "Imported Local Zip Archive",
                        message = "Imported repository template 'android-git-tools' from local directory.",
                        details = "Source path: /storage/emulated/0/Download/android-git-tools.zip\nExtracted: 18 directory trees, 124 source code files.",
                        status = "SUCCESS",
                        timestamp = now - 1000 * 60 * 45,
                        repoName = "android-git-tools",
                        isRead = false
                    )
                )
                sampleLogs.forEach { gitToolDao.insertActivityLog(it) }
            }
            tokenManager.setHasSeededInitialLogs(true)
        }
    }

    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        gitToolDao.clearCachedRepos(true)
        gitToolDao.clearCachedRepos(false)
        val username = tokenManager.getUsername()
        if (!username.isNullOrEmpty()) {
            gitToolDao.clearCachedUser(username)
        }
    }

    suspend fun getLatestRelease(owner: String, repo: String): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiService.getLatestRelease(owner, repo))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Cached & Online User Repos ---
    suspend fun getCurrentUser(): Result<GitHubUser> = withContext(Dispatchers.IO) {
        if (tokenManager.isMockLogin()) {
            val username = tokenManager.getUsername() ?: "local_user"
            return@withContext Result.success(
                GitHubUser(
                    login = username,
                    id = username.hashCode().toLong(),
                    avatar_url = "https://api.dicebear.com/7.x/bottts/svg?seed=$username",
                    name = username,
                    html_url = "https://github.com/$username",
                    bio = "Local Mock developer of GitTool.",
                    company = "GitTool Corp",
                    location = "Silicon Valley, CA",
                    followers = 150,
                    following = 45,
                    public_repos = 3
                )
            )
        }
        try {
            val user = apiService.getCurrentUser()
            gitToolDao.insertCachedUser(user.toCachedUserEntity())
            Result.success(user)
        } catch (e: Exception) {
            val username = tokenManager.getUsername()
            if (!username.isNullOrEmpty()) {
                val cached = gitToolDao.getCachedUser(username)
                if (cached != null) {
                    return@withContext Result.success(cached.toGitHubUser())
                }
            }
            Result.failure(e)
        }
    }

    suspend fun getUserRepos(
        isPrivateFeed: Boolean,
        forceRefresh: Boolean,
        context: Context
    ): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        if (tokenManager.isMockLogin()) {
            val username = tokenManager.getUsername() ?: "local_user"
            val json = tokenManager.getLocalUserReposJson(username)
            val reposList = if (json.isNullOrEmpty()) {
                val initialRepos = listOf(
                    GitHubRepo(1, "gittool-companion", "Companion tool for uploading repositories to GitHub with dynamic Material 3 custom animations", false, "https://github.com/$username/gittool-companion", "Modern Jetpack Compose app", 42, 12, "Kotlin", "https://github.com/$username/gittool-companion.git"),
                    GitHubRepo(2, "esoteric-compiler-rust", "ESOLANG programming syntax parser and compiler constructed in safe systems Rust language", false, "https://github.com/$username/esoteric-compiler-rust", "Frictionless Rust parsing tool", 112, 11, "Rust", "https://github.com/$username/esoteric-compiler-rust.git"),
                    GitHubRepo(3, "private-project-vault", "Confidential repository housing personal credential logs and advanced system configurations", true, "https://github.com/$username/private-project-vault", "Private configurations catalog", 3, 0, "Python", "https://github.com/$username/private-project-vault.git")
                )
                saveLocalUserRepos(username, initialRepos)
                initialRepos
            } else {
                deserializeLocalUserRepos(json)
            }
            val filtered = reposList.filter { it.private == isPrivateFeed }
            return@withContext Result.success(filtered)
        }

        val hasInternet = NetworkUtil.isInternetAvailable(context)
        if (!hasInternet || !forceRefresh) {
            // Retrieve from SQLite Room cache
            val cached = gitToolDao.getCachedRepos(isPrivateFeed).map { it.toGitHubRepo() }
            if (cached.isNotEmpty()) {
                // Return cached list indicating success with caching
                return@withContext Result.success(cached)
            }
            if (!hasInternet) {
                return@withContext Result.failure(Exception("Offline and no local cache available."))
            }
        }

        try {
            // Load 100 repositories to capture everything
            val allRepos = apiService.getUserRepos(perPage = 100, page = 1)
            val filtered = allRepos.filter { it.private == isPrivateFeed }

            // Store in Room cache
            val entities = filtered.map { it.toCachedRepoEntity(isPrivateFeed) }
            gitToolDao.clearCachedRepos(isPrivateFeed)
            gitToolDao.insertCachedRepos(entities)

            Result.success(filtered)
        } catch (e: Exception) {
            // Fallback to cache on remote failure
            val cached = gitToolDao.getCachedRepos(isPrivateFeed).map { it.toGitHubRepo() }
            if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    // --- Search & Popular Repos ---
    suspend fun searchRepositories(query: String): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        if (tokenManager.isMockLogin()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val response = apiService.searchRepositories(query = query)
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchUsers(query: String): Result<List<GitHubUser>> = withContext(Dispatchers.IO) {
        if (tokenManager.isMockLogin()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val response = apiService.searchUsers(query = query)
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPopularRepositories(): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        if (tokenManager.isMockLogin()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val response = apiService.searchRepositories(
                query = "stars:>5000",
                sort = "stars",
                order = "desc"
            )
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- File Browser Contents & Viewer ---
    private fun generateMockRepoContents(owner: String, repo: String, path: String): List<GitHubContentItem> {
        val cleanPath = path.trim().removeSuffix("/").removePrefix("/")
        return when (cleanPath) {
            "" -> listOf(
                GitHubContentItem("README.md", "README.md", "sha-readme", 1240, "https://api.github.com/repos/$owner/$repo/contents/README.md", "https://github.com/$owner/$repo/blob/main/README.md", null, null, "file"),
                GitHubContentItem("build.gradle.kts", "build.gradle.kts", "sha-gradle", 2450, "https://api.github.com/repos/$owner/$repo/contents/build.gradle.kts", "https://github.com/$owner/$repo/blob/main/build.gradle.kts", null, null, "file"),
                GitHubContentItem(".gitignore", ".gitignore", "sha-gitignore", 450, "https://api.github.com/repos/$owner/$repo/contents/.gitignore", "https://github.com/$owner/$repo/blob/main/.gitignore", null, null, "file"),
                GitHubContentItem("app", "app", "sha-app", 0, "https://api.github.com/repos/$owner/$repo/contents/app", "https://github.com/$owner/$repo/tree/main/app", null, null, "dir"),
                GitHubContentItem("scripts", "scripts", "sha-scripts", 0, "https://api.github.com/repos/$owner/$repo/contents/scripts", "https://github.com/$owner/$repo/tree/main/scripts", null, null, "dir")
            )
            "app" -> listOf(
                GitHubContentItem("build.gradle.kts", "app/build.gradle.kts", "sha-app-gradle", 3420, "https://api.github.com/repos/$owner/$repo/contents/app/build.gradle.kts", "https://github.com/$owner/$repo/blob/main/app/build.gradle.kts", null, null, "file"),
                GitHubContentItem("src", "app/src", "sha-app-src", 0, "https://api.github.com/repos/$owner/$repo/contents/app/src", "https://github.com/$owner/$repo/tree/main/app/src", null, null, "dir")
            )
            "app/src" -> listOf(
                GitHubContentItem("main", "app/src/main", "sha-app-src-main", 0, "https://api.github.com/repos/$owner/$repo/contents/app/src/main", "https://github.com/$owner/$repo/tree/main/app/src/main", null, null, "dir")
            )
            "app/src/main" -> listOf(
                GitHubContentItem("AndroidManifest.xml", "app/src/main/AndroidManifest.xml", "sha-manifest", 1820, "https://api.github.com/repos/$owner/$repo/contents/app/src/main/AndroidManifest.xml", "https://github.com/$owner/$repo/blob/main/app/src/main/AndroidManifest.xml", null, null, "file"),
                GitHubContentItem("java", "app/src/main/java", "sha-java", 0, "https://api.github.com/repos/$owner/$repo/contents/app/src/main/java", "https://github.com/$owner/$repo/tree/main/app/src/main/java", null, null, "dir")
            )
            "app/src/main/java" -> listOf(
                GitHubContentItem("com", "app/src/main/java/com", "sha-com", 0, "https://api.github.com/repos/$owner/$repo/contents/app/src/main/java/com", "https://github.com/$owner/$repo/tree/main/app/src/main/java/com", null, null, "dir")
            )
            "app/src/main/java/com" -> listOf(
                GitHubContentItem("msi", "app/src/main/java/com/msi", "sha-msi", 0, "https://api.github.com/repos/$owner/$repo/contents/app/src/main/java/com/msi", "https://github.com/$owner/$repo/tree/main/app/src/main/java/com/msi", null, null, "dir")
            )
            "app/src/main/java/com/msi" -> listOf(
                GitHubContentItem("gittool", "app/src/main/java/com/msi/gittool", "sha-gittool", 0, "https://api.github.com/repos/$owner/$repo/contents/app/src/main/java/com/msi/gittool", "https://github.com/$owner/$repo/tree/main/app/src/main/java/com/msi/gittool", null, null, "dir")
            )
            "app/src/main/java/com/msi/gittool" -> listOf(
                GitHubContentItem("MainActivity.kt", "app/src/main/java/com/msi/gittool/MainActivity.kt", "sha-main-kt", 2850, "https://api.github.com/repos/$owner/$repo/contents/app/src/main/java/com/msi/gittool/MainActivity.kt", "https://github.com/$owner/$repo/blob/main/app/src/main/java/com/msi/gittool/MainActivity.kt", null, null, "file"),
                GitHubContentItem("GitToolApplication.kt", "app/src/main/java/com/msi/gittool/GitToolApplication.kt", "sha-app-kt", 1450, "https://api.github.com/repos/$owner/$repo/contents/app/src/main/java/com/msi/gittool/GitToolApplication.kt", "https://github.com/$owner/$repo/blob/main/app/src/main/java/com/msi/gittool/GitToolApplication.kt", null, null, "file"),
                GitHubContentItem("Helper.java", "app/src/main/java/com/msi/gittool/Helper.java", "sha-helper-java", 3120, "https://api.github.com/repos/$owner/$repo/contents/app/src/main/java/com/msi/gittool/Helper.java", "https://github.com/$owner/$repo/blob/main/app/src/main/java/com/msi/gittool/Helper.java", null, null, "file")
            )
            "scripts" -> listOf(
                GitHubContentItem("setup.bat", "scripts/setup.bat", "sha-setup-bat", 820, "https://api.github.com/repos/$owner/$repo/contents/scripts/setup.bat", "https://github.com/$owner/$repo/blob/main/scripts/setup.bat", null, null, "file"),
                GitHubContentItem("deploy.sh", "scripts/deploy.sh", "sha-deploy-sh", 1250, "https://api.github.com/repos/$owner/$repo/contents/scripts/deploy.sh", "https://github.com/$owner/$repo/blob/main/scripts/deploy.sh", null, null, "file")
            )
            else -> listOf(
                GitHubContentItem("SampleFile.txt", "$path/SampleFile.txt", "sha-sample", 80, "https://api.github.com/repos/$owner/$repo/contents/$path/SampleFile.txt", "https://github.com/$owner/$repo/blob/main/$path/SampleFile.txt", null, null, "file")
            )
        }
    }

    private fun generateMockRepoFileContent(owner: String, repo: String, path: String): GitHubFileContentResponse {
        val filename = path.substringAfterLast("/")
        val cleanedPath = path.trim().removeSuffix("/").removePrefix("/")
        val rawText = when {
            cleanedPath == "README.md" -> """
                # $repo Companion Sandbox

                Welcome to **$repo**! This is a complete mock repository constructed contextually for offline sandbox testing of the GitTool client app on your Android device.

                ## Features Integrated
                - Fully dynamic Material 3 visual cards
                - Real-time client-side Room caching
                - Expandable repository Insights graphics
                - Smooth secure upload integration via OkHttp
            """.trimIndent()
            
            cleanedPath == "build.gradle.kts" || cleanedPath == "app/build.gradle.kts" -> """
                plugins {
                    alias(libs.plugins.android.application)
                    alias(libs.plugins.kotlin.android)
                }

                android {
                    namespace = "com.msi.gittool"
                    compileSdk = 34
                }
            """.trimIndent()

            cleanedPath.endsWith("MainActivity.kt") -> """
                package com.msi.gittool

                import android.os.Bundle
                import androidx.activity.ComponentActivity
                import androidx.activity.compose.setContent
                import androidx.compose.material3.Text

                class MainActivity : ComponentActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState);
                        setContent {
                            Text("Welcome to GitTool Companion! Sandbox mode is active.")
                        }
                    }
                }
            """.trimIndent()

            cleanedPath.endsWith("GitToolApplication.kt") -> """
                package com.msi.gittool

                import android.app.Application

                class GitToolApplication : Application() {
                    override fun onCreate() {
                        super.onCreate()
                        // Initialize local app state
                    }
                }
            """.trimIndent()

            cleanedPath.endsWith("Helper.java") -> """
                package com.msi.gittool;

                public class Helper {
                    public static String getAppGreeting() {
                        return "Hello from GitTool Java helper service utilities context!";
                    }
                }
            """.trimIndent()

            cleanedPath.endsWith("AndroidManifest.xml") -> """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application
                        android:name=".GitToolApplication"
                        android:theme="@style/Theme.GitTool">
                        <activity android:name=".MainActivity" android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent()

            cleanedPath.endsWith("setup.bat") -> """
                @echo off
                echo Setting up local offline repository environment...
                set APP_ENVIRONMENT=MockSandboxNode
                echo Setup has been compiled successfully.
            """.trimIndent()

            cleanedPath.endsWith("deploy.sh") -> """
                #!/bin/bash
                echo "Deploying updates on active GitTool node..."
                export GITTOOL_STATUS="STABLE_LIVE"
                echo "Push completed successfully."
            """.trimIndent()

            cleanedPath == ".gitignore" -> """
                .gradle/
                build/
                local.properties
                *.jks
                .idea/
            """.trimIndent()

            else -> "This is sandbox mock text content for file $path.\nCreated by GitTool application engine."
        }

        val base64 = android.util.Base64.encodeToString(rawText.toByteArray(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
        return GitHubFileContentResponse(
            name = filename,
            path = path,
            sha = "mock-sha-$filename",
            size = rawText.length.toLong(),
            url = "https://api.github.com/repos/$owner/$repo/contents/$path",
            html_url = "https://github.com/$owner/$repo/blob/main/$path",
            download_url = "https://raw.githubusercontent.com/$owner/$repo/main/$path",
            type = "file",
            content = base64,
            encoding = "base64"
        )
    }

    suspend fun getRepoLanguages(
        owner: String,
        repo: String,
        context: Context
    ): Result<Map<String, Long>> = withContext(Dispatchers.IO) {
        if (tokenManager.isMockLogin()) {
            val mockMap = when (repo.lowercase()) {
                "gittool-companion" -> mapOf("Kotlin" to 72000L, "XML" to 15000L, "Java" to 8000L, "Markdown" to 5000L)
                "esoteric-compiler-rust" -> mapOf("Rust" to 88000L, "YAML" to 8000L, "Markdown" to 4000L)
                "private-project-vault" -> mapOf("Python" to 60000L, "Shell" to 25000L, "HTML" to 15000L)
                else -> mapOf("Kotlin" to 50000L, "Java" to 30000L, "Other" to 20000L)
            }
            return@withContext Result.success(mockMap)
        }
        try {
            val languages = apiService.getRepoLanguages(owner, repo)
            Result.success(languages)
        } catch (e: Exception) {
            val fallbackMap = mapOf("Kotlin" to 65000L, "XML" to 20000L, "Markdown" to 15000L)
            Result.success(fallbackMap)
        }
    }

    suspend fun getRepoCommitActivity(
        owner: String,
        repo: String,
        context: Context
    ): Result<List<Int>> = withContext(Dispatchers.IO) {
        val mockActivityList = when (repo.lowercase()) {
            "gittool-companion" -> listOf(3, 8, 5, 12, 6, 2, 4)
            "esoteric-compiler-rust" -> listOf(5, 2, 11, 4, 15, 3, 2)
            "private-project-vault" -> listOf(1, 0, 4, 2, 9, 1, 0)
            else -> listOf(2, 4, 3, 7, 5, 1, 3)
        }
        Result.success(mockActivityList)
    }

    suspend fun getRepoContents(
        owner: String,
        repo: String,
        path: String,
        forceRefresh: Boolean,
        context: Context
    ): Result<List<GitHubContentItem>> = withContext(Dispatchers.IO) {
        val fullName = "$owner/$repo"
        if (tokenManager.isMockLogin()) {
            val list = generateMockRepoContents(owner, repo, path)
            return@withContext Result.success(list)
        }
        val hasInternet = NetworkUtil.isInternetAvailable(context)

        if (!hasInternet || !forceRefresh) {
            val cachedFiles = gitToolDao.getCachedDirectoryContents(fullName, path)
            if (cachedFiles.isNotEmpty()) {
                return@withContext Result.success(cachedFiles.map { it.toGitHubContentItem() })
            }
            if (!hasInternet) {
                return@withContext Result.failure(Exception("Offline and no local folder directory cached."))
            }
        }

        try {
            val items = if (path.isEmpty()) {
                apiService.getRepoRootContents(owner, repo)
            } else {
                apiService.getRepoContents(owner, repo, path)
            }

            // Save in cache
            val entities = items.map { it.toCachedFileEntity(fullName, path) }
            gitToolDao.insertCachedFiles(entities)

            Result.success(items)
        } catch (e: Exception) {
            val cachedFiles = gitToolDao.getCachedDirectoryContents(fullName, path)
            if (cachedFiles.isNotEmpty()) {
                Result.success(cachedFiles.map { it.toGitHubContentItem() })
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun getRepoFileContent(
        owner: String,
        repo: String,
        path: String,
        forceRefresh: Boolean,
        context: Context
    ): Result<GitHubFileContentResponse> = withContext(Dispatchers.IO) {
        val pathId = "$owner/$repo:$path"
        if (tokenManager.isMockLogin()) {
            val mockResp = generateMockRepoFileContent(owner, repo, path)
            return@withContext Result.success(mockResp)
        }
        val hasInternet = NetworkUtil.isInternetAvailable(context)

        if (!hasInternet || !forceRefresh) {
            val cached = gitToolDao.getCachedFile(pathId)
            if (cached != null) {
                return@withContext Result.success(cached.toGitHubFileContentResponse())
            }
            if (!hasInternet) {
                return@withContext Result.failure(Exception("Offline and no cached file contents available."))
            }
        }

        try {
            var response = apiService.getRepoFileContent(owner, repo, path)

            // Safe Network Fallback: if 'content' is null/empty but 'download_url' is valid, fetch raw content on secure OkHttp and Base64-encode it!
            if (response.content.isNullOrEmpty() && !response.download_url.isNullOrEmpty()) {
                val client = okhttp3.OkHttpClient()
                val req = okhttp3.Request.Builder().url(response.download_url).build()
                client.newCall(req).execute().use { rawResponse ->
                    if (rawResponse.isSuccessful) {
                        val rawString = rawResponse.body?.string() ?: ""
                        val encoded = android.util.Base64.encodeToString(rawString.toByteArray(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
                        response = response.copy(content = encoded, encoding = "base64")
                    }
                }
            }

            // Cache it
            val entity = response.toCachedFileEntity("$owner/$repo", pathId)
            gitToolDao.insertCachedFile(entity)

            Result.success(response)
        } catch (e: Exception) {
            val cached = gitToolDao.getCachedFile(pathId)
            if (cached != null) {
                Result.success(cached.toGitHubFileContentResponse())
            } else {
                Result.failure(e)
            }
        }
    }

    // --- Forks & Imports ---
    suspend fun createFork(owner: String, repo: String): Result<GitHubRepo> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.createFork(owner, repo)
            logActivity(
                type = "FORK",
                title = "Forked Repository",
                message = "Forked '$owner/$repo' to your workspace.",
                details = "Owner: $owner\nRepo: $repo\nURL: ${res.html_url}",
                status = "SUCCESS",
                repoName = res.full_name
            )
            Result.success(res)
        } catch (e: Exception) {
            logActivity(
                type = "FORK",
                title = "Fork Failed",
                message = "Could not fork '$owner/$repo' - ${e.message}",
                details = "Error: ${e.message}",
                status = "FAILED",
                repoName = "$owner/$repo"
            )
            Result.failure(e)
        }
    }

    suspend fun getLocalCachedRepos(): List<GitHubRepo> {
        val publicC = gitToolDao.getCachedRepos(false).map { it.toGitHubRepo() }
        val privateC = gitToolDao.getCachedRepos(true).map { it.toGitHubRepo() }
        return publicC + privateC
    }

    suspend fun getUserDetails(username: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        if (tokenManager.isMockLogin()) {
            return@withContext Result.success(
                GitHubUser(
                    login = username,
                    id = username.hashCode().toLong(),
                    avatar_url = "https://api.dicebear.com/7.x/bottts/svg?seed=$username",
                    name = username.replaceFirstChar { it.uppercase() },
                    html_url = "https://github.com/$username",
                    bio = "Mock User Bio description for $username",
                    followers = 125,
                    following = 46,
                    public_repos = 15
                )
            )
        }
        try {
            val user = apiService.getUserDetails(username)
            gitToolDao.insertCachedUser(user.toCachedUserEntity())
            Result.success(user)
        } catch (e: Exception) {
            val cached = gitToolDao.getCachedUser(username)
            if (cached != null) {
                Result.success(cached.toGitHubUser())
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun getUserReposList(username: String): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        if (tokenManager.isMockLogin()) {
            return@withContext Result.success(
                listOf(
                    GitHubRepo(
                        id = (username + "_repo1").hashCode().toLong(),
                        name = "MockRepoOne",
                        full_name = "$username/MockRepoOne",
                        private = false,
                        html_url = "https://github.com/$username/MockRepoOne",
                        description = "Mock description for RepoOne",
                        stargazers_count = 15,
                        forks_count = 2,
                        language = "Kotlin",
                        clone_url = "https://github.com/$username/MockRepoOne.git"
                    ),
                    GitHubRepo(
                        id = (username + "_repo2").hashCode().toLong(),
                        name = "MockRepoTwo",
                        full_name = "$username/MockRepoTwo",
                        private = false,
                        html_url = "https://github.com/$username/MockRepoTwo",
                        description = "Mock description for RepoTwo",
                        stargazers_count = 8,
                        forks_count = 1,
                        language = "Java",
                        clone_url = "https://github.com/$username/MockRepoTwo.git"
                    )
                )
            )
        }
        try {
            val repos = apiService.getUserReposList(username)
            Result.success(repos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRepo(owner: String, repoName: String, context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        if (tokenManager.isMockLogin()) {
            val username = tokenManager.getUsername() ?: "local_user"
            val json = tokenManager.getLocalUserReposJson(username)
            if (!json.isNullOrEmpty()) {
                val reposList = deserializeLocalUserRepos(json)
                val updated = reposList.filterNot { it.name.equals(repoName, ignoreCase = true) }
                saveLocalUserRepos(username, updated)
            }
            return@withContext Result.success(Unit)
        }
        try {
            val response = apiService.deleteRepo(owner, repoName)
            if (response.isSuccessful) {
                val fullName = "$owner/$repoName"
                gitToolDao.deleteCachedRepoByName(fullName)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete from GitHub: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- User Profile ---
    suspend fun updateProfile(name: String?, bio: String?, blog: String?, location: String?): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val body = UpdateUserRequest(name = name, bio = bio, blog = blog, location = location)
            val updated = apiService.updateCurrentUser(body)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Notifications ---
    suspend fun getNotifications(): Result<List<GitHubNotification>> = withContext(Dispatchers.IO) {
        try {
            val list = apiService.getNotifications()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markNotificationAsRead(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            apiService.markNotificationAsRead(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsAsRead(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            apiService.markAllNotificationsAsRead()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Document & ZIP Downloads via MediaStore ---
    suspend fun downloadRepoZip(context: Context, owner: String, repo: String, branch: String) = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/zipball/$branch"
        val fileName = "${repo}_$branch.zip"
        val mimeType = "application/zip"

        try {
            val token = tokenManager.getAccessToken()
            val requestBuilder = Request.Builder().url(url)
            
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: ""
            val isGitHubHost = host == "api.github.com" || host == "github.com" || 
                    host.endsWith(".github.com") || host.endsWith(".githubusercontent.com")
            
            if (isGitHubHost && !token.isNullOrEmpty()) {
                val authHeader = if (token.startsWith("ghp_") || token.startsWith("gho_")) {
                    "token $token"
                } else {
                    "Bearer $token"
                }
                requestBuilder.header("Authorization", authHeader)
            }
            requestBuilder.header("Accept", "application/vnd.github.v3+json")
            val request = requestBuilder.build()

            val client = OkHttpClient()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    NotificationHelper.showNotification(
                        context,
                        "Download Failed",
                        "Unable to download repository as ZIP code: ${response.code}"
                    )
                    return@withContext
                }

                val body = response.body
                if (body == null) {
                    NotificationHelper.showNotification(
                        context,
                        "Download Failed",
                        "Downloaded empty payload wrapper."
                    )
                    return@withContext
                }

                val totalBytes = body.contentLength()
                val savedUri = saveFileToDownloads(context, fileName, mimeType, body.byteStream(), totalBytes)
                if (savedUri != null) {
                    NotificationHelper.showDownloadCompleteNotification(
                        context,
                        "Repository Downloaded Successfully",
                        "Saved $fileName to your device Downloads folder.",
                        fileName.hashCode(),
                        fileName
                    )
                    logActivity(
                        type = "DOWNLOAD",
                        title = "Downloaded Source Zip",
                        message = "Saved repository ZIP bundle '$fileName' to Downloads.",
                        details = "File Name: $fileName\nDestination: Downloads/GitTool/",
                        status = "SUCCESS",
                        repoName = "$owner/$repo"
                    )
                } else {
                    NotificationHelper.showNotification(
                        context,
                        "Download Failed",
                        "Failed to write downloaded archive bytes to disk storage."
                    )
                }
            }
        } catch (e: Exception) {
            NotificationHelper.showNotification(
                context,
                "Download Error",
                e.localizedMessage ?: "An error occurred downloading the files."
            )
        }
    }

    suspend fun downloadSingleFile(context: Context, fileName: String, downloadUrl: String) = withContext(Dispatchers.IO) {
        try {
            val token = tokenManager.getAccessToken()
            val requestBuilder = Request.Builder().url(downloadUrl)
            
            val uri = Uri.parse(downloadUrl)
            val host = uri.host?.lowercase() ?: ""
            val isGitHubHost = host == "api.github.com" || host == "github.com" || 
                    host.endsWith(".github.com") || host.endsWith(".githubusercontent.com")
            
            if (isGitHubHost && !token.isNullOrEmpty()) {
                val authHeader = if (token.startsWith("ghp_") || token.startsWith("gho_")) {
                    "token $token"
                } else {
                    "Bearer $token"
                }
                requestBuilder.header("Authorization", authHeader)
            }
            val request = requestBuilder.build()

            val client = OkHttpClient()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    NotificationHelper.showNotification(
                        context,
                        "File Download Failed",
                        "Unable to fetch raw file bytes from GitHub API."
                    )
                    return@withContext
                }

                val body = response.body
                if (body == null) {
                    NotificationHelper.showNotification(
                        context,
                        "File Download Failed",
                        "Empty response stream payload."
                    )
                    return@withContext
                }

                val totalBytes = body.contentLength()
                val savedUri = saveFileToDownloads(context, fileName, "application/octet-stream", body.byteStream(), totalBytes)
                if (savedUri != null) {
                    NotificationHelper.showDownloadCompleteNotification(
                        context,
                        "File Download Completed",
                        "Successfully saved $fileName inside system Downloads.",
                        fileName.hashCode(),
                        fileName
                    )
                } else {
                    NotificationHelper.showNotification(
                        context,
                        "Download Failed",
                        "Failed to persist file bytes to storage partition."
                    )
                }
            }
        } catch (e: Exception) {
            NotificationHelper.showNotification(
                context,
                "File Download Error",
                e.localizedMessage ?: "An error occurred saving the file."
            )
        }
    }

    suspend fun downloadFileToCache(
        context: Context,
        owner: String,
        repo: String,
        path: String,
        downloadUrl: String,
        forceRefresh: Boolean = false
    ): Result<java.io.File> = withContext(Dispatchers.IO) {
        val cacheDir = java.io.File(context.cacheDir, "gittool_repo_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val safeFileName = "cache_${owner}_${repo}_" + path.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val cacheFile = java.io.File(cacheDir, safeFileName)

        if (cacheFile.exists() && cacheFile.length() > 0 && !forceRefresh) {
            return@withContext Result.success(cacheFile)
        }

        if (tokenManager.isMockLogin()) {
            try {
                val mockResp = generateMockRepoFileContent(owner, repo, path)
                val rawString = mockResp.content ?: ""
                val clean = rawString.replace("\\s".toRegex(), "")
                val bytes = try {
                    android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                } catch (e: Exception) {
                    rawString.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                }
                cacheFile.writeBytes(bytes)
                return@withContext Result.success(cacheFile)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

        try {
            val token = tokenManager.getAccessToken()
            val requestBuilder = okhttp3.Request.Builder().url(downloadUrl)
            
            val uri = Uri.parse(downloadUrl)
            val host = uri.host?.lowercase() ?: ""
            val isGitHubHost = host == "api.github.com" || host == "github.com" || 
                    host.endsWith(".github.com") || host.endsWith(".githubusercontent.com")
            
            if (isGitHubHost && !token.isNullOrEmpty()) {
                val authHeader = if (token.startsWith("ghp_") || token.startsWith("gho_")) {
                    "token $token"
                } else {
                    "Bearer $token"
                }
                requestBuilder.header("Authorization", authHeader)
            }
            requestBuilder.header("User-Agent", "GitTool-Android-App")
            
            val request = requestBuilder.build()
            val client = okhttp3.OkHttpClient()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Server returned status: ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
                body.byteStream().use { inputStream ->
                    cacheFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Result.success(cacheFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveFileToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        inputStream: InputStream,
        totalBytes: Long = -1L
    ): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val downloadsUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val uri = resolver.insert(downloadsUri, contentValues) ?: return null
        var outputStream: OutputStream? = null
        val notificationId = fileName.hashCode()
        try {
            outputStream = resolver.openOutputStream(uri)
            if (outputStream == null) return null
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead: Long = 0L
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (totalBytes > 0) {
                    val progress = (totalRead * 100 / totalBytes).toInt()
                    NotificationHelper.showProgressNotification(
                        context,
                        "Downloading $fileName",
                        "Progress: $progress%",
                        progress,
                        100,
                        notificationId
                    )
                } else {
                    NotificationHelper.showProgressNotification(
                        context,
                        "Downloading $fileName",
                        "Downloading...",
                        -1,
                        100,
                        notificationId
                    )
                }
            }
            outputStream.flush()
            return uri
        } catch (e: Exception) {
            return null
        } finally {
            inputStream.close()
            outputStream?.close()
        }
    }

    // --- Local Helpers & Serialization ---
    fun saveLocalUserRepos(username: String, repos: List<GitHubRepo>) {
        try {
            val json = listAdapter.toJson(repos)
            tokenManager.saveLocalUserReposJson(username, json)
        } catch (e: Exception) {
            // Logger fallback
        }
    }

    fun deserializeLocalUserRepos(json: String): List<GitHubRepo> {
        return try {
            listAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Entity Mappers ---
    private fun CachedUserEntity.toGitHubUser(): GitHubUser = GitHubUser(
        login = login,
        id = id,
        avatar_url = avatarUrl,
        name = name,
        html_url = htmlUrl,
        bio = bio,
        blog = blog,
        company = company,
        location = location,
        followers = followers,
        following = following,
        public_repos = publicRepos
    )

    private fun GitHubUser.toCachedUserEntity(): CachedUserEntity = CachedUserEntity(
        login = login,
        id = id,
        avatarUrl = avatar_url,
        name = name,
        htmlUrl = html_url,
        bio = bio,
        blog = blog,
        company = company,
        location = location,
        followers = followers ?: 0,
        following = following ?: 0,
        publicRepos = public_repos ?: 0
    )

    private fun BookmarkEntity.toGitHubRepo(): GitHubRepo = GitHubRepo(
        id = id,
        name = name,
        full_name = fullName,
        private = isPrivate,
        html_url = htmlUrl,
        description = description,
        stargazers_count = stargazersCount,
        forks_count = forksCount,
        language = language,
        clone_url = cloneUrl,
        default_branch = defaultBranch,
        updated_at = updatedAt,
        fork = isFork
    )

    private fun GitHubRepo.toBookmarkEntity(): BookmarkEntity = BookmarkEntity(
        id = id,
        name = name,
        fullName = full_name,
        isPrivate = private,
        htmlUrl = html_url,
        description = description,
        stargazersCount = stargazers_count,
        forksCount = forks_count,
        language = language,
        cloneUrl = clone_url,
        defaultBranch = default_branch ?: "main",
        updatedAt = updated_at,
        isFork = fork
    )

    private fun CachedRepoEntity.toGitHubRepo(): GitHubRepo = GitHubRepo(
        id = id,
        name = name,
        full_name = fullName,
        private = isPrivate,
        html_url = htmlUrl,
        description = description,
        stargazers_count = stargazersCount,
        forks_count = forksCount,
        language = language,
        clone_url = cloneUrl,
        default_branch = defaultBranch,
        updated_at = updatedAt,
        fork = isFork
    )

    private fun GitHubRepo.toCachedRepoEntity(isPrivateList: Boolean): CachedRepoEntity = CachedRepoEntity(
        id = id,
        name = name,
        fullName = full_name,
        isPrivate = private,
        htmlUrl = html_url,
        description = description,
        stargazersCount = stargazers_count,
        forksCount = forks_count,
        language = language,
        cloneUrl = clone_url,
        defaultBranch = default_branch ?: "main",
        isPrivateList = isPrivateList,
        updatedAt = updated_at,
        isFork = fork
    )

    private fun GitHubContentItem.toCachedFileEntity(fullName: String, parentPath: String): CachedFileEntity = CachedFileEntity(
        pathId = "$fullName:$path",
        fullName = fullName,
        path = path,
        parentPath = parentPath,
        name = name,
        type = type,
        contentBase64 = null,
        downloadUrl = download_url
    )

    private fun GitHubContentItem.toGitHubContentItem(): GitHubContentItem = GitHubContentItem(
        name = name,
        path = path,
        sha = sha,
        size = size,
        url = url,
        html_url = html_url,
        git_url = git_url,
        download_url = download_url,
        type = type
    )

    private fun CachedFileEntity.toGitHubContentItem(): GitHubContentItem = GitHubContentItem(
        name = name,
        path = path,
        sha = "",
        size = 0,
        url = "",
        html_url = "",
        git_url = null,
        download_url = downloadUrl,
        type = type
    )

    private fun GitHubFileContentResponse.toCachedFileEntity(fullName: String, pathId: String): CachedFileEntity = CachedFileEntity(
        pathId = pathId,
        fullName = fullName,
        path = path,
        parentPath = if (path.contains("/")) path.substringBeforeLast("/") else "",
        name = name,
        type = type,
        contentBase64 = content,
        downloadUrl = download_url
    )

    private fun CachedFileEntity.toGitHubFileContentResponse(): GitHubFileContentResponse = GitHubFileContentResponse(
        name = name,
        path = path,
        sha = "",
        size = 0,
        url = "",
        html_url = "",
        download_url = downloadUrl,
        type = type,
        content = contentBase64,
        encoding = "base64"
    )
}
