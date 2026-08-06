package com.msi.gittool.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {
    private val secureTokenStorage = try {
        com.msi.gittool.data.local.SecureTokenStorage(context)
    } catch (e: Throwable) {
        null
    }

    private val sharedPrefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "gittool_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Throwable) {
        context.getSharedPreferences("gittool_standard_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "github_access_token"
        private const val KEY_USERNAME = "github_username"
        private const val KEY_REMEMBER_ME = "github_remember_me"
        private const val KEY_OAUTH_CLIENT_ID = "github_oauth_client_id"
        private const val KEY_OAUTH_CLIENT_SECRET = "github_oauth_client_secret"
        private const val KEY_OAUTH_REDIRECT_URI = "github_oauth_redirect_uri"
        private const val KEY_IS_MOCK_LOGIN = "github_is_mock_login"
    }

    fun saveIsMockLogin(isMock: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_IS_MOCK_LOGIN, isMock).apply()
    }

    fun isMockLogin(): Boolean {
        return sharedPrefs.getBoolean(KEY_IS_MOCK_LOGIN, false)
    }

    fun registerLocalUser(username: String, secretPass: String): Boolean {
        val savedPass = sharedPrefs.getString("local_user_pass_$username", null)
        if (savedPass == null) {
            sharedPrefs.edit().putString("local_user_pass_$username", secretPass).apply()
            return true
        } else {
            return savedPass == secretPass
        }
    }

    fun getLocalUserReposJson(username: String): String? {
        return sharedPrefs.getString("local_user_repos_$username", null)
    }

    fun saveLocalUserReposJson(username: String, json: String) {
        sharedPrefs.edit().putString("local_user_repos_$username", json).apply()
    }

    fun saveOAuthClientId(clientId: String?) {
        sharedPrefs.edit().putString(KEY_OAUTH_CLIENT_ID, clientId).apply()
    }

    fun getOAuthClientId(): String? {
        return sharedPrefs.getString(KEY_OAUTH_CLIENT_ID, null)
    }

    fun saveOAuthClientSecret(clientSecret: String?) {
        sharedPrefs.edit().putString(KEY_OAUTH_CLIENT_SECRET, clientSecret).apply()
    }

    fun getOAuthClientSecret(): String? {
        return sharedPrefs.getString(KEY_OAUTH_CLIENT_SECRET, null)
    }

    fun saveOAuthRedirectUri(redirectUri: String?) {
        sharedPrefs.edit().putString(KEY_OAUTH_REDIRECT_URI, redirectUri).apply()
    }

    fun getOAuthRedirectUri(): String? {
        return sharedPrefs.getString(KEY_OAUTH_REDIRECT_URI, null)
    }

    fun saveAccessToken(token: String?) {
        if (token != null) {
            try {
                secureTokenStorage?.saveToken(token)
            } catch (e: Exception) {
                // Ignore backup failures
            }
        } else {
            try {
                secureTokenStorage?.clearToken()
            } catch (e: Exception) {
                // Ignore backup failures
            }
        }
        sharedPrefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun getAccessToken(): String? {
        val secure = try {
            secureTokenStorage?.getToken()
        } catch (e: Exception) {
            null
        }
        return secure ?: sharedPrefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun saveUsername(username: String?) {
        sharedPrefs.edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(): String? {
        return sharedPrefs.getString(KEY_USERNAME, null)
    }

    fun saveRememberMe(remember: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_REMEMBER_ME, remember).apply()
    }

    fun isRememberMe(): Boolean {
        return sharedPrefs.getBoolean(KEY_REMEMBER_ME, false)
    }

    fun clear() {
        try {
            secureTokenStorage?.clearToken()
        } catch (e: Exception) {
            // Ignore format exceptions on legacy keys
        }
        sharedPrefs.edit().clear().apply()
    }
}
