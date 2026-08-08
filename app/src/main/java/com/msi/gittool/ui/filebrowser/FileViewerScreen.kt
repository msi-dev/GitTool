package com.msi.gittool.ui.filebrowser

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.msi.gittool.data.repository.RepoRepository

@Composable
fun FileViewerScreen(
    owner: String,
    repo: String,
    path: String,
    repoRepository: RepoRepository,
    onBack: () -> Unit
) {
    val viewModel: FileViewerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = FileViewerViewModel.Factory(repoRepository, owner, repo, path)
    )
    FileViewerScreen(viewModel = viewModel, onBackClick = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    viewModel: FileViewerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showSearchField by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadFile(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.path.substringAfterLast("/"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = uiState.path.substringAfterLast('.', "").uppercase().ifEmpty { "TXT" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${uiState.owner}/${uiState.repo} • ${uiState.path.substringBeforeLast('/', "root")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.fileCategory == FileCategory.CODE || uiState.fileCategory == FileCategory.MARKDOWN || uiState.fileCategory == FileCategory.TABULAR) {
                        IconButton(onClick = { showSearchField = !showSearchField }) {
                            Icon(Icons.Default.Search, contentDescription = "Search in file")
                        }
                        IconButton(onClick = { viewModel.toggleEditMode() }) {
                            Icon(
                                imageVector = if (uiState.isEditMode) Icons.Default.Visibility else Icons.Default.Edit,
                                contentDescription = if (uiState.isEditMode) "Preview Mode" else "Edit Mode",
                                tint = if (uiState.isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.toggleInfoBottomSheet(true) }) {
                        Icon(Icons.Default.Info, contentDescription = "File Details")
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open in External App") },
                                onClick = {
                                    showMenu = false
                                    viewModel.openWithExternalApp(context)
                                },
                                leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy File Link") },
                                onClick = {
                                    showMenu = false
                                    viewModel.shareFileLink(context)
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy File Contents") },
                                onClick = {
                                    showMenu = false
                                    viewModel.copyContentToClipboard(context)
                                },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Download to Device") },
                                onClick = {
                                    showMenu = false
                                    viewModel.downloadImageUsingDownloadManager(context)
                                },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (uiState.wrapText) "Disable Word Wrap" else "Enable Word Wrap") },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleWrapText()
                                },
                                leadingIcon = { Icon(Icons.Default.WrapText, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Go to Line Number...") },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleLineJumpDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.FormatLineSpacing, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Text Size +") },
                                onClick = {
                                    showMenu = false
                                    viewModel.increaseFontSize()
                                },
                                leadingIcon = { Icon(Icons.Default.ZoomIn, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Text Size -") },
                                onClick = {
                                    showMenu = false
                                    viewModel.decreaseFontSize()
                                },
                                leadingIcon = { Icon(Icons.Default.ZoomOut, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (uiState.hasUnsavedChanges) {
                ExtendedFloatingActionButton(
                    text = { Text("Commit Changes") },
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    onClick = { viewModel.toggleCommitDialog(true) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search Bar Bar
                AnimatedVisibility(
                    visible = showSearchField,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SearchBarView(
                        query = uiState.searchQuery,
                        matchCount = uiState.searchMatches.size,
                        currentIndex = uiState.currentSearchIndex,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        onNext = { viewModel.nextSearchMatch() },
                        onPrev = { viewModel.previousSearchMatch() },
                        onClose = {
                            showSearchField = false
                            viewModel.setSearchQuery("")
                        }
                    )
                }

                // Edit mode warning banner
                if (uiState.isEditMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.EditNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.hasUnsavedChanges) "Unsaved changes in editor" else "Editing file • Tap commit when ready",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            if (uiState.hasUnsavedChanges) {
                                Button(
                                    onClick = { viewModel.toggleCommitDialog(true) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Commit", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // Main Content View based on State
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Fetching & caching repository file...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (uiState.errorMessage != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Unable to load file",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uiState.errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadFile(context, forceRefresh = true) }) {
                                Text("Retry Reload")
                            }
                        }
                    }
                } else {
                    // Category Router
                    when (uiState.fileCategory) {
                        FileCategory.IMAGE -> ImageViewerSection(uiState = uiState, viewModel = viewModel, context = context)
                        FileCategory.AUDIO -> MediaViewerSection(uiState = uiState, isVideo = false, viewModel = viewModel, context = context)
                        FileCategory.VIDEO -> MediaViewerSection(uiState = uiState, isVideo = true, viewModel = viewModel, context = context)
                        FileCategory.PDF_DOCUMENT -> DocumentViewerSection(uiState = uiState, viewModel = viewModel, context = context)
                        FileCategory.MARKDOWN -> MarkdownViewerSection(uiState = uiState, viewModel = viewModel, context = context)
                        FileCategory.TABULAR -> TabularCsvViewerSection(uiState = uiState, viewModel = viewModel, context = context)
                        FileCategory.ARCHIVE -> ArchiveViewerSection(uiState = uiState, viewModel = viewModel, context = context)
                        FileCategory.BINARY -> BinaryHexViewerSection(uiState = uiState, viewModel = viewModel, context = context)
                        FileCategory.CODE -> CodeTextViewerSection(uiState = uiState, viewModel = viewModel, context = context)
                    }
                }
            }
        }
    }

    // Info Bottom Sheet
    if (uiState.showInfoBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleInfoBottomSheet(false) }
        ) {
            FileInfoSheetContent(uiState = uiState, onOpenExternal = {
                viewModel.toggleInfoBottomSheet(false)
                viewModel.openWithExternalApp(context)
            }, onDownload = {
                viewModel.toggleInfoBottomSheet(false)
                viewModel.downloadImageUsingDownloadManager(context)
            })
        }
    }

    // Line Jump Dialog
    if (uiState.showLineJumpDialog) {
        var lineInput by remember { mutableStateOf(uiState.lineJumpTarget) }
        AlertDialog(
            onDismissRequest = { viewModel.toggleLineJumpDialog(false) },
            title = { Text("Jump to Line", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter line number between 1 and ${uiState.totalLines}:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lineInput,
                        onValueChange = { lineInput = it },
                        label = { Text("Line Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.jumpToLineNumber(lineInput) }) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleLineJumpDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Commit Dialog
    if (uiState.showCommitDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleCommitDialog(false) },
            title = { Text("Commit File Changes", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Repository: ${uiState.owner}/${uiState.repo}\nFile: ${uiState.path}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.commitMessageInput,
                        onValueChange = { viewModel.setCommitMessageInput(it) },
                        label = { Text("Commit Message") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.targetBranchInput,
                        onValueChange = { viewModel.setTargetBranchInput(it) },
                        label = { Text("Target Branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.commitFileChanges(context) },
                    enabled = !uiState.isCommitting
                ) {
                    if (uiState.isCommitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Commit & Push")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleCommitDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SearchBarView(
    query: String,
    matchCount: Int,
    currentIndex: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Find in file...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (matchCount > 0) "${currentIndex + 1}/$matchCount" else "0/0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onPrev, enabled = matchCount > 0) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
            }
            IconButton(onClick = onNext, enabled = matchCount > 0) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close search")
            }
        }
    }
}

@Composable
fun CodeTextViewerSection(
    uiState: FileViewerUiState,
    viewModel: FileViewerViewModel,
    context: Context
) {
    val listState = rememberLazyListState()

    // Handle smooth scrolling when targetScrollLine is requested
    LaunchedEffect(uiState.targetScrollLine) {
        val target = uiState.targetScrollLine
        if (target != null) {
            listState.animateScrollToItem(target)
            viewModel.clearTargetScrollLine()
        }
    }

    if (uiState.isEditMode) {
        // Editable Code View
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = uiState.editableContent,
                onValueChange = { viewModel.updateCodeContent(it, context) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = uiState.fontSizeSp.sp
                )
            )
        }
    } else {
        // Read-only formatted line view
        val lines = remember(uiState.editableContent) { uiState.editableContent.lines() }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(lines) { index, lineText ->
                    val isMatched = uiState.searchMatches.contains(index)
                    val isCurrentMatch = uiState.currentSearchIndex >= 0 &&
                            uiState.searchMatches.getOrNull(uiState.currentSearchIndex) == index

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                when {
                                    isCurrentMatch -> MaterialTheme.colorScheme.primaryContainer
                                    isMatched -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    index % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    else -> Color.Transparent
                                }
                            )
                            .padding(vertical = 2.dp)
                    ) {
                        // Line Number Gutter
                        Text(
                            text = "${index + 1}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = (uiState.fontSizeSp * 0.85f).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .width(44.dp)
                                .padding(end = 8.dp)
                        )

                        // Line Code Text
                        if (uiState.wrapText) {
                            Text(
                                text = lineText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = uiState.fontSizeSp.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = lineText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = uiState.fontSizeSp.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownViewerSection(
    uiState: FileViewerUiState,
    viewModel: FileViewerViewModel,
    context: Context
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = if (uiState.markdownTabMode == MarkdownTabMode.RENDERED) 0 else 1
        ) {
            Tab(
                selected = uiState.markdownTabMode == MarkdownTabMode.RENDERED,
                onClick = { viewModel.setMarkdownTabMode(MarkdownTabMode.RENDERED) },
                text = { Text("Rendered Preview") },
                icon = { Icon(Icons.Default.Article, contentDescription = null) }
            )
            Tab(
                selected = uiState.markdownTabMode == MarkdownTabMode.RAW,
                onClick = { viewModel.setMarkdownTabMode(MarkdownTabMode.RAW) },
                text = { Text("Raw Source") },
                icon = { Icon(Icons.Default.Code, contentDescription = null) }
            )
        }

        if (uiState.markdownTabMode == MarkdownTabMode.RENDERED && !uiState.isEditMode) {
            // Rendered Markdown Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                val lines = uiState.editableContent.lines()
                lines.forEach { line ->
                    RenderedMarkdownLine(line = line)
                }
            }
        } else {
            CodeTextViewerSection(uiState = uiState, viewModel = viewModel, context = context)
        }
    }
}

@Composable
fun RenderedMarkdownLine(line: String) {
    val trimmed = line.trim()
    when {
        trimmed.startsWith("# ") -> {
            Text(
                text = trimmed.removePrefix("# "),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        trimmed.startsWith("## ") -> {
            Text(
                text = trimmed.removePrefix("## "),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        trimmed.startsWith("### ") -> {
            Text(
                text = trimmed.removePrefix("### "),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        trimmed.startsWith("> ") -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(modifier = Modifier.padding(8.dp)) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = trimmed.removePrefix("> "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        trimmed.startsWith("- [x]") || trimmed.startsWith("* [x]") -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Icon(Icons.Default.CheckBox, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = trimmed.drop(5).trim(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        trimmed.startsWith("- [ ]") || trimmed.startsWith("* [ ]") -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Icon(Icons.Default.CheckBoxOutlineBlank, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = trimmed.drop(5).trim(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
            Row(modifier = Modifier.padding(vertical = 2.dp, horizontal = 8.dp)) {
                Text("• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(text = trimmed.drop(2), style = MaterialTheme.typography.bodyMedium)
            }
        }
        trimmed.startsWith("```") -> {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
        trimmed.isEmpty() -> {
            Spacer(modifier = Modifier.height(8.dp))
        }
        else -> {
            Text(
                text = trimmed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
fun ImageViewerSection(
    uiState: FileViewerUiState,
    viewModel: FileViewerViewModel,
    context: Context
) {
    val downloadUrl = uiState.contentItem?.download_url
        ?: "https://raw.githubusercontent.com/${uiState.owner}/${uiState.repo}/main/${uiState.path}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = downloadUrl,
                    contentDescription = "Image Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = uiState.path.substringAfterLast('/'),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Size: ${formatFileSize(uiState.fileSize)} • Format: ${uiState.path.substringAfterLast('.', "").uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.downloadImageUsingDownloadManager(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download")
                    }
                    OutlinedButton(
                        onClick = { viewModel.openWithExternalApp(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("External")
                    }
                }
            }
        }
    }
}

@Composable
fun MediaViewerSection(
    uiState: FileViewerUiState,
    isVideo: Boolean,
    viewModel: FileViewerViewModel,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isVideo) Icons.Default.VideoFile else Icons.Default.AudioFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = uiState.path.substringAfterLast('/'),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isVideo) "Video Media Stream" else "Audio Media Stream",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Size: ${formatFileSize(uiState.fileSize)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Stream or play this media using any installed system player application.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.openWithExternalApp(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open with External Player")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.downloadImageUsingDownloadManager(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download Media File")
                }
            }
        }
    }
}

@Composable
fun DocumentViewerSection(
    uiState: FileViewerUiState,
    viewModel: FileViewerViewModel,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = uiState.path.substringAfterLast('/'),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "PDF & Office Document • ${formatFileSize(uiState.fileSize)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = { viewModel.openWithExternalApp(context) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Document with External App")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { viewModel.downloadImageUsingDownloadManager(context) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download Document")
        }
    }
}

@Composable
fun TabularCsvViewerSection(
    uiState: FileViewerUiState,
    viewModel: FileViewerViewModel,
    context: Context
) {
    var showRawView by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (showRawView) 1 else 0) {
            Tab(
                selected = !showRawView,
                onClick = { showRawView = false },
                text = { Text("Data Table View") },
                icon = { Icon(Icons.Default.TableChart, contentDescription = null) }
            )
            Tab(
                selected = showRawView,
                onClick = { showRawView = true },
                text = { Text("Raw CSV/TSV") },
                icon = { Icon(Icons.Default.Code, contentDescription = null) }
            )
        }

        if (showRawView || uiState.isEditMode) {
            CodeTextViewerSection(uiState = uiState, viewModel = viewModel, context = context)
        } else {
            val tableData = uiState.csvTableData
            if (tableData == null || tableData.headers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data or empty table.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Table Summary: ${tableData.totalRows} Rows • ${tableData.totalCols} Columns",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            // Header Row
                            item {
                                Row(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    tableData.headers.forEach { header ->
                                        Text(
                                            text = header,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier
                                                .width(140.dp)
                                                .padding(8.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Rows
                            itemsIndexed(tableData.rows) { rowIndex, row ->
                                Row(
                                    modifier = Modifier
                                        .background(
                                            if (rowIndex % 2 == 1) MaterialTheme.colorScheme.surfaceContainer
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    for (colIdx in 0 until tableData.totalCols) {
                                        val cellVal = row.getOrNull(colIdx) ?: ""
                                        Text(
                                            text = cellVal,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier
                                                .width(140.dp)
                                                .padding(8.dp),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveViewerSection(
    uiState: FileViewerUiState,
    viewModel: FileViewerViewModel,
    context: Context
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    Icons.Default.FolderZip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Archive Contents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "${uiState.zipEntries.size} Files/Folders • ${formatFileSize(uiState.fileSize)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
                Button(
                    onClick = { viewModel.openWithExternalApp(context) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Extract App", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (uiState.zipEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Archive viewer ready. Use 'Open in External App' to extract completely.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.zipEntries) { entry ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = entry.name,
                                fontWeight = if (entry.isDirectory) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        supportingContent = {
                            if (!entry.isDirectory) {
                                Text(
                                    text = "Size: ${formatFileSize(entry.size)} • Compressed: ${formatFileSize(entry.compressedSize)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
fun BinaryHexViewerSection(
    uiState: FileViewerUiState,
    viewModel: FileViewerViewModel,
    context: Context
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "HEX Byte Inspector • ${formatFileSize(uiState.fileSize)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.openWithExternalApp(context) }) {
                    Text("Open External")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                items(uiState.hexDumpLines) { line ->
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text(
                            text = "${line.offset}: ",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = line.hexBytes.padEnd(48, ' '),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "| ${line.asciiText} |",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileInfoSheetContent(
    uiState: FileViewerUiState,
    onOpenExternal: () -> Unit,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = uiState.path.substringAfterLast('/'),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = uiState.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        InfoRow(label = "Category", value = uiState.fileCategory.name)
        InfoRow(label = "Syntax / Format", value = uiState.syntaxLanguage.uppercase())
        InfoRow(label = "File Size", value = formatFileSize(uiState.fileSize))
        if (uiState.totalLines > 0) {
            InfoRow(label = "Total Lines", value = "${uiState.totalLines} lines")
        }
        InfoRow(
            label = "MIME Type",
            value = FileTypeClassifier.getMimeType(uiState.path)
        )
        if (!uiState.contentItem?.sha.isNullOrEmpty()) {
            InfoRow(label = "SHA Hash", value = uiState.contentItem?.sha?.take(10) ?: "")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onOpenExternal,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open External")
            }
            OutlinedButton(
                onClick = onDownload,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download")
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(
        "%.1f %s",
        bytes / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups.coerceAtMost(units.size - 1)]
    )
}
