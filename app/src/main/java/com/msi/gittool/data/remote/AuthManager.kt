package com.msi.gittool.data.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.msi.gittool.data.local.TokenManager
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AuthManager(
    private val context: Context,
    private val tokenManager: TokenManager
) {
    private val authService = AuthorizationService(context)
    private var authState = AuthState()

    private val _oauthStateFlow = MutableStateFlow<OAuthState>(OAuthState.Idle)
    val oauthStateFlow: StateFlow<OAuthState> = _oauthStateFlow.asStateFlow()

    companion object {
        const val RC_AUTH = 1001
    }

    sealed interface OAuthState {
        object Idle : OAuthState
        object Loading : OAuthState
        data class Success(val accessToken: String) : OAuthState
        data class Error(val message: String) : OAuthState
    }

    fun startOAuth(activity: Activity) {
        _oauthStateFlow.value = OAuthState.Loading
        val clientId = GitHubOAuthConfig.getClientId(tokenManager)
        val redirectUri = GitHubOAuthConfig.getRedirectUri(tokenManager)

        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(GitHubOAuthConfig.AUTH_ENDPOINT),
            Uri.parse(GitHubOAuthConfig.TOKEN_ENDPOINT)
        )
        
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(redirectUri)
        ).setScope(GitHubOAuthConfig.SCOPE)
         .build()

        try {
            val authIntent = authService.getAuthorizationRequestIntent(authRequest)
            activity.startActivityForResult(authIntent, RC_AUTH)
        } catch (e: Exception) {
            try {
                val authUrl = Uri.parse(GitHubOAuthConfig.AUTH_ENDPOINT).buildUpon()
                    .appendQueryParameter("client_id", clientId)
                    .appendQueryParameter("redirect_uri", redirectUri)
                    .appendQueryParameter("scope", GitHubOAuthConfig.SCOPE)
                    .build()
                val browserIntent = Intent(Intent.ACTION_VIEW, authUrl)
                activity.startActivity(browserIntent)
            } catch (ex: Exception) {
                _oauthStateFlow.value = OAuthState.Error("Failed to launch login: ${ex.message}")
            }
        }
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Try AppAuth response parsing
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        
        if (response != null) {
            authState.update(response, exception)
            exchangeCode(response)
            return
        }

        // 2. Fallback to direct Intent URI query parameter parsing (for browser / deep link redirects)
        val dataUri = intent.data
        if (dataUri != null) {
            val code = dataUri.getQueryParameter("code")
            val accessToken = dataUri.getQueryParameter("access_token")
                ?: dataUri.getQueryParameter("token")
            val error = dataUri.getQueryParameter("error")
                ?: dataUri.getQueryParameter("error_description")

            if (!accessToken.isNullOrEmpty()) {
                _oauthStateFlow.value = OAuthState.Success(accessToken)
                return
            }

            if (!code.isNullOrEmpty()) {
                exchangeCodeDirect(code)
                return
            }

            if (!error.isNullOrEmpty()) {
                _oauthStateFlow.value = OAuthState.Error("Authentication Error: $error")
                return
            }
        }

        if (exception != null) {
            _oauthStateFlow.value = OAuthState.Error(
                exception.message ?: "Authorization failed or cancelled"
            )
        }
    }

    fun handleAuthorizationResponse(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == RC_AUTH) {
            if (data != null) {
                handleIntent(data)
            } else {
                _oauthStateFlow.value = OAuthState.Error("No authorization response received")
            }
        }
    }

    private fun exchangeCode(response: AuthorizationResponse) {
        _oauthStateFlow.value = OAuthState.Loading
        val clientSecret = GitHubOAuthConfig.getClientSecret(tokenManager)
        val clientAuth = if (!clientSecret.isNullOrEmpty()) {
            ClientSecretPost(clientSecret)
        } else {
            NoClientAuthentication.INSTANCE
        }

        authService.performTokenRequest(
            response.createTokenExchangeRequest(),
            clientAuth
        ) { tokenResponse, exception ->
            authState.update(tokenResponse, exception)
            if (tokenResponse != null) {
                val token = tokenResponse.accessToken
                if (!token.isNullOrEmpty()) {
                    _oauthStateFlow.value = OAuthState.Success(token)
                } else {
                    _oauthStateFlow.value = OAuthState.Error("Received empty access token from GitHub")
                }
            } else {
                val authCode = response.authorizationCode
                if (!authCode.isNullOrEmpty()) {
                    exchangeCodeDirect(authCode)
                } else {
                    _oauthStateFlow.value = OAuthState.Error(
                        exception?.message ?: "Failed to exchange OAuth code"
                    )
                }
            }
        }
    }

    private fun exchangeCodeDirect(code: String) {
        _oauthStateFlow.value = OAuthState.Loading
        val clientId = GitHubOAuthConfig.getClientId(tokenManager)
        val clientSecret = GitHubOAuthConfig.getClientSecret(tokenManager)
        val redirectUri = GitHubOAuthConfig.getRedirectUri(tokenManager)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val formBodyBuilder = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("code", code)

                if (clientSecret.isNotEmpty()) {
                    formBodyBuilder.add("client_secret", clientSecret)
                }
                if (redirectUri.isNotEmpty()) {
                    formBodyBuilder.add("redirect_uri", redirectUri)
                }

                val request = Request.Builder()
                    .url(GitHubOAuthConfig.TOKEN_ENDPOINT)
                    .addHeader("Accept", "application/json")
                    .post(formBodyBuilder.build())
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    val moshi = Moshi.Builder().build()
                    val adapter = moshi.adapter(Map::class.java)
                    val map = adapter.fromJson(responseBody)
                    val token = map?.get("access_token") as? String

                    if (!token.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            _oauthStateFlow.value = OAuthState.Success(token)
                        }
                    } else {
                        val errDesc = (map?.get("error_description") as? String)
                            ?: (map?.get("error") as? String)
                            ?: "No access token returned"
                        withContext(Dispatchers.Main) {
                            _oauthStateFlow.value = OAuthState.Error("OAuth error: $errDesc")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _oauthStateFlow.value = OAuthState.Error("Token exchange failed (HTTP ${response.code})")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _oauthStateFlow.value = OAuthState.Error("Failed to exchange OAuth code: ${e.message}")
                }
            }
        }
    }
}

