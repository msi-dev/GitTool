package com.msi.gittool.ui.upload

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.2f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(java.util.Locale.US, "%.2f GB", gb)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadSheet(
    viewModel: UploadViewModel,
    onUploadSuccess: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val progressState by viewModel.uploadProgress.collectAsState()
    val scrollState = rememberScrollState()

    // Standalone SAF folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.onFolderSelected(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to resolve folder permissions", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Reacting to successful upload sequence
    LaunchedEffect(progressState) {
        if (progressState is UploadState.Success) {
            onUploadSuccess()
            onDismissRequest()
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )

    ModalBottomSheet(
        onDismissRequest = {
            // Dismiss only if we aren't currently deploying
            if (progressState !is UploadState.Uploading && progressState !is UploadState.Loading) {
                onDismissRequest()
            }
        },
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Upload Project",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Dynamic flow layout based on service state
            AnimatedContent(
                targetState = progressState,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "Form uploading status sheets"
            ) { targetState ->
                when (targetState) {
                    is UploadState.Loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = targetState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is UploadState.Uploading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { targetState.progress },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .testTag("upload_linear_progress")
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "${(targetState.progress * 100).toInt()}% Completed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${targetState.stage} (${targetState.uploadedCount}/${targetState.totalCount} files)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            
                            val speedStr = if (targetState.uploadSpeedBytesPerSec > 0) {
                                val mbSec = targetState.uploadSpeedBytesPerSec / (1024.0 * 1024.0)
                                if (mbSec >= 1.0) String.format(java.util.Locale.US, "%.1f MB/s", mbSec)
                                else String.format(java.util.Locale.US, "%.0f KB/s", targetState.uploadSpeedBytesPerSec / 1024.0)
                            } else null

                            val etaStr = if (targetState.estimatedRemainingSeconds != null && targetState.estimatedRemainingSeconds > 0) {
                                if (targetState.estimatedRemainingSeconds >= 60) "~${targetState.estimatedRemainingSeconds / 60}m remaining"
                                else "~${targetState.estimatedRemainingSeconds}s remaining"
                            } else null

                            val speedAndEta = listOfNotNull(speedStr, etaStr).joinToString(" • ")
                            if (speedAndEta.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = speedAndEta,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    is UploadState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Error notification info icon",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = targetState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.reset() },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Retry")
                            }
                        }
                    }

                    else -> {
                        // Regular Form (Idle/Unset)
                        Column {
                            // 1. Folder Selection Button
                            Button(
                                onClick = { folderPickerLauncher.launch(null) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.folderUri == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("select_folder_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "Open file tree folder picker")
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (uiState.folderUri == null) "Select Project Folder" else "Change Project Folder",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Display details of selection
                            if (uiState.folderUri != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Selected Path URI",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = uiState.folderUri.toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        Spacer(modifier = Modifier.height(12.dp))

                                        if (uiState.isScanning) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Scanning project files, subfolders & exact repo size...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        } else if (uiState.scannedFilesCount != null) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceAround,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Subfolders stat
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Default.Folder,
                                                            contentDescription = "Folders count",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "${uiState.scannedFoldersCount ?: 0}",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                    Text(
                                                        text = "Subfolders",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                                    )
                                                }

                                                // Files stat
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Default.Description,
                                                            contentDescription = "Files count",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "${uiState.scannedFilesCount ?: 0}",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                    Text(
                                                        text = "Files",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                                    )
                                                }

                                                // Exact Repo Size stat
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Default.SdStorage,
                                                            contentDescription = "Repo size",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = formatFileSize(uiState.scannedTotalSizeBytes),
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                    Text(
                                                        text = "Repo Size",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // 2. Project Name input
                            OutlinedTextField(
                                value = uiState.repoName,
                                onValueChange = { viewModel.onRepoNameChanged(it) },
                                label = { Text("Project Name") },
                                leadingIcon = { Icon(Icons.Default.Title, contentDescription = "Filename") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("upload_project_name_field")
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 3. Multiline Description
                            OutlinedTextField(
                                value = uiState.description,
                                onValueChange = { viewModel.onDescriptionChanged(it) },
                                label = { Text("Description (Optional)") },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = "File description details") },
                                shape = RoundedCornerShape(12.dp),
                                maxLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .testTag("upload_description_field")
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // 4. Visibility choice cards
                            Text(
                                text = "Repository Visibility",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (uiState.isPrivate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = { viewModel.onVisibilityChanged(true) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Lock",
                                            tint = if (uiState.isPrivate) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Private",
                                            fontWeight = FontWeight.Bold,
                                            color = if (uiState.isPrivate) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (!uiState.isPrivate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = { viewModel.onVisibilityChanged(false) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.Public,
                                            contentDescription = "Globe",
                                            tint = if (!uiState.isPrivate) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Public",
                                            fontWeight = FontWeight.Bold,
                                            color = if (!uiState.isPrivate) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // 5. Build trigger upload button
                            Button(
                                onClick = {
                                    if (uiState.folderUri == null) return@Button
                                    if (uiState.repoName.isBlank()) return@Button
                                    
                                    UploadService.startService(
                                        context = context,
                                        repoName = uiState.repoName,
                                        repoDescription = uiState.description,
                                        isPrivate = uiState.isPrivate,
                                        folderUri = uiState.folderUri.toString()
                                    )
                                },
                                enabled = uiState.folderUri != null && uiState.repoName.isNotBlank() && !uiState.isScanning,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("upload_project_trigger_button")
                            ) {
                                Text(
                                    text = "Upload to GitHub",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}
