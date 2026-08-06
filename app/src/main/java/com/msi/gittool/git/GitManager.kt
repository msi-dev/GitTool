package com.msi.gittool.git

import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.StoredConfig
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

data class ProjectPreCheckResult(
    val totalFiles: Int,
    val totalSizeBytes: Long,
    val largeFiles: List<File>,
    val availableDiskSpaceBytes: Long,
    val hasGitDirectory: Boolean,
    val warningMessage: String? = null
)

data class GitPushResult(
    val isSuccess: Boolean,
    val commitHash: String?,
    val totalFilesPushed: Int,
    val summaryMessage: String,
    val errorMessage: String? = null
)

class AccurateProgressMonitor(
    private val onProgressUpdate: (title: String, percent: Int, detail: String) -> Unit,
    private val isCancelledCheck: () -> Boolean = { false }
) : ProgressMonitor {

    private var totalTasks = 0
    private var completedTasks = 0
    private var currentTaskTotalWork = 0
    private var currentTaskCompletedWork = 0
    private var currentTaskName = "Initializing Git Operation"

    private val PRE_PUSH_PERCENTAGE = 40

    override fun start(totalTasks: Int) {
        this.totalTasks = totalTasks
        this.completedTasks = 0
        notifyProgress()
    }

    override fun beginTask(title: String?, totalWork: Int) {
        currentTaskName = title ?: "Processing"
        currentTaskTotalWork = totalWork
        currentTaskCompletedWork = 0
        notifyProgress()
    }

    override fun update(completed: Int) {
        currentTaskCompletedWork += completed
        notifyProgress()
    }

    override fun endTask() {
        completedTasks++
        currentTaskCompletedWork = 0
        notifyProgress()
    }

    override fun showDuration(enabled: Boolean) {
        // Optional duration toggle in JGit 6.7+
    }

    private fun notifyProgress() {
        val taskProgress = if (currentTaskTotalWork > 0) {
            (currentTaskCompletedWork.toFloat() / currentTaskTotalWork)
        } else 0f

        val overallProgress = if (totalTasks > 0) {
            val taskShare = 60f / totalTasks
            val completedTaskProgress = (completedTasks * taskShare)
            val currentTaskProgress = (taskProgress * taskShare)
            PRE_PUSH_PERCENTAGE + (completedTaskProgress + currentTaskProgress).toInt()
        } else {
            PRE_PUSH_PERCENTAGE + (taskProgress * 60).toInt()
        }

        val percent = overallProgress.coerceIn(0, 100)
        val detail = if (currentTaskTotalWork > 0) {
            "$currentTaskName ($currentTaskCompletedWork/$currentTaskTotalWork)"
        } else {
            currentTaskName
        }

        onProgressUpdate(currentTaskName, percent, detail)
    }

    override fun isCancelled(): Boolean = isCancelledCheck()
}

class GitManager {

    /**
     * Pre-check phase before running heavy Git operations:
     * - Validates directory exists & is readable
     * - Counts files & total size
     * - Checks for files > 100MB (GitHub limit)
     * - Checks available disk space
     */
    suspend fun runPreCheck(projectDir: File): ProjectPreCheckResult = withContext(Dispatchers.IO) {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            throw IllegalArgumentException("Project directory does not exist or is invalid: ${projectDir.absolutePath}")
        }

        val gitDir = File(projectDir, ".git")
        val hasGitDir = gitDir.exists() && gitDir.isDirectory

        var fileCount = 0
        var totalSize = 0L
        val largeFilesList = mutableListOf<File>()

        fun scanDir(dir: File) {
            val children = dir.listFiles() ?: return
            for (file in children) {
                if (file.name == ".git" || file.name == "node_modules" || file.name == "build" || file.name.startsWith(".")) {
                    continue
                }
                if (file.isDirectory) {
                    scanDir(file)
                } else if (file.isFile) {
                    fileCount++
                    val len = file.length()
                    totalSize += len
                    if (len > 100 * 1024 * 1024) { // >100MB
                        largeFilesList.add(file)
                    }
                }
            }
        }

        scanDir(projectDir)

        val stat = StatFs(projectDir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong

        var warning: String? = null
        if (largeFilesList.isNotEmpty()) {
            val names = largeFilesList.take(3).joinToString { it.name }
            warning = "Warning: ${largeFilesList.size} file(s) exceed GitHub's 100MB limit (e.g. $names). Consider Git LFS or excluding them."
        } else if (availableBytes < totalSize * 2) {
            warning = "Low disk space warning: Available space may be tight for packfile creation."
        }

        ProjectPreCheckResult(
            totalFiles = fileCount,
            totalSizeBytes = totalSize,
            largeFiles = largeFilesList,
            availableDiskSpaceBytes = availableBytes,
            hasGitDirectory = hasGitDir,
            warningMessage = warning
        )
    }

