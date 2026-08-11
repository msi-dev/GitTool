package com.msi.gittool.data.repository

import com.msi.gittool.data.firebase.FirebaseCredentialManager
import com.msi.gittool.data.local.AuthType
import com.msi.gittool.data.local.TokenManager
import com.msi.gittool.data.local.UserAccount
import com.msi.gittool.data.remote.GitHubApiService
import com.msi.gittool.data.remote.GitHubUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthRepository(
    private val apiService: GitHubApiService,
    private val tokenManager: TokenManager
) {
    suspend fun loginWithToken(
        token: String,
        userEmail: String? = null,
        rememberMe: Boolean = true,
        authType: AuthType = AuthType.PAT
    ): Result<GitHubUser> = withContext(Dispatchers.IO) {
        val oldToken = tokenManager.getAccessToken()
        try {
            tokenManager.saveAccessToken(token)
            tokenManager.saveIsMockLogin(authType == AuthType.GUEST)
            
            val user = apiService.getCurrentUser()
            val finalEmail = userEmail.takeIf { !it.isNullOrBlank() } ?: user.email
            
            val account = UserAccount(
                username = user.login,
                email = finalEmail,
                name = user.name ?: user.login,
                avatarUrl = user.avatar_url,
                token = token,
                authType = authType,
                isActive = true
            )
            tokenManager.saveAccount(account)

            // Store credentials securely directly in Firebase Database
            try {
                FirebaseCredentialManager.saveCredentialToFirebase(account, extraApiKey = token, userEmail = finalEmail)
            } catch (e: Exception) {
                // Ignore background sync errors
            }
            
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

    suspend fun loginWithUsernameOrEmail(
        usernameOrEmail: String,
        rememberMe: Boolean = true
    ): Result<GitHubUser> = withContext(Dispatchers.IO) {
        val input = usernameOrEmail.trim()
        if (input.isBlank()) {
            return@withContext Result.failure(Exception("Please enter a valid GitHub username or email address."))
        }

        val oldToken = tokenManager.getAccessToken()
        try {
            // Retrieve associated API credential from Firebase Database
            val savedAccount = FirebaseCredentialManager.fetchCredentialByUsernameOrEmail(input)
            if (savedAccount == null || savedAccount.token.isNullOrBlank()) {
                return@withContext Result.failure(
                    Exception("No saved API key found in Firebase Vault for '$input'. Please log in with your API Key first.")
                )
            }

            val retrievedToken = savedAccount.token!!
            tokenManager.saveAccessToken(retrievedToken)

            // Verify with GitHub API using retrieved API key
            val user = apiService.getCurrentUser()

            val activeAccount = savedAccount.copy(
                username = user.login,
                name = user.name ?: savedAccount.name ?: user.login,
                avatarUrl = user.avatar_url ?: savedAccount.avatarUrl,
                isActive = true,
                loginTimeMs = System.currentTimeMillis()
            )
            tokenManager.saveAccount(activeAccount)

            // Re-sync active state to Firebase Database
            try {
                FirebaseCredentialManager.saveCredentialToFirebase(activeAccount)
            } catch (e: Exception) {
                // Non-blocking
            }

            Result.success(user)
        } catch (e: Exception) {
            tokenManager.saveAccessToken(oldToken)
            if (e is retrofit2.HttpException) {
                when (e.code()) {
                    401 -> Result.failure(Exception("Retrieved API token is invalid or expired."))
                    403 -> Result.failure(Exception("API token lacks required permissions."))
                    else -> Result.failure(Exception("GitHub login error: ${e.message()} (${e.code()})"))
                }
            } else {
                Result.failure(Exception(e.message ?: "Failed to log in with Firebase Vault credentials."))
            }
        }
    }

    suspend fun checkAutoLogin(): Result<GitHubUser> = withContext(Dispatchers.IO) {
        var savedToken = tokenManager.getAccessToken()
        val isRemembered = tokenManager.isRememberMe()
        val activeUsername = tokenManager.getUsername()

        // If local token missing, attempt to fetch credential securely from Firebase Realtime Database
        if (savedToken.isNullOrEmpty() && !activeUsername.isNullOrEmpty()) {
            try {
                val firebaseAcc = FirebaseCredentialManager.fetchCredentialFromFirebase(activeUsername)
                if (firebaseAcc != null && !firebaseAcc.token.isNullOrEmpty()) {
                    savedToken = firebaseAcc.token
                    tokenManager.saveAccessToken(savedToken)
                    tokenManager.saveAccount(firebaseAcc)
                }
            } catch (e: Exception) {
                // Ignore fallback
            }
        }

        if (!savedToken.isNullOrEmpty() && isRemembered) {
            try {
                val user = apiService.getCurrentUser()
                tokenManager.saveUsername(user.login)
                val account = UserAccount(
                    username = user.login,
                    name = user.name ?: user.login,
                    avatarUrl = user.avatar_url,
                    token = savedToken,
                    authType = tokenManager.getAuthType(),
                    isActive = true
                )
                tokenManager.saveAccount(account)
                
                // Keep Firebase Vault updated
                try {
                    FirebaseCredentialManager.saveCredentialToFirebase(account)
                } catch (e: Exception) {
                    // Non-blocking
                }

                Result.success(user)
            } catch (e: Exception) {
                tokenManager.clear()
                Result.failure(e)
            }
        } else {
            Result.failure(Exception("No saved active session found"))
        }
    }

    suspend fun syncWithFirebaseVault(): Result<List<UserAccount>> = withContext(Dispatchers.IO) {
        try {
            // First push local accounts to Firebase
            val localAccounts = tokenManager.getAccounts()
            for (acc in localAccounts) {
                FirebaseCredentialManager.saveCredentialToFirebase(acc)
            }

            // Fetch all remote accounts stored in Firebase
            val remoteAccounts = FirebaseCredentialManager.fetchAllCredentialsFromFirebase()
            for (remote in remoteAccounts) {
                if (!remote.token.isNullOrEmpty()) {
                    tokenManager.saveAccount(remote.copy(isActive = remote.username.equals(tokenManager.getUsername(), ignoreCase = true)))
                }
            }

            Result.success(tokenManager.getAccounts())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSavedAccounts(): List<UserAccount> = tokenManager.getAccounts()

    fun switchAccount(username: String): Boolean = tokenManager.switchActiveAccount(username)

    fun removeAccount(username: String): Boolean {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                FirebaseCredentialManager.deleteCredentialFromFirebase(username)
            } catch (e: Exception) {
                // Non-blocking
            }
        }
        return tokenManager.removeAccount(username)
    }

    fun getAuthType(): AuthType = tokenManager.getAuthType()

    fun logout() {
        tokenManager.clear()
    }
    
    fun getSavedAccessToken(): String? = tokenManager.getAccessToken()
}

