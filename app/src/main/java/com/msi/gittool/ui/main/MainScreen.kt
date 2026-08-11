package com.msi.gittool.ui.main

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.gittool.data.remote.GitHubRepo
import com.msi.gittool.ui.components.DashboardStatsOverview
import com.msi.gittool.ui.components.InAppNotificationToast
import com.msi.gittool.ui.components.InAppToastManager
import com.msi.gittool.ui.components.RepoItem
import com.msi.gittool.ui.components.RepoSkeletonList
import com.msi.gittool.ui.components.TopBarWithMenu
import com.msi.gittool.ui.theme.ThemeViewModel
import com.msi.gittool.util.GitHubUrlParser
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.msi.gittool.ui.upload.UploadProgressOverlay
import com.msi.gittool.ui.upload.UploadSheet
import com.msi.gittool.ui.upload.UploadViewModel
import com.msi.gittool.util.NetworkUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repoViewModel: RepoListViewModel,
    themeViewModel: ThemeViewModel,
    uploadViewModel: UploadViewModel,
    onLogoutFinished: () -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRepoClick: (owner: String, repo: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by repoViewModel.uiState.collectAsState()
    val currentFilter by repoViewModel.currentFilter.collectAsState()
    val filteredRepos by repoViewModel.filteredRepos.collectAsState()
    val showPrivateWarning by repoViewModel.showPrivateWarning.collectAsState()
    val bookmarksList by repoViewModel.bookmarksFlow.collectAsState()
    val updateState by repoViewModel.updateState.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showUploadSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var repoToDelete by remember { mutableStateOf<GitHubRepo?>(null) }
    var showAvatarChangeDialog by remember { mutableStateOf(false) }
    
    // Dialog input controls
    var showImportDialog by remember { mutableStateOf(false) }
    var importUrlInput by remember { mutableStateOf("") }
    var showForkDialog by remember { mutableStateOf(false) }
    var forkRepoNameInput by remember { mutableStateOf("") } // e.g., "owner/repo"

    // FAB Speed-dial state
    var isFabExpanded by remember { mutableStateOf(false) }

    // Read general network connectivity state
    val isOnline = remember(uiState.isLoading, uiState.isRefreshing) {
        NetworkUtil.isInternetAvailable(context)
    }

    val listState = rememberLazyListState()

    // 1. Double check and demand POST_NOTIFICATIONS & Storage access permission sequence
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Graceful completion
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // Trigger initial cached/online loading on startup
    LaunchedEffect(Unit) {
        repoViewModel.loadUserAndRepos(context, forceRefresh = false)
        repoViewModel.checkForUpdates()
    }

    val unreadCount by repoViewModel.unreadActivityCount.collectAsState()
    var showSortSheet by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            TopBarWithMenu(
                user = uiState.user,
                onLogout = {
                    repoViewModel.logout()
                    onLogoutFinished()
                    Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                },
                onThemeSelect = { showThemeDialog = true },
                onProfileClick = { showProfileSheet = true },
                onSearchClick = onSearchClick,
                onNotificationsClick = onNotificationsClick,
                onSettingsClick = onSettingsClick,
                onSortSelect = { showSortSheet = true },
                unreadNotificationsCount = unreadCount,
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                modifier = Modifier.testTag("repo_navigation_bar")
            ) {
                val navTabs = listOf(
                    Triple(RepoFilter.PUBLIC, "Public", Icons.Outlined.Public to Icons.Filled.Public),
                    Triple(RepoFilter.BOOKMARKS, "Bookmarks", Icons.Outlined.Star to Icons.Filled.Star),
                    Triple(RepoFilter.PRIVATE, "Private", Icons.Outlined.Lock to Icons.Filled.Lock),
                    Triple(RepoFilter.FORKED, "Forked", Icons.Default.CallSplit to Icons.Filled.CallSplit)
                )

                navTabs.forEach { (filter, label, icons) ->
                    val isSelected = currentFilter == filter
                    val (outlinedIcon, filledIcon) = icons

                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1.0f,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "nav_item_scale"
                    )

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { repoViewModel.selectFilter(filter) },
                        icon = {
                            Box(modifier = Modifier.scale(scale)) {
                                AnimatedContent(
                                    targetState = isSelected,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                                    },
                                    label = "nav_icon_crossfade"
                                ) { selected ->
                                    Icon(
                                        imageVector = if (selected) filledIcon else outlinedIcon,
                                        contentDescription = "$label repositories",
                                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("${label.lowercase()}_tab")
                    )
                }
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Expanded Action 1: Upload
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + expandVertically() + slideInVertically(initialOffsetY = { 50 }),
                    exit = fadeOut() + shrinkVertically() + slideOutVertically(targetOffsetY = { 50 })
                ) {
                    FloatingActionButton(
                        onClick = {
                            isFabExpanded = false
                            uploadViewModel.reset()
                            showUploadSheet = true
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(48.dp).testTag("fab_upload_repo")
                    ) {
                        Icon(imageVector = Icons.Default.Backup, contentDescription = "Scan & Upload")
                    }
                }

                // Expanded Action 2: Import
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + expandVertically() + slideInVertically(initialOffsetY = { 50 }),
                    exit = fadeOut() + shrinkVertically() + slideOutVertically(targetOffsetY = { 50 })
                ) {
                    FloatingActionButton(
                        onClick = {
                            isFabExpanded = false
                            importUrlInput = ""
                            showImportDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(48.dp).testTag("fab_import_repo")
                    ) {
                        Icon(imageVector = Icons.Default.VerticalAlignBottom, contentDescription = "Import External Git")
                    }
                }

                // Expanded Action 3: Fork
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + expandVertically() + slideInVertically(initialOffsetY = { 50 }),
                    exit = fadeOut() + shrinkVertically() + slideOutVertically(targetOffsetY = { 50 })
                ) {
                    FloatingActionButton(
                        onClick = {
                            isFabExpanded = false
                            forkRepoNameInput = ""
                            showForkDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(48.dp).testTag("fab_fork_repo")
                    ) {
                        Icon(imageVector = Icons.Default.CallSplit, contentDescription = "Fork Repository")
                    }
                }

                // Main Speed dial Fab controller toggle
                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("app_dashboard_fab")
                ) {
                    Icon(
                        imageVector = if (isFabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Expand Speed dial settings panel"
                    )
                }
            }
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = uiState.isLoading,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    )
                }

                // If offline, display a beautiful high-contrast banner indicating cache mode is active
                if (!isOnline) {
                    Surface(
                        color = Color(0xFFFFB300),
                        contentColor = Color.Black,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Viewing Local Database Cache (Offline Mode)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { repoViewModel.loadUserAndRepos(context, forceRefresh = true) },
                    modifier = Modifier.fillMaxSize().weight(1f)
                ) {
                    AnimatedContent(
                        targetState = currentFilter,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                (slideInHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> width / 3 } +
                                        fadeIn(animationSpec = tween(280))) togetherWith
                                        (slideOutHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> -width / 3 } +
                                                fadeOut(animationSpec = tween(280)))
                            } else {
                                (slideInHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> -width / 3 } +
                                        fadeIn(animationSpec = tween(280))) togetherWith
                                        (slideOutHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> width / 3 } +
                                                fadeOut(animationSpec = tween(280)))
                            }
                        },
                        label = "tab_content_transition",
                        modifier = Modifier.fillMaxSize()
                    ) { filterState ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (filteredRepos.isEmpty() && uiState.isLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 640.dp)
                                        .align(Alignment.TopCenter)
                                        .padding(16.dp)
                                ) {
                                    RepoSkeletonList()
                                }
                            } else if (filteredRepos.isEmpty()) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 480.dp)
                                        .align(Alignment.Center)
                                        .padding(32.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.size(72.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Text(
                                        text = when (filterState) {
                                            RepoFilter.PUBLIC -> "No Public Repositories"
                                            RepoFilter.BOOKMARKS -> "No Bookmarked Items"
                                            RepoFilter.PRIVATE -> "No Private Repositories"
                                            RepoFilter.FORKED -> "No Forked Repositories"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = when (filterState) {
                                            RepoFilter.PUBLIC -> "Your public repository list is empty. Touch the '+' button below to upload local projects."
                                            RepoFilter.BOOKMARKS -> "Bookmarks show up here as quick access anchors to view files offline."
                                            RepoFilter.PRIVATE -> "Your private repository list is empty under this account."
                                            RepoFilter.FORKED -> "You don't have any forked repositories under this account."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    Button(
                                        onClick = { repoViewModel.loadUserAndRepos(context, forceRefresh = true) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Refresh List")
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 640.dp)
                                        .align(Alignment.TopCenter)
                                ) {
                                    item {
                                        val totalStars = remember(uiState.publicRepos, uiState.privateRepos) {
                                            uiState.publicRepos.sumOf { it.stargazers_count ?: 0 } +
                                                    uiState.privateRepos.sumOf { it.stargazers_count ?: 0 }
                                        }
                                        val totalForks = remember(uiState.publicRepos, uiState.privateRepos) {
                                            uiState.publicRepos.sumOf { it.forks_count ?: 0 } +
                                                    uiState.privateRepos.sumOf { it.forks_count ?: 0 }
                                        }
                                        DashboardStatsOverview(
                                            user = uiState.user,
                                            publicCount = uiState.publicRepos.size,
                                            privateCount = uiState.privateRepos.size,
                                            totalStars = totalStars,
                                            totalForks = totalForks,
                                            totalBookmarks = bookmarksList.size,
                                            isLoading = uiState.isLoading,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                    items(
                                        items = filteredRepos,
                                        key = { it.id }
                                    ) { repo ->
                                        val isBookmarked = bookmarksList.any { it.id == repo.id }
                                        RepoItem(
                                            repo = repo,
                                            isBookmarked = isBookmarked,
                                            onToggleBookmark = { repoViewModel.toggleBookmark(repo) },
                                            onOpenInBrowser = { url ->
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "No web browser found on device.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onDownloadZip = {
                                                val parts = repo.full_name.split("/")
                                                if (parts.size >= 2) {
                                                    Toast.makeText(context, "Initiating download sequence...", Toast.LENGTH_SHORT).show()
                                                    repoViewModel.downloadRepositoryZip(
                                                        context = context,
                                                        owner = parts[0],
                                                        repoName = parts[1],
                                                        branch = repo.default_branch ?: "main"
                                                    )
                                                }
                                            },
                                            onCardClick = {
                                                val parts = repo.full_name.split("/")
                                                if (parts.size >= 2) {
                                                    onRepoClick(parts[0], parts[1])
                                                }
                                            },
                                            onShareClick = {
                                                try {
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_SUBJECT, repo.name)
                                                        putExtra(Intent.EXTRA_TEXT, "Check out this GitHub repository: ${repo.html_url}")
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "Share Repository"))
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Cannot share repository.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onDeleteClick = {
                                                val parts = repo.full_name.split("/")
                                                if (parts.size >= 2) {
                                                    repoToDelete = repo
                                                    showDeleteDialog = true
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Persistent Real-time Upload Progress Overlay
            UploadProgressOverlay(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp)
            )

            // In-App Notification Toast directly above bottom navigation bar
            InAppNotificationToast(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )

            // Error display Snackbar banner
            if (!uiState.errorMessage.isNullOrEmpty()) {
                Snackbar(
                    action = {
                        TextButton(onClick = { repoViewModel.loadUserAndRepos(context, forceRefresh = true) }) {
                            Text("Retry", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(text = uiState.errorMessage ?: "")
                }
            }

            // Global floating capsule sync indicator
            AnimatedVisibility(
                visible = uiState.isLoading || uiState.isRefreshing,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -40 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -40 }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.testTag("global_fetching_sync_capsule")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (uiState.isRefreshing) "Refreshing..." else "Syncing with GitHub...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Modal dialog to select sorting options for repositories
    if (showSortSheet) {
        val currentSortOrder by repoViewModel.currentSortOrder.collectAsState()
        AlertDialog(
            onDismissRequest = { showSortSheet = false },
            title = {
                Text(
                    text = "Sort Repositories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                repoViewModel.selectSortOrder(RepoSortOrder.NAME)
                                showSortSheet = false
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .testTag("sort_by_name")
                    ) {
                        RadioButton(
                            selected = currentSortOrder == RepoSortOrder.NAME,
                            onClick = {
                                repoViewModel.selectSortOrder(RepoSortOrder.NAME)
                                showSortSheet = false
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Sort by Name", style = MaterialTheme.typography.bodyLarge)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                repoViewModel.selectSortOrder(RepoSortOrder.LAST_UPDATED)
                                showSortSheet = false
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .testTag("sort_by_last_updated")
                    ) {
                        RadioButton(
                            selected = currentSortOrder == RepoSortOrder.LAST_UPDATED,
                            onClick = {
                                repoViewModel.selectSortOrder(RepoSortOrder.LAST_UPDATED)
                                showSortSheet = false
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Sort by Last Updated Date", style = MaterialTheme.typography.bodyLarge)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                repoViewModel.selectSortOrder(RepoSortOrder.STAR_COUNT)
                                showSortSheet = false
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .testTag("sort_by_star_count")
                    ) {
                        RadioButton(
                            selected = currentSortOrder == RepoSortOrder.STAR_COUNT,
                            onClick = {
                                repoViewModel.selectSortOrder(RepoSortOrder.STAR_COUNT)
                                showSortSheet = false
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Sort by Star Count", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortSheet = false }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal dialogue to select core app light/dark styles
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    text = "Theming Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeOptionRow(
                        title = "System Default",
                        selected = themeViewModel.themeMode.collectAsState().value == com.msi.gittool.ui.theme.ThemeMode.SYSTEM,
                        onClick = { themeViewModel.setThemeMode(com.msi.gittool.ui.theme.ThemeMode.SYSTEM) }
                    )
                    ThemeOptionRow(
                        title = "Light Theme Style",
                        selected = themeViewModel.themeMode.collectAsState().value == com.msi.gittool.ui.theme.ThemeMode.LIGHT,
                        onClick = { themeViewModel.setThemeMode(com.msi.gittool.ui.theme.ThemeMode.LIGHT) }
                    )
                    ThemeOptionRow(
                        title = "Dark Theme Style",
                        selected = themeViewModel.themeMode.collectAsState().value == com.msi.gittool.ui.theme.ThemeMode.DARK,
                        onClick = { themeViewModel.setThemeMode(com.msi.gittool.ui.theme.ThemeMode.DARK) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal sheet for scanning project folder uploads
    if (showUploadSheet) {
        UploadSheet(
            viewModel = uploadViewModel,
            onUploadSuccess = {
                showUploadSheet = false
                repoViewModel.loadUserAndRepos(context, forceRefresh = true)
            },
            onDismissRequest = { showUploadSheet = false }
        )
    }

    // Dialog for Repository forks
    if (showForkDialog) {
        val parsedFork = GitHubUrlParser.parse(forkRepoNameInput)
        AlertDialog(
            onDismissRequest = { showForkDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ForkRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fork Repository by Link", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter or paste any GitHub repository link (e.g. https://github.com/msi-dev/Music.git). GitTool will locate the project on GitHub and automatically create a fork under your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = forkRepoNameInput,
                        onValueChange = { forkRepoNameInput = it },
                        label = { Text("GitHub Repository Link or Owner/Repo") },
                        placeholder = { Text("https://github.com/msi-dev/Music.git") },
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (forkRepoNameInput.isNotEmpty()) {
                                IconButton(onClick = { forkRepoNameInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear text")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("repo_fork_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (parsedFork != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Target Repository: ${parsedFork.fullName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = parsedFork.httpsUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    } else if (forkRepoNameInput.trim().isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Invalid link format. Enter link like https://github.com/msi-dev/Music.git",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { forkRepoNameInput = "https://github.com/msi-dev/Music.git" },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Fill example: https://github.com/msi-dev/Music.git",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = GitHubUrlParser.parse(forkRepoNameInput)
                        if (parsed != null) {
                            showForkDialog = false
                            repoViewModel.forkRepo(parsed.owner, parsed.repoName, context) { success, msg ->
                                InAppToastManager.showToast(msg, isError = !success)
                            }
                        } else {
                            InAppToastManager.showToast(
                                "Invalid GitHub link. Example: https://github.com/msi-dev/Music.git",
                                isError = true
                            )
                        }
                    },
                    enabled = parsedFork != null,
                    modifier = Modifier.testTag("repo_fork_confirm_btn")
                ) {
                    Text("Fork to Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForkDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Dialog for Repository Import actions
    if (showImportDialog) {
        val parsedImport = GitHubUrlParser.parse(importUrlInput)
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Repository by Link", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter or paste any GitHub project link (e.g. https://github.com/msi-dev/Music.git). GitTool will locate the repository on GitHub and import it directly into your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = importUrlInput,
                        onValueChange = { importUrlInput = it },
                        label = { Text("GitHub Repository Link") },
                        placeholder = { Text("https://github.com/msi-dev/Music.git") },
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (importUrlInput.isNotEmpty()) {
                                IconButton(onClick = { importUrlInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear text")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("repo_import_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (parsedImport != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Project: ${parsedImport.fullName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = parsedImport.httpsUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    } else if (importUrlInput.trim().isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Invalid link format. Enter link like https://github.com/msi-dev/Music.git",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { importUrlInput = "https://github.com/msi-dev/Music.git" },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Fill example: https://github.com/msi-dev/Music.git",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = GitHubUrlParser.parse(importUrlInput)
                        if (parsed != null) {
                            showImportDialog = false
                            repoViewModel.importExternalRepo(importUrlInput, context) { success, msg ->
                                InAppToastManager.showToast(msg, isError = !success)
                            }
                        } else {
                            InAppToastManager.showToast(
                                "Invalid GitHub repository link. Example: https://github.com/msi-dev/Music.git",
                                isError = true
                            )
                        }
                    },
                    enabled = parsedImport != null,
                    modifier = Modifier.testTag("repo_import_confirm_btn")
                ) {
                    Text("Import Repository")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Modal sheet / Dialog to edit User profile metrics
    if (showProfileSheet) {
        var profileName by remember { mutableStateOf(uiState.user?.name ?: "") }
        var profileBio by remember { mutableStateOf(uiState.user?.bio ?: "") }
        var profileBlog by remember { mutableStateOf(uiState.user?.blog ?: "") }
        var profileLocation by remember { mutableStateOf(uiState.user?.location ?: "") }

        AlertDialog(
            onDismissRequest = { showProfileSheet = false },
            title = { Text("My GitHub Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // Avatar display with camera overlay
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        if (uiState.user?.avatar_url != null) {
                            AsyncImage(
                                model = uiState.user?.avatar_url,
                                contentDescription = "User profile picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "No avatar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { showAvatarChangeDialog = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Edit avatar",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Profile info fields
                    Text(
                        text = "Customize profile fields on GitHub.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                    )
                    OutlinedTextField(
                        value = profileBio,
                        onValueChange = { profileBio = it },
                        label = { Text("Bio description") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_bio_input")
                    )
                    OutlinedTextField(
                        value = profileLocation,
                        onValueChange = { profileLocation = it },
                        label = { Text("Location") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_location_input")
                    )
                    OutlinedTextField(
                        value = profileBlog,
                        onValueChange = { profileBlog = it },
                        label = { Text("Personal Link / Blog") },
                        trailingIcon = {
                            if (profileBlog.isNotBlank()) {
                                IconButton(onClick = {
                                    try {
                                        var cleanUrl = profileBlog.trim()
                                        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                                            cleanUrl = "https://$cleanUrl"
                                        }
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Invalid website link", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Open personal website in browser"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("profile_blog_input")
                    )

                    // Stats summary panel
                    HorizontalDivider()
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${uiState.user?.followers ?: 0}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Followers", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${uiState.user?.following ?: 0}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Following", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${uiState.user?.public_repos ?: 0}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Repos", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showProfileSheet = false
                        repoViewModel.updateProfile(
                            name = profileName,
                            bio = profileBio,
                            blog = profileBlog,
                            location = profileLocation
                        ) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("profile_save_btn")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileSheet = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal warning Dialog confirmation for reading private items
    if (showPrivateWarning) {
        AlertDialog(
            onDismissRequest = { repoViewModel.onPrivateWarningResult(false) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Private Data Access Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Private Repositories Check",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "You are about to access your private repositories.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "These private modules may contain sensitive items, proprietary layouts, and configurations. Please check in a safe visual space.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { repoViewModel.onPrivateWarningResult(true) },
                    modifier = Modifier.testTag("warning_confirm_button")
                ) {
                    Text("Continue Access")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { repoViewModel.onPrivateWarningResult(false) },
                    modifier = Modifier.testTag("warning_cancel_button")
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDeleteDialog && repoToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; repoToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Danger Warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "Irreversible Action Warning",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Are you sure you want to delete ${repoToDelete!!.name}?",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "⚠️ CRITICAL CONSEQUENCES:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "• All files, branches, issues, and histories will be instantly and permanently deleted on GitHub.\n• You will lose ALL data and commits of this repo.\n• This action cannot be redressed.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val repo = repoToDelete!!
                        showDeleteDialog = false
                        repoToDelete = null
                        val parts = repo.full_name.split("/")
                        if (parts.size >= 2) {
                            repoViewModel.deleteRepository(context, parts[0], parts[1]) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; repoToDelete = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showAvatarChangeDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarChangeDialog = false },
            title = { Text("Change Profile Picture") },
            text = { Text("Direct avatar photo upload is not supported by standard GitHub REST API. Would you like to open GitHub's official profile configuration pages in your standard browser?") },
            confirmButton = {
                Button(
                    onClick = {
                        showAvatarChangeDialog = false
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/profile"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No browser detected.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Open Profile Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAvatarChangeDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (updateState.hasUpdate && updateState.latestRelease != null) {
        val release = updateState.latestRelease!!
        AlertDialog(
            onDismissRequest = { repoViewModel.dismissUpdateDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Update Available",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Update Available!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "A new version of GitTool is available to download.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "New Version: ${release.tag_name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (!com.msi.gittool.BuildConfig.VERSION_NAME.isNullOrEmpty()) {
                        Text(
                            text = "Current Version: ${com.msi.gittool.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!release.body.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "What's New:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = release.body,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repoViewModel.dismissUpdateDialog()
                        try {
                            val downloadUrl = release.html_url ?: "https://github.com/MSI-Sirajul/GitTool/releases"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No browser detected to download the update.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { repoViewModel.dismissUpdateDialog() }) {
                    Text("Later")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun ThemeOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
