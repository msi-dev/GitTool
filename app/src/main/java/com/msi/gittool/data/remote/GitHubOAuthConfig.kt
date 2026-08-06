package com.msi.gittool.data.remote

import com.msi.gittool.data.local.TokenManager
import com.msi.gittool.util.Constants

object GitHubOAuthConfig {
    fun getClientId(tokenManager: TokenManager? = null): String {
        val stored = tokenManager?.getOAuthClientId()
        return if (!stored.isNullOrBlank()) stored else Constants.GITHUB_CLIENT_ID
    }

    fun getClientSecret(tokenManager: TokenManager? = null): String {
        val stored = tokenManager?.getOAuthClientSecret()
        return if (!stored.isNullOrBlank()) stored else Constants.GITHUB_CLIENT_SECRET
    }

    fun getRedirectUri(tokenManager: TokenManager? = null): String {
        val stored = tokenManager?.getOAuthRedirectUri()
        return if (!stored.isNullOrBlank()) stored else Constants.GITHUB_REDIRECT_URI
    }

    val CLIENT_ID: String get() = Constants.GITHUB_CLIENT_ID
    val REDIRECT_URI: String get() = Constants.GITHUB_REDIRECT_URI
    const val SCOPE = "repo user"
    val AUTH_ENDPOINT: String get() = Constants.GITHUB_OAUTH_AUTHORIZE_URL
    val TOKEN_ENDPOINT: String get() = Constants.GITHUB_OAUTH_TOKEN_URL
}

