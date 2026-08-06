package com.msi.gittool.git

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.msi.gittool.data.local.SecureTokenStorage

class SecureTokenManager(private val context: Context) {

    private val masterKey = try {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    } catch (e: Exception) {
        null
    }

    private val encryptedPrefs = try {
        if (masterKey != null) {
            EncryptedSharedPreferences.create(
                context,
                "gittool_encrypted_tokens",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    private val fallbackStorage = try {
        SecureTokenStorage(context)
    } catch (e: Exception) {
        null
    }

    private val standardPrefs = context.getSharedPreferences("gittool_token_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_GITHUB_TOKEN = "secure_github_pat"
        private const val KEY_GITHUB_USERNAME = "secure_github_username"
    }

    fun saveToken(token: String) {
        val trimmed = token.trim()
        if (encryptedPrefs != null) {
            encryptedPrefs.edit().putString(KEY_GITHUB_TOKEN, trimmed).apply()
        } else {
            fallbackStorage?.saveToken(trimmed)
            standardPrefs.edit().putString(KEY_GITHUB_TOKEN, trimmed).apply()
        }
    }

    fun getToken(): String? {
        var token = encryptedPrefs?.getString(KEY_GITHUB_TOKEN, null)
        if (token.isNullOrEmpty()) {
            token = fallbackStorage?.getToken()
        }
        if (token.isNullOrEmpty()) {
            token = standardPrefs.getString(KEY_GITHUB_TOKEN, null)
        }
        return token?.takeIf { it.isNotBlank() }
    }

    fun saveUsername(username: String) {
        val trimmed = username.trim()
        if (encryptedPrefs != null) {
            encryptedPrefs.edit().putString(KEY_GITHUB_USERNAME, trimmed).apply()
        } else {
            standardPrefs.edit().putString(KEY_GITHUB_USERNAME, trimmed).apply()
        }
    }

    fun getUsername(): String? {
        var username = encryptedPrefs?.getString(KEY_GITHUB_USERNAME, null)
        if (username.isNullOrEmpty()) {
            username = standardPrefs.getString(KEY_GITHUB_USERNAME, null)
        }
        return username?.takeIf { it.isNotBlank() }
    }

    fun clearToken() {
        encryptedPrefs?.edit()?.remove(KEY_GITHUB_TOKEN)?.remove(KEY_GITHUB_USERNAME)?.apply()
        fallbackStorage?.clearToken()
        standardPrefs.edit().remove(KEY_GITHUB_TOKEN).remove(KEY_GITHUB_USERNAME).apply()
    }

    fun isValidToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val t = token.trim()
        return t.length >= 10 && (t.startsWith("ghp_") || t.startsWith("github_pat_") || t.startsWith("gho_") || t.length >= 30)
    }
}
