package com.msi.gittool.ui.upload

sealed interface UploadState {
    data object Idle : UploadState
    data class Loading(val message: String) : UploadState
    data class Uploading(
        val stage: String,
        val progress: Float,
        val uploadedCount: Int,
        val totalCount: Int,
        val uploadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val uploadSpeedBytesPerSec: Long = 0L,
        val estimatedRemainingSeconds: Long? = null
    ) : UploadState
    data class Success(val repoUrl: String) : UploadState
    data class Error(val message: String) : UploadState
}
