package com.msi.gittool.ui.filebrowser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msi.gittool.data.remote.GitHubFileContentResponse
import com.msi.gittool.data.repository.RepoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.StandardCharsets

enum class MarkdownTabMode { RENDERED, RAW }

data class FileViewerUiState(
    val owner: String = "",
    val repo: String = "",
    val path: String = "",
    val ref: String? = null,
    val contentItem: GitHubFileContentResponse? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val editableContent: String = "",
    val originalContent: String = "",
    val tempCacheFilePath: String? = null,
    val fileCategory: FileCategory = FileCategory.CODE,
    val syntaxLanguage: String = "text",
    val fileSize: Long = 0L,
    val rawBytes: ByteArray? = null,
    val isEditMode: Boolean = false,
    val showInfoBottomSheet: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<Int> = emptyList(), // line indices with matches
    val currentSearchIndex: Int = -1,
    val wrapText: Boolean = false,
    val showLineNumbers: Boolean = true,
    val fontSizeSp: Float = 13f,
    val showLineJumpDialog: Boolean = false,
    val lineJumpTarget: String = "",
    val targetScrollLine: Int? = null,
    val showCommitDialog: Boolean = false,
    val commitMessageInput: String = "Update file content",
    val targetBranchInput: String = "main",
    val isCommitting: Boolean = false,
    val markdownTabMode: MarkdownTabMode = MarkdownTabMode.RENDERED,
    val csvTableData: ParsedCsvTable? = null,
    val zipEntries: List<ZipEntryInfo> = emptyList(),
    val hexDumpLines: List<HexDumpLine> = emptyList()
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

    val hasUnsavedChanges: Boolean
        get() = isEditMode && editableContent != originalContent

    val totalLines: Int
        get() = if (editableContent.isEmpty()) 0 else editableContent.lines().size
}

