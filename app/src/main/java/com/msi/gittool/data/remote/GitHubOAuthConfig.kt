package com.msi.gittool.data.remote

import com.msi.gittool.util.Constants

object GitHubOAuthConfig {
    val CLIENT_ID: String get() = Constants.GITHUB_CLIENT_ID
    val REDIRECT_URI: String get() = Constants.GITHUB_REDIRECT_URI
    const val SCOPE = "repo user"
    val AUTH_ENDPOINT: String get() = Constants.GITHUB_OAUTH_AUTHORIZE_URL
    val TOKEN_ENDPOINT: String get() = Constants.GITHUB_OAUTH_TOKEN_URL
}
