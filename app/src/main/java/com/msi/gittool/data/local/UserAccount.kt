package com.msi.gittool.data.local

import com.squareup.moshi.JsonClass

enum class AuthType {
    PAT,    // Personal Access Token
    OAUTH,  // GitHub OAuth
    GUEST   // Local Guest Mode
}

@JsonClass(generateAdapter = true)
data class UserAccount(
    val username: String,
    val email: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val token: String? = null,
    val authType: AuthType = AuthType.PAT,
    val isActive: Boolean = false,
    val loginTimeMs: Long = System.currentTimeMillis()
)