    /**
     * Main Git Push workflow following sequence:
     * 1. Pre-check
     * 2. Repository setup (open or init)
     * 3. Double add() staging (new, modified, deleted)
     * 4. Commit
     * 5. Remote configuration
     * 6. Push with AccurateProgressMonitor & cancellation support
     */
    suspend fun pushProject(
        projectDir: File,
        remoteUrl: String,
        token: String,
        branchName: String = "main",
        commitMessage: String = "Automated commit via GitTool",
        authorName: String = "GitTool User",
        authorEmail: String = "user@gittool.app",
        onProgress: (stage: String, percent: Int, detail: String) -> Unit,
        isCancelled: () -> Boolean = { false }
    ): GitPushResult = withContext(Dispatchers.IO) {
        var git: Git? = null
        try {
            onProgress("Pre-checking project files...", 5, "Analyzing project structure")
            if (isCancelled()) throw InterruptedException("Upload cancelled by user")

            val preCheck = runPreCheck(projectDir)
            if (preCheck.largeFiles.isNotEmpty()) {
                val names = preCheck.largeFiles.take(2).joinToString { it.name }
                throw IllegalArgumentException("File too large: $names exceeds GitHub's 100MB limit.")
            }

            // 2. Repository setup
            onProgress("Initializing Git repository...", 10, "Setting up local repository")
            if (isCancelled()) throw InterruptedException("Upload cancelled by user")

            val gitDir = File(projectDir, ".git")
            git = if (gitDir.exists()) {
                // DO NOT delete existing .git - enables resumable pushes
                Git.open(projectDir)
            } else {
                Git.init().setDirectory(projectDir).setInitialBranch(branchName).call()
            }

            val repository = git.repository
            configureJGitPerformance(repository)

            // Configure Author/Committer
            val config: StoredConfig = repository.config
            config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, authorName)
            config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, authorEmail)
            config.save()

            // 3. Double add() staging workaround (JGit pattern)
            onProgress("Staging project files...", 20, "Staging new files")
            if (isCancelled()) throw InterruptedException("Upload cancelled by user")

            // First add(): setUpdate(false) to stage new/untracked files
            git.add().addFilepattern(".").setUpdate(false).call()

            onProgress("Staging project files...", 30, "Staging modified and deleted files")
            if (isCancelled()) throw InterruptedException("Upload cancelled by user")

            // Second add(): setUpdate(true) to stage modified and deleted files
            git.add().addFilepattern(".").setUpdate(true).call()

            // 4. Commit phase
            onProgress("Creating Git commit...", 35, "Checking repository status")
            if (isCancelled()) throw InterruptedException("Upload cancelled by user")

            val status = git.status().call()
            var lastCommitHash: String? = null

            if (status.hasUncommittedChanges() || status.untracked.isNotEmpty() || repository.resolve("HEAD") == null) {
                val commit = git.commit()
                    .setMessage(commitMessage)
                    .setAuthor(authorName, authorEmail)
                    .setCommitter(authorName, authorEmail)
                    .call()
                lastCommitHash = commit.name
            } else {
                val head = repository.resolve("HEAD")
                lastCommitHash = head?.name
            }

            // 5. Remote configuration
            onProgress("Configuring remote repository...", 40, "Setting remote origin URL")
            if (isCancelled()) throw InterruptedException("Upload cancelled by user")

            val remotes = git.remoteList().call()
            val originRemote = remotes.find { it.name == "origin" }
            if (originRemote != null) {
                git.remoteSetUrl().setRemoteName("origin").setRemoteUri(org.eclipse.jgit.transport.URIish(remoteUrl)).call()
            } else {
                git.remoteAdd().setName("origin").setUri(org.eclipse.jgit.transport.URIish(remoteUrl)).call()
            }

            // 6. Push phase with AccurateProgressMonitor
            onProgress("Connecting to GitHub...", 42, "Authenticating with credentials")
            if (isCancelled()) throw InterruptedException("Upload cancelled by user")

            val credentialsProvider: CredentialsProvider = UsernamePasswordCredentialsProvider("oauth2", token)

            val monitor = AccurateProgressMonitor(
                onProgressUpdate = { title, percent, detail ->
                    onProgress(title, percent, detail)
                },
                isCancelledCheck = isCancelled
            )

            val refSpec = RefSpec("refs/heads/$branchName:refs/heads/$branchName")
            val pushResults = git.push()
                .setRemote("origin")
                .setRefSpecs(refSpec)
                .setCredentialsProvider(credentialsProvider)
                .setProgressMonitor(monitor)
                .call()

            var pushSuccessful = false
            var pushErrorMessage: String? = null

