package com.msi.gittool.ui.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msi.gittool.data.repository.UploadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UploadUiState(
    val folderUri: Uri? = null,
    val repoName: String = "",
    val description: String = "",
    val isPrivate: Boolean = true,
    val scannedFilesCount: Int? = null,
    val scannedFoldersCount: Int? = null,
    val scannedTotalSizeBytes: Long = 0L,
    val isScanning: Boolean = false,
    val isFilePickerActive: Boolean = false
)

class UploadViewModel(
    private val uploadRepository: UploadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    val uploadProgress: StateFlow<UploadState> = UploadService.uploadProgress

    fun onFolderSelected(uri: Uri) {
        _uiState.update { 
            it.copy(
                folderUri = uri, 
                isScanning = true, 
                scannedFilesCount = null,
                scannedFoldersCount = null,
                scannedTotalSizeBytes = 0L
            ) 
        }
        
        val segment = uri.lastPathSegment ?: "my-repository"
        val inferredName = segment.substringAfterLast('/').substringAfterLast(':')
            .lowercase()
            .replace("\\s+".toRegex(), "-")
            .replace("[^a-z0-9\\-_]".toRegex(), "")
            .trim()
            
        _uiState.update { it.copy(repoName = if (inferredName.isEmpty()) "my-repository" else inferredName) }

        viewModelScope.launch {
            val scanResult = uploadRepository.scanProjectFolderDetailed(uri)
            _uiState.update { 
                it.copy(
                    scannedFilesCount = scanResult.fileCount,
                    scannedFoldersCount = scanResult.folderCount,
                    scannedTotalSizeBytes = scanResult.totalSizeBytes,
                    isScanning = false
                ) 
            }
        }
    }

    fun onRepoNameChanged(name: String) {
        _uiState.update { it.copy(repoName = name) }
    }

    fun onDescriptionChanged(desc: String) {
        _uiState.update { it.copy(description = desc) }
    }

    fun onVisibilityChanged(isPrivate: Boolean) {
        _uiState.update { it.copy(isPrivate = isPrivate) }
    }

    fun reset() {
        _uiState.value = UploadUiState()
        UploadService.clearState()
    }

    companion object {
        fun Factory(uploadRepository: UploadRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UploadViewModel(uploadRepository) as T
            }
        }
    }
}
