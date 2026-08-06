package com.msi.gittool.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Main : Screen("main")
    data object Search : Screen("search")
    data object Notifications : Screen("notifications")
    data object FileBrowser : Screen("filebrowser/{owner}/{repo}") {
        fun createRoute(owner: String, repo: String) = "filebrowser/$owner/$repo"
    }
    data object FileViewer : Screen("fileviewer/{owner}/{repo}/{path}") {
        fun createRoute(owner: String, repo: String, path: String) = "fileviewer/$owner/$repo/${Uri.encode(path)}"
    }
    data object UserProfile : Screen("userprofile/{username}") {
        fun createRoute(username: String) = "userprofile/$username"
    }
}
