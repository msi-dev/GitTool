package com.msi.gittool.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msi.gittool.data.local.AuthType
import com.msi.gittool.data.local.TokenManager
import com.msi.gittool.data.local.UserAccount
import com.msi.gittool.data.repository.AuthRepository
import com.msi.gittool.data.repository.RepoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class RepoSortOrder {
    NAME, UPDATED, STARS
}

data class SettingsUiState(
    val activeAccount: UserAccount? = null,
    val accounts: List<UserAccount> = emptyList(),
    val authType: AuthType = AuthType.PAT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sortOrder: RepoSortOrder = RepoSortOrder.UPDATED,
    val notificationsEnabled: Boolean = true,
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
    val isLoading: Boolean = false,
    val message: String? = null
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val repoRepository: RepoRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            val accountsList = authRepository.getSavedAccounts()
            val active = accountsList.find { it.isActive } ?: accountsList.firstOrNull()
            
            _uiState.update {
                it.copy(
                    activeAccount = active,
                    accounts = accountsList,
                    authType = authRepository.getAuthType(),
                    clientId = tokenManager.getOAuthClientId() ?: "",
                    clientSecret = tokenManager.getOAuthClientSecret() ?: "",
                    redirectUri = tokenManager.getOAuthRedirectUri() ?: ""
                )
            }
        }
    }

    fun switchAccount(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = authRepository.switchAccount(username)
            if (success) {
                // Refresh local repo cache for switched account
                repoRepository.clearAllCache()
                authRepository.checkAutoLogin()
                loadSettings()
                _uiState.update { it.copy(isLoading = false, message = "Switched to account @$username") }
            } else {
                _uiState.update { it.copy(isLoading = false, message = "Failed to switch account") }
            }
        }
    }

    fun removeAccount(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            authRepository.removeAccount(username)
            repoRepository.clearAllCache()
            loadSettings()
            _uiState.update { it.copy(isLoading = false, message = "Account @$username removed") }
        }
    }

    fun addAccountWithToken(token: String) {
        if (token.isBlank()) {
            _uiState.update { it.copy(message = "Token cannot be empty") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = authRepository.loginWithToken(token = token, rememberMe = true, authType = AuthType.PAT)
            result.onSuccess { user ->
                loadSettings()
                _uiState.update { it.copy(isLoading = false, message = "Account @${user.login} added successfully") }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, message = error.message ?: "Failed to add account") }
            }
        }
    }

    fun updateTheme(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun updateSortOrder(order: RepoSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun toggleNotifications(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
    }

    fun saveOAuthConfig(clientId: String, clientSecret: String, redirectUri: String) {
        tokenManager.saveOAuthClientId(clientId)
        tokenManager.saveOAuthClientSecret(clientSecret)
        tokenManager.saveOAuthRedirectUri(redirectUri)
        _uiState.update {
            it.copy(
                clientId = clientId,
                clientSecret = clientSecret,
                redirectUri = redirectUri,
                message = "OAuth Credentials updated"
            )
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repoRepository.clearAllCache()
            _uiState.update { it.copy(message = "Local cache cleared successfully") }
        }
    }

    fun syncFirebaseVault() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = authRepository.syncWithFirebaseVault()
            result.onSuccess { accounts ->
                loadSettings()
                _uiState.update { it.copy(isLoading = false, message = "Firebase Database Vault synchronized (${accounts.size} accounts)") }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, message = "Firebase sync failed: ${error.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        fun Factory(
            authRepository: AuthRepository,
            repoRepository: RepoRepository,
            tokenManager: TokenManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(authRepository, repoRepository, tokenManager) as T
            }
        }
    }
}
