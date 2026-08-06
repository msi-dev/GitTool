package com.msi.gittool.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.msi.gittool.GitToolApplication
import com.msi.gittool.ui.login.LoginScreen
import com.msi.gittool.ui.login.LoginViewModel
import com.msi.gittool.ui.main.MainScreen
import com.msi.gittool.ui.main.RepoListViewModel
import com.msi.gittool.ui.theme.ThemeViewModel
import com.msi.gittool.ui.upload.UploadViewModel
import kotlinx.coroutines.delay

@Composable
fun NavGraph(
    navController: NavHostController,
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val container = (context.applicationContext as GitToolApplication).container

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        // 1. SPLASH SCREEN
        composable(Screen.Splash.route) {
            var startAnim by remember { mutableStateOf(false) }
            
            LaunchedEffect(key1 = true) {
                startAnim = true
                delay(1200) // Aesthetic delay for branding
                
                // Inspect stored OAuth or access token session availability
                val authResult = container.authRepository.checkAutoLogin()
                authResult.onSuccess {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }.onFailure {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedVisibility(
                        visible = startAnim,
                        enter = scaleIn() + fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Code bracket icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "GitTool",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Pristine project pushes, automated",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // 2. LOGIN SCREEN
        composable(Screen.Login.route) {
            val loginViewModel: LoginViewModel = viewModel(
                factory = LoginViewModel.Factory(container.authRepository, container.authManager)
            )
            LoginScreen(
                viewModel = loginViewModel,
                onNavigateToMain = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // 3. MAIN DASHBOARD SCREEN (Includes the upload logic)
        composable(Screen.Main.route) {
            val repoViewModel: RepoListViewModel = viewModel(
                factory = RepoListViewModel.Factory(container.repoRepository, container.authRepository)
            )
            val uploadViewModel: UploadViewModel = viewModel(
                factory = UploadViewModel.Factory(container.uploadRepository)
            )

            MainScreen(
                repoViewModel = repoViewModel,
                themeViewModel = themeViewModel,
                uploadViewModel = uploadViewModel,
                onLogoutFinished = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                },
                onNotificationsClick = {
                    navController.navigate(Screen.Notifications.route)
                },
                onRepoClick = { owner, repo ->
                    navController.navigate(Screen.FileBrowser.createRoute(owner, repo))
                }
            )
        }

        // 4. SEARCH SCREEN
        composable(Screen.Search.route) {
            val searchViewModel: com.msi.gittool.ui.search.SearchViewModel = viewModel(
                factory = com.msi.gittool.ui.search.SearchViewModel.Factory(container.repoRepository)
            )
            com.msi.gittool.ui.search.SearchScreen(
                viewModel = searchViewModel,
                onBackClick = { navController.popBackStack() },
                onRepoClick = { owner, repo ->
                    navController.navigate(Screen.FileBrowser.createRoute(owner, repo))
                },
                onUserClick = { username ->
                    navController.navigate(Screen.UserProfile.createRoute(username))
                }
            )
        }

        // 8. USER PROFILE SCREEN
        composable(
            route = Screen.UserProfile.route,
            arguments = listOf(
                androidx.navigation.navArgument("username") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val userProfileViewModel: com.msi.gittool.ui.search.UserProfileViewModel = viewModel(
                factory = com.msi.gittool.ui.search.UserProfileViewModel.Factory(container.repoRepository, username)
            )
            com.msi.gittool.ui.search.UserProfileScreen(
                viewModel = userProfileViewModel,
                onBackClick = { navController.popBackStack() },
                onRepoClick = { owner, repo ->
                    navController.navigate(Screen.FileBrowser.createRoute(owner, repo))
                }
            )
        }

        // 5. ACTIVITY FEED SCREEN
        composable(Screen.Notifications.route) {
            val notificationViewModel: com.msi.gittool.ui.notifications.NotificationViewModel = viewModel(
                factory = com.msi.gittool.ui.notifications.NotificationViewModel.Factory(container.repoRepository)
            )
            com.msi.gittool.ui.notifications.NotificationsScreen(
                viewModel = notificationViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // 6. FILE EXPLORER SCREEN
        composable(
            route = Screen.FileBrowser.route,
            arguments = listOf(
                androidx.navigation.navArgument("owner") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("repo") { type = androidx.navigation.NavType.StringType }
            ),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(350)
                ) + fadeIn(animationSpec = tween(350))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(350)
                ) + fadeOut(animationSpec = tween(350))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(350)
                ) + fadeIn(animationSpec = tween(350))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(350)
                ) + fadeOut(animationSpec = tween(350))
            }
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val fileBrowserViewModel: com.msi.gittool.ui.filebrowser.FileBrowserViewModel = viewModel(
                factory = com.msi.gittool.ui.filebrowser.FileBrowserViewModel.Factory(container.repoRepository, owner, repo)
            )
            com.msi.gittool.ui.filebrowser.FileBrowserScreen(
                viewModel = fileBrowserViewModel,
                onBackClick = { navController.popBackStack() },
                onFileClick = { fileOwner, fileRepo, filePath ->
                    navController.navigate(Screen.FileViewer.createRoute(fileOwner, fileRepo, filePath))
                }
            )
        }

        // 7. FILE VIEWER SCREEN
        composable(
            route = Screen.FileViewer.route,
            arguments = listOf(
                androidx.navigation.navArgument("owner") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("repo") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("path") { type = androidx.navigation.NavType.StringType }
            ),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(350)
                ) + fadeIn(animationSpec = tween(350))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(350)
                ) + fadeOut(animationSpec = tween(350))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(350)
                ) + fadeIn(animationSpec = tween(350))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(350)
                ) + fadeOut(animationSpec = tween(350))
            }
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val rawPath = backStackEntry.arguments?.getString("path") ?: ""
            val decodedPath = android.net.Uri.decode(rawPath)
            
            val fileViewerViewModel: com.msi.gittool.ui.filebrowser.FileViewerViewModel = viewModel(
                factory = com.msi.gittool.ui.filebrowser.FileViewerViewModel.Factory(container.repoRepository, owner, repo, decodedPath)
            )
            com.msi.gittool.ui.filebrowser.FileViewerScreen(
                viewModel = fileViewerViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