class FileViewerViewModel(
    private val repoRepository: RepoRepository,
    private val owner: String,
    private val repo: String,
    private val path: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FileViewerUiState(
            owner = owner,
            repo = repo,
            path = path,
            commitMessageInput = "Update ${path.substringAfterLast('/')}"
        )
    )
    val uiState: StateFlow<FileViewerUiState> = _uiState.asStateFlow()

    fun loadFile(context: Context, forceRefresh: Boolean = false) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repoRepository.getRepoFileContent(owner, repo, path, forceRefresh, context)
            result.onSuccess { contentResponse ->
                val downloadUrl = contentResponse.download_url
                    ?: "https://raw.githubusercontent.com/$owner/$repo/main/$path"

                val cacheResult = repoRepository.downloadFileToCache(
                    context = context,
                    owner = owner,
                    repo = repo,
                    path = path,
                    downloadUrl = downloadUrl,
                    forceRefresh = forceRefresh
                )

                cacheResult.onSuccess { cacheFile ->
                    val bytes = try {
                        cacheFile.readBytes()
                    } catch (e: Exception) {
                        ByteArray(0)
                    }

                    // Check if file has null bytes or is binary
                    val isBinaryBytes = bytes.take(1024).contains(0.toByte())
                    val category = FileTypeClassifier.classifyFile(path, forceBinary = isBinaryBytes)
                    val lang = FileTypeClassifier.getSyntaxLanguage(path)

                    var fileText = ""
                    var csvTable: ParsedCsvTable? = null
                    var zipList: List<ZipEntryInfo> = emptyList()
                    var hexList: List<HexDumpLine> = emptyList()

                    if (category == FileCategory.IMAGE || category == FileCategory.AUDIO ||
                        category == FileCategory.VIDEO || category == FileCategory.PDF_DOCUMENT ||
                        category == FileCategory.BINARY
                    ) {
                        if (category == FileCategory.BINARY || isBinaryBytes) {
                            hexList = FileTypeClassifier.generateHexDump(bytes)
                        }
                    } else if (category == FileCategory.ARCHIVE) {
                        zipList = FileTypeClassifier.readZipArchiveEntries(cacheFile)
                        hexList = FileTypeClassifier.generateHexDump(bytes)
                    } else {
                        fileText = try {
                            String(bytes, StandardCharsets.UTF_8)
                        } catch (e: Exception) {
                            FileViewerUiState(owner = owner, repo = repo, path = path, contentItem = contentResponse).decodedContent
                        }
                        if (category == FileCategory.TABULAR) {
                            val delim = if (path.lowercase().endsWith(".tsv")) '\t' else ','
                            csvTable = FileTypeClassifier.parseCsv(fileText, delimiter = delim)
                        }
                    }

                    _uiState.update {
                        it.copy(
                            contentItem = contentResponse,
                            isLoading = false,
                            editableContent = fileText,
                            originalContent = fileText,
                            tempCacheFilePath = cacheFile.absolutePath,
                            fileCategory = category,
                            syntaxLanguage = lang,
                            fileSize = contentResponse.size.takeIf { s -> s > 0 } ?: cacheFile.length(),
                            rawBytes = bytes,
                            csvTableData = csvTable,
                            zipEntries = zipList,
                            hexDumpLines = hexList
                        )
                    }
                }.onFailure { _ ->
                    val decoded = FileViewerUiState(owner = owner, repo = repo, path = path, contentItem = contentResponse).decodedContent
                    val category = FileTypeClassifier.classifyFile(path)
                    val lang = FileTypeClassifier.getSyntaxLanguage(path)

                    _uiState.update {
                        it.copy(
                            contentItem = contentResponse,
                            isLoading = false,
                            editableContent = decoded,
                            originalContent = decoded,
                            tempCacheFilePath = null,
                            fileCategory = category,
                            syntaxLanguage = lang,
                            fileSize = contentResponse.size,
                            rawBytes = decoded.toByteArray(StandardCharsets.UTF_8)
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Failed to read file contents from repository."
                    )
                }
            }
        }
    }

    fun updateCodeContent(newContent: String, context: Context) {
        val currentCache = _uiState.value.tempCacheFilePath
        if (currentCache != null) {
            try {
                File(currentCache).writeText(newContent, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _uiState.update {
            val updatedState = it.copy(editableContent = newContent)
            // Recompute search if search query is active
            if (it.searchQuery.isNotEmpty()) {
                val matches = computeSearchMatches(newContent, it.searchQuery)
                updatedState.copy(
                    searchMatches = matches,
                    currentSearchIndex = if (matches.isNotEmpty()) 0 else -1
                )
            } else {
                updatedState
            }
        }
    }

    fun toggleEditMode() {
        _uiState.update { it.copy(isEditMode = !it.isEditMode) }
    }

    fun toggleWrapText() {
        _uiState.update { it.copy(wrapText = !it.wrapText) }
    }

    fun toggleShowLineNumbers() {
        _uiState.update { it.copy(showLineNumbers = !it.showLineNumbers) }
    }

    fun increaseFontSize() {
        _uiState.update { it.copy(fontSizeSp = (it.fontSizeSp + 2f).coerceAtMost(28f)) }
    }

    fun decreaseFontSize() {
        _uiState.update { it.copy(fontSizeSp = (it.fontSizeSp - 2f).coerceAtLeast(9f)) }
    }

    fun setMarkdownTabMode(mode: MarkdownTabMode) {
        _uiState.update { it.copy(markdownTabMode = mode) }
    }

    fun toggleInfoBottomSheet(show: Boolean) {
        _uiState.update { it.copy(showInfoBottomSheet = show) }
    }

    fun toggleLineJumpDialog(show: Boolean) {
        _uiState.update { it.copy(showLineJumpDialog = show) }
    }

    fun setLineJumpTarget(target: String) {
        _uiState.update { it.copy(lineJumpTarget = target) }
    }

    fun jumpToLineNumber(lineNumStr: String) {
        val line = lineNumStr.trim().toIntOrNull()
        if (line != null && line > 0 && line <= _uiState.value.totalLines) {
            _uiState.update {
                it.copy(
                    showLineJumpDialog = false,
                    targetScrollLine = line - 1
                )
            }
        } else {
            _uiState.update { it.copy(showLineJumpDialog = false) }
        }
    }

    fun clearTargetScrollLine() {
        _uiState.update { it.copy(targetScrollLine = null) }
    }

    fun setSearchQuery(query: String) {
        if (query.isEmpty()) {
            _uiState.update { it.copy(searchQuery = "", searchMatches = emptyList(), currentSearchIndex = -1) }
            return
        }
        val matches = computeSearchMatches(_uiState.value.editableContent, query)
        _uiState.update {
            it.copy(
                searchQuery = query,
                searchMatches = matches,
                currentSearchIndex = if (matches.isNotEmpty()) 0 else -1
            )
        }
    }

    private fun computeSearchMatches(content: String, query: String): List<Int> {
        if (query.isEmpty()) return emptyList()
        val lines = content.lines()
        val matches = mutableListOf<Int>()
        lines.forEachIndexed { index, lineText ->
            if (lineText.contains(query, ignoreCase = true)) {
                matches.add(index)
            }
        }
        return matches
    }

    fun nextSearchMatch() {
        val state = _uiState.value
        if (state.searchMatches.isEmpty()) return
        val nextIdx = (state.currentSearchIndex + 1) % state.searchMatches.size
        _uiState.update {
            it.copy(
                currentSearchIndex = nextIdx,
                targetScrollLine = state.searchMatches[nextIdx]
            )
        }
    }

    fun previousSearchMatch() {
        val state = _uiState.value
        if (state.searchMatches.isEmpty()) return
        val prevIdx = if (state.currentSearchIndex - 1 < 0) state.searchMatches.size - 1 else state.currentSearchIndex - 1
        _uiState.update {
            it.copy(
                currentSearchIndex = prevIdx,
                targetScrollLine = state.searchMatches[prevIdx]
            )
        }
    }

    fun toggleCommitDialog(show: Boolean) {
        _uiState.update { it.copy(showCommitDialog = show) }
    }

    fun setCommitMessageInput(msg: String) {
        _uiState.update { it.copy(commitMessageInput = msg) }
    }

    fun setTargetBranchInput(branch: String) {
        _uiState.update { it.copy(targetBranchInput = branch) }
    }

    fun commitFileChanges(context: Context) {
        val state = _uiState.value
        if (state.editableContent == state.originalContent) {
            Toast.makeText(context, "No changes to commit.", Toast.LENGTH_SHORT).show()
            return
        }

        _uiState.update { it.copy(isCommitting = true) }
        viewModelScope.launch {
            val sha = state.contentItem?.sha
            val result = repoRepository.updateRepoFileContent(
                owner = owner,
                repo = repo,
                path = path,
                newContentText = state.editableContent,
                commitMessage = state.commitMessageInput.ifBlank { "Updated $path" },
                sha = sha,
                branch = state.targetBranchInput.ifBlank { "main" }
            )

            result.onSuccess { updatedResponse ->
                Toast.makeText(context, "Commit succeeded! File updated.", Toast.LENGTH_LONG).show()
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        showCommitDialog = false,
                        isEditMode = false,
                        originalContent = state.editableContent,
                        contentItem = updatedResponse
                    )
                }
            }.onFailure { error ->
                Toast.makeText(context, "Commit failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                _uiState.update { it.copy(isCommitting = false) }
            }
        }
    }

    fun openWithExternalApp(context: Context) {
        val cachePath = _uiState.value.tempCacheFilePath
        if (cachePath != null) {
            val file = File(cachePath)
            if (file.exists()) {
                val launched = FileTypeClassifier.openWithExternalApp(context, file)
                if (!launched) {
                    Toast.makeText(context, "No external app found to handle this file type.", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
        Toast.makeText(context, "File cache pending. Download or reload first.", Toast.LENGTH_SHORT).show()
    }

    fun copyContentToClipboard(context: Context) {
        val text = _uiState.value.editableContent
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("File Content", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "File content copied to clipboard!", Toast.LENGTH_SHORT).show()
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
                setDescription("Saving media file via system DownloadManager")
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
            Toast.makeText(context, "Initiated download for $filename. Check notification drawer.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFileLink(context: Context) {
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
        fun Factory(
            repoRepository: RepoRepository,
            owner: String,
            repo: String,
            path: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FileViewerViewModel(repoRepository, owner, repo, path) as T
            }
        }
    }
}
