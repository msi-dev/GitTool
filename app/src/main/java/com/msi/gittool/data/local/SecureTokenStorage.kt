package com.msi.gittool.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenStorage(context: Context) {
    private val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
    private val alias = "gittool_token_key"

    init {
        try {
            generateKeyIfNeeded()
        } catch (e: Exception) {
            // Under older hardware or emulator, Keystore generation might raise error
        }
    }

    private fun generateKeyIfNeeded() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!keyStore.containsAlias(alias)) {
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            )
            keyGen.generateKey()
        }
    }

    private fun getKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            ks.getKey(alias, null) as? SecretKey
        } catch (e: Exception) {
            null
        }
    }

    fun saveToken(token: String) {
        try {
            val key = getKey()
            if (key == null) {
                // Fallback to simple obfuscation if Keystore is completely unavailable
                prefs.edit().putString("token_simple", Base64.encodeToString(token.toByteArray(Charsets.UTF_8), Base64.DEFAULT)).apply()
                return
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            val bundle = Base64.encodeToString(iv, Base64.DEFAULT).trim() + ":" +
                    Base64.encodeToString(encrypted, Base64.DEFAULT).trim()
            prefs.edit().putString("token_encrypted", bundle).apply()
        } catch (e: Exception) {
            prefs.edit().putString("token_simple", Base64.encodeToString(token.toByteArray(Charsets.UTF_8), Base64.DEFAULT)).apply()
        }
    }

    fun getToken(): String? {
        try {
            val bundle = prefs.getString("token_encrypted", null)
            if (bundle != null) {
                val parts = bundle.split(":")
                if (parts.size == 2) {
                    val iv = Base64.decode(parts[0], Base64.DEFAULT)
                    val encrypted = Base64.decode(parts[1], Base64.DEFAULT)
                    val key = getKey() ?: return null
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                    return String(cipher.doFinal(encrypted), Charsets.UTF_8)
                }
            }
            val simple = prefs.getString("token_simple", null)
            if (simple != null) {
                return String(Base64.decode(simple, Base64.DEFAULT), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            // Return null if decryption fails (tampering/uninstall/etc.)
        }
        return null
    }

    fun clearToken() {
        prefs.edit().remove("token_encrypted").remove("token_simple").apply()
    }
}
