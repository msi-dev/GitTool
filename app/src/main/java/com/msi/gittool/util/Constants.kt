package com.msi.gittool.util

import com.msi.gittool.security.NativeSecurity

object Constants {
    
    val GITHUB_CLIENT_ID: String get() = NativeSecurity.getClientId()

    val GITHUB_CLIENT_SECRET: String get() = NativeSecurity.getClientSecret()

    const val GITHUB_REDIRECT_URI = "gittool://callback"
    const val GITHUB_OAUTH_AUTHORIZE_URL = "https://github.com/login/oauth/authorize"
    const val GITHUB_OAUTH_TOKEN_URL = "https://github.com/login/oauth/access_token"
    
    const val APP_REPO_OWNER = "MSI-Sirajul"
    const val APP_REPO_NAME = "GitTool"
}
