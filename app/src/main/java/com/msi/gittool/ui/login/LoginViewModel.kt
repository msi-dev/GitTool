package com.msi.gittool.ui.login

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msi.gittool.data.repository.AuthRepository
import com.msi.gittool.data.remote.AuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val tokenInput: String = "",
    val rememberMe: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccess: Boolean = false
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // Collect OAuth state updates from AuthManager
        viewModelScope.launch {
            authManager.oauthStateFlow.collect { oauthState ->
                when (oauthState) {
                    is AuthManager.OAuthState.Idle -> {
                        // Pass
                    }
                    is AuthManager.OAuthState.Loading -> {
                        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    }
                    is AuthManager.OAuthState.Success -> {
                        // Conduct downstream validation and persistence
                        loginWithOAuthToken(oauthState.accessToken)
                    }
                    is AuthManager.OAuthState.Error -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = oauthState.message) }
                    }
                }
            }
        }
    }

    fun updateTokenInput(token: String) {
        _uiState.update { it.copy(tokenInput = token, errorMessage = null) }
    }

    fun updateRememberMe(remember: Boolean) {
        _uiState.update { it.copy(rememberMe = remember) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun loginWithToken() {
        val token = _uiState.value.tokenInput.trim()
        if (token.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter a personal access token") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = authRepository.loginWithToken(token, _uiState.value.rememberMe)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isLoginSuccess = true) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Authentication failed") }
            }
        }
    }

    private fun loginWithOAuthToken(token: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = authRepository.loginWithToken(token, _uiState.value.rememberMe)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isLoginSuccess = true) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Single Sign-On integration failed") }
            }
        }
    }

    fun startOAuthFlow(activity: Activity) {
        clearError()
        authManager.startOAuth(activity)
    }

    companion object {
        fun Factory(authRepository: AuthRepository, authManager: AuthManager): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LoginViewModel(authRepository, authManager) as T
            }
        }
    }
}
