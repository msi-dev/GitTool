package com.msi.gittool.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.msi.gittool.data.local.TokenManager
import com.msi.gittool.data.remote.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class ProjectFile(
    val relativePath: String,
    val fileUri: Uri,
    val size: Long
)

data class FolderScanResult(
    val files: List<ProjectFile> = emptyList(),
    val fileCount: Int = 0,
    val folderCount: Int = 0,
    val totalSizeBytes: Long = 0L
)

class UploadRepository(
    private val apiService: GitHubApiService,
    private val context: Context,
    private val tokenManager: TokenManager
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val repoListType = Types.newParameterizedType(List::class.java, GitHubRepo::class.java)
    private val listAdapter = moshi.adapter<List<GitHubRepo>>(repoListType)

    private suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 8,
        initialDelayMs: Long = 1000,
        onRateLimit: ((String) -> Unit)? = null,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(maxAttempts - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                val isRateLimit = if (e is retrofit2.HttpException) {
                    val code = e.code()
                    code == 403 || code == 429 || code == 503 || code == 502
                } else {
                    val msg = e.message ?: ""
                    msg.contains("403") || msg.contains("429") || msg.contains("SecondaryRateLimit") || msg.contains("abuse", ignoreCase = true)
                }

                if (isRateLimit) {
                    // Exponential backoff with jitter for GitHub secondary rate limits (2.5s, 5s, 10s, 20s, 30s)
                    val backoffMs = (2500L * (1L shl attempt.coerceAtMost(4))) + (100L..500L).random()
                    onRateLimit?.invoke("GitHub rate limit hit. Pausing ${backoffMs / 1000}s before retry (Attempt ${attempt + 1}/$maxAttempts)...")
                    delay(backoffMs)
                    currentDelay = backoffMs
                } else {
                    delay(currentDelay)
                    currentDelay = (currentDelay * 1.5).toLong().coerceAtMost(10000L)
                }
            }
        }
        return block()
    }

    private fun addRepoToLocalList(username: String, repo: GitHubRepo) {
        try {
            val json = tokenManager.getLocalUserReposJson(username)
            val currentList = if (json.isNullOrEmpty()) {
                mutableListOf(
                    GitHubRepo(1, "gittool-companion", "Companion tool for uploading repositories to GitHub with dynamic Material 3 custom animations", false, "https://github.com/$username/gittool-companion", "Modern Jetpack Compose app", 42, 12, "Kotlin", "https://github.com/$username/gittool-companion.git"),
                    GitHubRepo(2, "esoteric-compiler-rust", "ESOLANG programming syntax parser and compiler constructed in safe systems Rust language", false, "https://github.com/$username/esoteric-compiler-rust", "Frictionless Rust parsing tool", 112, 11, "Rust", "https://github.com/$username/esoteric-compiler-rust.git"),
                    GitHubRepo(3, "private-project-vault", "Confidential repository housing personal credential logs and advanced system configurations", true, "https://github.com/$username/private-project-vault", "Private configurations catalog", 3, 0, "Python", "https://github.com/$username/private-project-vault.git")
                )
            } else {
                listAdapter.fromJson(json)?.toMutableList() ?: mutableListOf()
            }
            currentList.add(0, repo)
            tokenManager.saveLocalUserReposJson(username, listAdapter.toJson(currentList))
        } catch (e: Exception) {
            // safe fallback
        }
    }

    suspend fun createRepository(
        name: String,
        description: String?,
        private: Boolean
    ): Result<GitHubRepo> = withContext(Dispatchers.IO) {
        if (tokenManager.isMockLogin()) {
            val username = tokenManager.getUsername() ?: "local_user"
            val newRepo = GitHubRepo(
                id = System.currentTimeMillis(),
                name = name,
                full_name = "$username/$name",
                private = private,
                html_url = "https://github.com/$username/$name",
                description = description,
                stargazers_count = 0,
                forks_count = 0,
                language = "Kotlin",
                clone_url = "https://github.com/$username/$name.git"
            )
            addRepoToLocalList(username, newRepo)
            return@withContext Result.success(newRepo)
        }
        try {
            // 1. Check if user already has a repository with this name on GitHub
            var currentUserLogin: String? = null
            try {
                val currentUser = apiService.getCurrentUser()
                currentUserLogin = currentUser.login
                val existingRepo = apiService.getRepo(currentUser.login, name)
                // Repository exists! Return existing repo to attach as origin remote and push into
                return@withContext Result.success(existingRepo)
            } catch (_: Exception) {
                // Not found (404) or couldn't fetch existing -> proceed to create new repo
            }

            // 2. Repository does not exist -> Create new repository
            val response = apiService.createRepo(
                CreateRepoRequest(
                    name = name,
                    description = description,
                    private = private,
                    auto_init = false // Empty repo so JGit can push main branch directly
                )
            )
            Result.success(response)
        } catch (e: Exception) {
            val is422OrAlreadyExists = if (e is retrofit2.HttpException) {
                val code = e.code()
                code == 422 || code == 409 || code == 400
            } else {
                val msg = e.message ?: ""
                msg.contains("422") || msg.contains("already exists", ignoreCase = true)
            }

            if (is422OrAlreadyExists) {
                try {
                    val user = apiService.getCurrentUser()
                    val username = user.login
                    val existingRepo = try {
                        apiService.getRepo(username, name)
                    } catch (_: Exception) {
                        GitHubRepo(
                            id = System.currentTimeMillis(),
                            name = name,
                            full_name = "$username/$name",
                            private = private,
                            html_url = "https://github.com/$username/$name",
                            description = description,
                            stargazers_count = 0,
                            forks_count = 0,
                            language = "Kotlin",
                            clone_url = "https://github.com/$username/$name.git"
                        )
                    }
                    return@withContext Result.success(existingRepo)
                } catch (_: Exception) {
                    val fallbackUser = tokenManager.getUsername() ?: com.msi.gittool.git.SecureTokenManager(context).getUsername() ?: ""
                    if (fallbackUser.isNotEmpty()) {
                        val existingRepo = GitHubRepo(
                            id = System.currentTimeMillis(),
                            name = name,
                            full_name = "$fallbackUser/$name",
                            private = private,
                            html_url = "https://github.com/$fallbackUser/$name",
                            description = description,
                            stargazers_count = 0,
                            forks_count = 0,
                            language = "Kotlin",
                            clone_url = "https://github.com/$fallbackUser/$name.git"
                        )
                        return@withContext Result.success(existingRepo)
                    }
                }
            }
            Result.failure(e)
        }
    }

    suspend fun scanProjectFolderDetailed(rootTreeUri: Uri): FolderScanResult = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<ProjectFile>()
        val folderCount = java.util.concurrent.atomic.AtomicInteger(0)
        val totalSizeBytes = java.util.concurrent.atomic.AtomicLong(0L)
        val rootDir = DocumentFile.fromTreeUri(context, rootTreeUri)
        if (rootDir != null && rootDir.exists() && rootDir.isDirectory) {
            scanDirRecursive(rootDir, "", resultList, folderCount, totalSizeBytes)
        }
        FolderScanResult(
            files = resultList,
            fileCount = resultList.size,
            folderCount = folderCount.get(),
            totalSizeBytes = totalSizeBytes.get()
        )
    }

    suspend fun scanProjectFolder(rootTreeUri: Uri): List<ProjectFile> {
        return scanProjectFolderDetailed(rootTreeUri).files
    }

    private fun scanDirRecursive(
        currentDir: DocumentFile,
        currentPath: String,
        resultList: MutableList<ProjectFile>,
        folderCount: java.util.concurrent.atomic.AtomicInteger,
        totalSizeBytes: java.util.concurrent.atomic.AtomicLong
    ) {
        val files = currentDir.listFiles()
        for (file in files ?: emptyArray()) {
            val name = file.name ?: continue
            // Ignore build outputs, hidden files/folders, temporary caches, and compiled binary artifacts
            if (name.startsWith(".") || 
                name.equals("build", ignoreCase = true) || 
                name.equals("node_modules", ignoreCase = true) || 
                name.equals("bin", ignoreCase = true) || 
                name.equals("obj", ignoreCase = true) || 
                name.equals("out", ignoreCase = true) || 
                name.equals("target", ignoreCase = true) || 
                name.equals("dist", ignoreCase = true) || 
                name.equals("vendor", ignoreCase = true) || 
                name.equals(".gradle", ignoreCase = true) || 
                name.equals(".idea", ignoreCase = true) || 
                name.equals(".git", ignoreCase = true) ||
                name.equals(".cxx", ignoreCase = true) ||
                name.equals(".externalNativeBuild", ignoreCase = true) ||
                name.equals(".DS_Store", ignoreCase = true) ||
                name.equals("tmp", ignoreCase = true) ||
                name.equals("temp", ignoreCase = true) ||
                name.equals("caches", ignoreCase = true) ||
                name.endsWith(".class", ignoreCase = true) ||
                name.endsWith(".pyc", ignoreCase = true) ||
                name.endsWith(".so", ignoreCase = true) ||
                name.endsWith(".o", ignoreCase = true) ||
                name.endsWith(".a", ignoreCase = true) ||
                name.endsWith(".apk", ignoreCase = true) ||
                name.endsWith(".aar", ignoreCase = true) ||
                name.endsWith(".zip", ignoreCase = true) ||
                name.endsWith(".tar.gz", ignoreCase = true)
            ) {
                continue
            }
            
            val relativePath = if (currentPath.isEmpty()) name else "$currentPath/$name"
            if (file.isDirectory) {
                folderCount.incrementAndGet()
                scanDirRecursive(file, relativePath, resultList, folderCount, totalSizeBytes)
            } else {
                // Ignore files strictly above 100MB (GitHub's hard limit for standard blob API)
                val len = file.length()
                if (len <= 100 * 1024 * 1024) {
                    resultList.add(ProjectFile(relativePath, file.uri, len))
                    totalSizeBytes.addAndGet(len)
                }
            }
        }
    }

    suspend fun uploadProject(
        owner: String,
        repo: String,
        files: List<ProjectFile>,
        onProgress: (
            stage: String,
            progress: Float,
            count: Int,
            uploadedBytes: Long,
            totalBytes: Long,
            speedBytesPerSec: Long,
            etaSeconds: Long?
        ) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val totalBytes = files.sumOf { it.size }
        val startTimeMs = System.currentTimeMillis()

        if (tokenManager.isMockLogin()) {
            try {
                onProgress("Initializing local vault upload...", 0.05f, 0, 0L, totalBytes, 0L, null)
                var uploadedCount = 0
                var currentUploadedBytes = 0L
                for (file in files) {
                    uploadedCount++
                    currentUploadedBytes += file.size
                    val stageMsg = "Uploading files ($uploadedCount/${files.size})"
                    val uploadProgress = 0.05f + ((uploadedCount.toFloat() / files.size) * 0.80f)
                    val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
                    val speed = if (elapsedSec >= 0.2) (currentUploadedBytes / elapsedSec).toLong() else 0L
                    val remainingBytes = totalBytes - currentUploadedBytes
                    val etaSec = if (speed > 0 && remainingBytes > 0) remainingBytes / speed else null

                    onProgress(stageMsg, uploadProgress, uploadedCount, currentUploadedBytes, totalBytes, speed, etaSec)
                    kotlinx.coroutines.delay(20)
                }
                onProgress("Assembling secure archive structural tree...", 0.88f, uploadedCount, totalBytes, totalBytes, 0L, null)
                kotlinx.coroutines.delay(100)
                onProgress("Composing repository commit hashes...", 0.92f, uploadedCount, totalBytes, totalBytes, 0L, null)
                kotlinx.coroutines.delay(100)
                onProgress("Committing modifications...", 0.95f, uploadedCount, totalBytes, totalBytes, 0L, null)
                kotlinx.coroutines.delay(100)
                onProgress("Syncing remote configurations...", 0.98f, uploadedCount, totalBytes, totalBytes, 0L, null)
                kotlinx.coroutines.delay(100)
                onProgress("Successfully completed!", 1.0f, uploadedCount, totalBytes, totalBytes, 0L, 0L)
                return@withContext Result.success("MOCK_SUCCESS")
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        try {
            onProgress("Preparing local Git workspace...", 0.05f, 0, 0L, totalBytes, 0L, null)

            val workspaceDir = java.io.File(context.cacheDir, "jgit_workspace/$repo").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }

            // Copy files to local workspace folder
            var copiedBytes = 0L
            files.forEachIndexed { index, pFile ->
                val targetFile = java.io.File(workspaceDir, pFile.relativePath)
                targetFile.parentFile?.mkdirs()

                context.contentResolver.openInputStream(pFile.fileUri)?.use { input ->
                    java.io.FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                copiedBytes += pFile.size
                val prepProgress = 0.05f + ((index.toFloat() / files.size) * 0.20f)
                val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
                val speed = if (elapsedSec >= 0.2) (copiedBytes / elapsedSec).toLong() else 0L
                onProgress("Extracting project files (${index + 1}/${files.size})...", prepProgress, index + 1, copiedBytes, totalBytes, speed, null)
            }

            val token = com.msi.gittool.git.SecureTokenManager(context).getToken()
                ?: tokenManager.getAccessToken()
                ?: throw Exception("GitHub Token is missing. Please enter your personal access token.")

            val remoteUrl = "https://github.com/$owner/$repo.git"
            val gitManager = com.msi.gittool.git.GitManager()

            val username = com.msi.gittool.git.SecureTokenManager(context).getUsername() ?: owner

            val pushResult = gitManager.pushProject(
                projectDir = workspaceDir,
                remoteUrl = remoteUrl,
                token = token,
                branchName = "main",
                commitMessage = "Upload project files via GitTool JGit engine",
                authorName = username,
                authorEmail = "$username@users.noreply.github.com",
                onProgress = { stage, percent, detail ->
                    val floatProg = 0.25f + ((percent.toFloat() / 100f) * 0.70f)
                    val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
                    val speed = if (elapsedSec >= 0.2) (totalBytes / elapsedSec).toLong() else 0L
                    onProgress(
                        stage,
                        floatProg,
                        files.size,
                        (totalBytes * floatProg).toLong().coerceAtMost(totalBytes),
                        totalBytes,
                        speed,
                        null
                    )
                }
            )

            if (pushResult.isSuccess) {
                addRepoToLocalList(
                    username = owner,
                    repo = GitHubRepo(
                        id = System.currentTimeMillis(),
                        name = repo,
                        full_name = "$owner/$repo",
                        private = false,
                        html_url = "https://github.com/$owner/$repo",
                        description = "Uploaded via GitTool JGit",
                        stargazers_count = 0,
                        forks_count = 0,
                        language = "Kotlin",
                        clone_url = "https://github.com/$owner/$repo.git"
                    )
                )

                onProgress("Upload completed successfully!", 1.0f, files.size, totalBytes, totalBytes, 0L, 0L)
                return@withContext Result.success(pushResult.commitHash ?: "HEAD")
            } else {
                return@withContext Result.failure(Exception(pushResult.errorMessage ?: "Git push failed."))
            }

        } catch (e: Exception) {
            val userError = com.msi.gittool.git.GitManager().mapUserFriendlyError(e.message ?: e.toString())
            return@withContext Result.failure(Exception(userError))
        }
    }

    private fun readFileAsBase64(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            null
        }
    }
}
