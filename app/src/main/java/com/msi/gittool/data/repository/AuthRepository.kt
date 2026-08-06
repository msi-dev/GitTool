package com.msi.gittool.data.repository

import com.msi.gittool.data.local.TokenManager
import com.msi.gittool.data.remote.GitHubApiService
import com.msi.gittool.data.remote.GitHubUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val apiService: GitHubApiService,
    private val tokenManager: TokenManager
) {
    suspend fun loginWithToken(token: String, rememberMe: Boolean): Result<GitHubUser> = withContext(Dispatchers.IO) {
        val oldToken = tokenManager.getAccessToken()
        try {
            tokenManager.saveAccessToken(token)
            tokenManager.saveIsMockLogin(false)
            
            val user = apiService.getCurrentUser()
            tokenManager.saveUsername(user.login)
            tokenManager.saveRememberMe(rememberMe)
            
            Result.success(user)
        } catch (e: Exception) {
            tokenManager.saveAccessToken(oldToken) // Restore if it fails
            if (e is retrofit2.HttpException) {
                when (e.code()) {
                    401 -> Result.failure(Exception("Invalid token."))
                    403 -> Result.failure(Exception("Token lacks required permissions."))
                    else -> Result.failure(Exception("GitHub returned error: ${e.message()} (Code: ${e.code()})"))
                }
            } else if (e is java.io.IOException) {
                Result.failure(Exception("Unable to connect to GitHub."))
            } else {
                Result.failure(Exception("Authentication failed: ${e.localizedMessage ?: "Unknown error"}"))
            }
        }
    }

    suspend fun checkAutoLogin(): Result<GitHubUser> = withContext(Dispatchers.IO) {
        val savedToken = tokenManager.getAccessToken()
        val isRemembered = tokenManager.isRememberMe()
        if (!savedToken.isNullOrEmpty() && isRemembered) {
            try {
                val user = apiService.getCurrentUser()
                tokenManager.saveUsername(user.login)
                Result.success(user)
            } catch (e: Exception) {
                tokenManager.clear()
                Result.failure(e)
            }
        } else {
            Result.failure(Exception("No saved active session found"))
        }
    }

    fun logout() {
        tokenManager.clear()
    }
    
    fun getSavedAccessToken(): String? = tokenManager.getAccessToken()
}
