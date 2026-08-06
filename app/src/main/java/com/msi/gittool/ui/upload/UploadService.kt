package com.msi.gittool.ui.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.msi.gittool.GitToolApplication
import com.msi.gittool.MainActivity
import com.msi.gittool.data.repository.ProjectFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UploadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "gittool_upload_service"
        private const val NOTIFICATION_ID = 4829
        
        private val _uploadProgress = MutableStateFlow<UploadState>(UploadState.Idle)
        val uploadProgress: StateFlow<UploadState> = _uploadProgress.asStateFlow()

        fun startService(
            context: Context,
            repoName: String,
            repoDescription: String?,
            isPrivate: Boolean,
            folderUri: String
        ) {
            val intent = Intent(context, UploadService::class.java).apply {
                putExtra("repoName", repoName)
                putExtra("description", repoDescription)
                putExtra("isPrivate", isPrivate)
                putExtra("folderUri", folderUri)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun clearState() {
            _uploadProgress.value = UploadState.Idle
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repoName = intent?.getStringExtra("repoName") ?: ""
        val description = intent?.getStringExtra("description")
        val isPrivate = intent?.getBooleanExtra("isPrivate", true) ?: true
        val folderUriString = intent?.getStringExtra("folderUri") ?: ""

        if (repoName.isEmpty() || folderUriString.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val folderUri = Uri.parse(folderUriString)
        val container = (applicationContext as GitToolApplication).container
        val uploadRepo = container.uploadRepository

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GitTool - Starting Project Upload")
            .setContentText("Scanning project directory...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(getPendingIntent())

        startForeground(NOTIFICATION_ID, builder.build())

        serviceScope.launch {
            try {
                _uploadProgress.value = UploadState.Loading("Scanning folder directories & calculating size...")
                val scanResult = uploadRepo.scanProjectFolderDetailed(folderUri)
                val files = scanResult.files
                
                if (files.isEmpty()) {
                    _uploadProgress.value = UploadState.Error("No files found or all files exceed 100MB.")
                    updateFailureNotification(repoName, "Zero valid project files discovered.")
                    stopSelf()
                    return@launch
                }

                val formattedSize = if (scanResult.totalSizeBytes > 0) {
                    val mb = scanResult.totalSizeBytes / (1024.0 * 1024.0)
                    if (mb >= 1.0) String.format(java.util.Locale.US, "%.2f MB", mb)
                    else String.format(java.util.Locale.US, "%.2f KB", scanResult.totalSizeBytes / 1024.0)
                } else "0 B"

                _uploadProgress.value = UploadState.Loading("Creating repository '$repoName' (${files.size} files, ${scanResult.folderCount} subfolders, $formattedSize)...")
                val repoResult = uploadRepo.createRepository(repoName, description, isPrivate)
                
                repoResult.onSuccess { createdRepo ->
                    val owner = createdRepo.full_name.split("/")[0]
                    
                    val uploadResult = uploadRepo.uploadProject(
                        owner,
                        repoName,
                        files
                    ) { stage, progress, count, uploadedBytes, totalBytes, speed, etaSeconds ->
                        val percent = (progress * 100).toInt()
                        
                        _uploadProgress.value = UploadState.Uploading(
                            stage = stage,
                            progress = progress,
                            uploadedCount = count,
                            totalCount = files.size,
                            uploadedBytes = uploadedBytes,
                            totalBytes = totalBytes,
                            uploadSpeedBytesPerSec = speed,
                            estimatedRemainingSeconds = etaSeconds
                        )

                        val speedText = if (speed > 0) {
                            val mb = speed / (1024.0 * 1024.0)
                            if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f MB/s", mb)
                            else String.format(java.util.Locale.US, "%.0f KB/s", speed / 1024.0)
                        } else ""

                        val etaText = if (etaSeconds != null && etaSeconds > 0) {
                            if (etaSeconds >= 60) "${etaSeconds / 60}m ${etaSeconds % 60}s remaining"
                            else "${etaSeconds}s remaining"
                        } else ""

                        val detailText = listOf(
                            "$count/${files.size} files",
                            speedText,
                            etaText
                        ).filter { it.isNotEmpty() }.joinToString(" • ")

                        val updatedBuilder = NotificationCompat.Builder(this@UploadService, CHANNEL_ID)
                            .setContentTitle("Uploading '$repoName' to GitHub ($percent%)")
                            .setContentText(detailText)
                            .setSmallIcon(android.R.drawable.stat_sys_upload)
                            .setProgress(100, percent, false)
                            .setOngoing(true)
                            .setOnlyAlertOnce(true)
                            .setContentIntent(getPendingIntent())
                            
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(NOTIFICATION_ID, updatedBuilder.build())
                    }

                    uploadResult.onSuccess { url ->
                        _uploadProgress.value = UploadState.Success(url)
                        updateSuccessNotification(repoName, url)
                    }.onFailure { error ->
                        val msg = error.message ?: "Upload aborted"
                        _uploadProgress.value = UploadState.Error(msg)
                        updateFailureNotification(repoName, msg)
                    }
                }.onFailure { error ->
                    val msg = error.message ?: "Failed to assemble repo"
                    _uploadProgress.value = UploadState.Error(msg)
                    updateFailureNotification(repoName, msg)
                }
            } catch (e: Exception) {
                _uploadProgress.value = UploadState.Error(e.message ?: "An unhandled error occurred")
                updateFailureNotification(repoName, e.message ?: "Unhandled system error")
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun updateSuccessNotification(repoName: String, url: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Upload Succeeded!")
            .setContentText("Project '$repoName' successfully pushed to GitHub.")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())
        manager.notify(NOTIFICATION_ID + 1, builder.build())
    }

    private fun updateFailureNotification(repoName: String, reason: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Upload Failed")
            .setContentText("Could not push '$repoName' - $reason")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())
        manager.notify(NOTIFICATION_ID + 2, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Project Upload Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps tracks of active project pushes onto GitHub repositories"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
