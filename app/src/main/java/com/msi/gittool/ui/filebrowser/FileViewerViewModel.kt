package com.msi.gittool.ui.filebrowser

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msi.gittool.data.repository.RepoRepository
import com.msi.gittool.data.remote.GitHubFileContentResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

data class FileViewerUiState(
    val owner: String = "",
    val repo: String = "",
    val path: String = "",
    val contentItem: GitHubFileContentResponse? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val editableContent: String = "",
    val tempCacheFilePath: String? = null
) {
    val decodedContent: String by lazy {
        val raw = contentItem?.content ?: return@lazy ""
        val clean = raw.replace("\\s".toRegex(), "")
        try {
            val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
            String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            try {
                val bytes = android.util.Base64.decode(clean, android.util.Base64.URL_SAFE)
                String(bytes, StandardCharsets.UTF_8)
            } catch (e2: Exception) {
                raw
            }
        }
    }

    val isImage: Boolean
        get() {
            val p = path.lowercase()
            return p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg") ||
                    p.endsWith(".gif") || p.endsWith(".webp") || p.endsWith(".bmp") ||
                    p.endsWith(".svg")
        }

    val isVideo: Boolean
        get() {
            val p = path.lowercase()
            return p.endsWith(".mp4") || p.endsWith(".mkv") || p.endsWith(".3gp") ||
                    p.endsWith(".webm") || p.endsWith(".avi") || p.endsWith(".mov")
        }

    val isAudio: Boolean
        get() {
            val p = path.lowercase()
            return p.endsWith(".mp3") || p.endsWith(".wav") || p.endsWith(".ogg") ||
                    p.endsWith(".m4a") || p.endsWith(".aac") || p.endsWith(".flac")
        }
}

class FileViewerViewModel(
    private val repoRepository: RepoRepository,
    private val owner: String,
    private val repo: String,
    private val path: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileViewerUiState(owner = owner, repo = repo, path = path))
    val uiState: StateFlow<FileViewerUiState> = _uiState.asStateFlow()

    fun loadFile(context: Context, forceRefresh: Boolean = false) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repoRepository.getRepoFileContent(owner, repo, path, forceRefresh, context)
            result.onSuccess { contentResponse ->
                val downloadUrl = contentResponse.download_url ?: "https://raw.githubusercontent.com/$owner/$repo/main/$path"
                
                // Let's download the raw file to the internal cache directory
                val cacheResult = repoRepository.downloadFileToCache(
                    context = context,
                    owner = owner,
                    repo = repo,
                    path = path,
                    downloadUrl = downloadUrl,
                    forceRefresh = forceRefresh
                )

                cacheResult.onSuccess { cacheFile ->
                    val fileText = try {
                        cacheFile.readText(java.nio.charset.StandardCharsets.UTF_8)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        ""
                    }

                    _uiState.update { 
                        it.copy(
                            contentItem = contentResponse, 
                            isLoading = false,
                            editableContent = fileText,
                            tempCacheFilePath = cacheFile.absolutePath
                        ) 
                    }
                }.onFailure { cacheError ->
                    // Fallback to decode base64 field from response if download fails
                    val decoded = FileViewerUiState(owner, repo, path, contentResponse).decodedContent
                    _uiState.update { 
                        it.copy(
                            contentItem = contentResponse, 
                            isLoading = false,
                            editableContent = decoded,
                            tempCacheFilePath = null
                        ) 
                    }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = error.localizedMessage ?: "Failed to read file contents."
                ) }
            }
        }
    }

    fun updateCodeContent(newContent: String, context: Context) {
        val currentPath = _uiState.value.tempCacheFilePath
        if (currentPath != null) {
            try {
                val file = java.io.File(currentPath)
                file.writeText(newContent, java.nio.charset.StandardCharsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _uiState.update { it.copy(editableContent = newContent) }
    }

    fun downloadFile(context: Context) {
        val state = _uiState.value
        val url = state.contentItem?.download_url ?: return
        val filename = path.substringAfterLast("/")
        viewModelScope.launch {
            repoRepository.downloadSingleFile(context, filename, url)
        }
    }

    fun downloadImageUsingDownloadManager(context: Context) {
        val state = _uiState.value
        val url = state.contentItem?.download_url ?: return
        val filename = path.substringAfterLast("/")
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val uri = android.net.Uri.parse(url)
            val request = android.app.DownloadManager.Request(uri).apply {
                setTitle("Downloading $filename")
                setDescription("Saving image via local system DownloadManager")
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
                val token = repoRepository.getAccessToken()
                if (!token.isNullOrEmpty()) {
                    val authHeader = if (token.startsWith("ghp_") || token.startsWith("gho_")) {
                        "token $token"
                    } else {
                        "Bearer $token"
                    }
                    addRequestHeader("Authorization", authHeader)
                }
            }
            downloadManager.enqueue(request)
            android.widget.Toast.makeText(context, "Initiated image download via DownloadManager. Check notification drawer.", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Download failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFileContent(context: Context) {
        val state = _uiState.value
        val shareText = "Check out this file on GitHub:\n" +
                (state.contentItem?.html_url ?: "https://github.com/${owner}/${repo}/blob/main/${path}")
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, path)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Share File Link"))
    }

    companion object {
        fun Factory(repoRepository: RepoRepository, owner: String, repo: String, path: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FileViewerViewModel(repoRepository, owner, repo, path) as T
            }
        }
    }
}