            pushResults.forEach { result ->
                result.getRemoteUpdates().forEach { refUpdate ->
                    when (refUpdate.getStatus()) {
                        RemoteRefUpdate.Status.OK, RemoteRefUpdate.Status.UP_TO_DATE -> {
                            pushSuccessful = true
                        }
                        RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD -> {
                            // Try fetch & rebase
                            onProgress("Rebasing remote changes...", 70, "Remote branch has commits. Fetching & rebasing...")
                            try {
                                git.fetch()
                                    .setRemote("origin")
                                    .setCredentialsProvider(credentialsProvider)
                                    .call()

                                git.rebase()
                                    .setUpstream("origin/$branchName")
                                    .call()

                                // Retry push after rebase
                                val retryResults = git.push()
                                    .setRemote("origin")
                                    .setRefSpecs(refSpec)
                                    .setCredentialsProvider(credentialsProvider)
                                    .setProgressMonitor(monitor)
                                    .call()

                                retryResults.forEach { rr ->
                                    rr.getRemoteUpdates().forEach { u ->
                                        if (u.getStatus() == RemoteRefUpdate.Status.OK || u.getStatus() == RemoteRefUpdate.Status.UP_TO_DATE) {
                                            pushSuccessful = true
                                        } else {
                                            pushErrorMessage = "Push rejected after rebase: ${u.getStatus()}"
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                pushErrorMessage = "Push rejected. The remote has changes that could not be automatically rebased: ${e.message}"
                            }
                        }
                        RemoteRefUpdate.Status.REJECTED_NODELETE -> pushErrorMessage = "Push rejected: Cannot delete remote ref."
                        RemoteRefUpdate.Status.REJECTED_OTHER_REASON -> pushErrorMessage = "Push rejected by GitHub: ${refUpdate.getMessage() ?: "Unknown reason"}"
                        else -> pushErrorMessage = "Push status: ${refUpdate.getStatus()} - ${refUpdate.getMessage() ?: ""}"
                    }
                }
            }

            if (pushSuccessful) {
                onProgress("Upload completed successfully!", 100, "Pushed ${preCheck.totalFiles} files")
                GitPushResult(
                    isSuccess = true,
                    commitHash = lastCommitHash,
                    totalFilesPushed = preCheck.totalFiles,
                    summaryMessage = "Successfully pushed ${preCheck.totalFiles} files to $remoteUrl ($branchName)"
                )
            } else {
                val error = pushErrorMessage ?: "Failed to push to remote repository"
                GitPushResult(
                    isSuccess = false,
                    commitHash = lastCommitHash,
                    totalFilesPushed = 0,
                    summaryMessage = "Push failed",
                    errorMessage = mapUserFriendlyError(error)
                )
            }

        } catch (e: InterruptedException) {
            GitPushResult(
                isSuccess = false,
                commitHash = null,
                totalFilesPushed = 0,
                summaryMessage = "Upload cancelled",
                errorMessage = "Upload was cancelled by user."
            )
        } catch (e: TransportException) {
            val userMsg = mapUserFriendlyError(e.message ?: e.toString())
            GitPushResult(
                isSuccess = false,
                commitHash = null,
                totalFilesPushed = 0,
                summaryMessage = "Network/Auth Error",
                errorMessage = userMsg
            )
        } catch (e: Exception) {
            val userMsg = mapUserFriendlyError(e.message ?: e.toString())
            GitPushResult(
                isSuccess = false,
                commitHash = null,
                totalFilesPushed = 0,
                summaryMessage = "Push Error",
                errorMessage = userMsg
            )
        } finally {
            git?.close()
        }
    }

    /**
     * Optimizes JGit configuration parameters for handling repositories with 1000+ files
     */
    private fun configureJGitPerformance(repository: Repository) {
        val config = repository.config
        config.setInt(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_COMPRESSION, 6)
        config.setInt(ConfigConstants.CONFIG_PACK_SECTION, null, ConfigConstants.CONFIG_KEY_WINDOW, 10)
        config.setLong(ConfigConstants.CONFIG_PACK_SECTION, null, ConfigConstants.CONFIG_KEY_DELTA_CACHE_SIZE, 5 * 1024 * 1024L) // 5MB
        config.setInt(ConfigConstants.CONFIG_PACK_SECTION, null, ConfigConstants.CONFIG_KEY_THREADS, 4)
        config.save()
    }

    /**
     * User-friendly error mapping to turn technical stack traces into friendly messages
     */
    fun mapUserFriendlyError(rawError: String): String {
        val lower = rawError.lowercase()
        return when {
            lower.contains("not authorized") || lower.contains("401") || lower.contains("authentication failed") || lower.contains("invalid credentials") ->
                "Authentication failed. Please check your GitHub token and scope permissions."

            lower.contains("403") || lower.contains("rate limit") ->
                "GitHub rate limit or permission restricted (403). Please verify your token has 'repo' permissions."

            lower.contains("404") || lower.contains("repository not found") ->
                "Repository not found on GitHub. Please check the repository name and owner."

            lower.contains("non-fast-forward") || lower.contains("rejected_nonfastforward") ->
                "Push rejected. The remote repository has existing commits that differ. Pull first or rebase."

            lower.contains("exceeds github's 100mb limit") || lower.contains("file too large") ->
                rawError

            lower.contains("disk space") || lower.contains("no space left") ->
                "Disk space insufficient on device to package repository."

            lower.contains("unknownhost") || lower.contains("timeout") || lower.contains("network") || lower.contains("connection refused") ->
                "Network error. Check your internet connection and try again."

            lower.contains("cancelled") ->
                "Upload was cancelled."

            else -> rawError
        }
    }
}
