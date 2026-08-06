package com.msi.gittool.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val fullName: String,
    val isPrivate: Boolean,
    val htmlUrl: String,
    val description: String?,
    val stargazersCount: Int,
    val forksCount: Int,
    val language: String?,
    val cloneUrl: String,
    val defaultBranch: String,
    val updatedAt: String? = null,
    val isFork: Boolean = false
)

@Entity(tableName = "cached_repos")
data class CachedRepoEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val fullName: String,
    val isPrivate: Boolean,
    val htmlUrl: String,
    val description: String?,
    val stargazersCount: Int,
    val forksCount: Int,
    val language: String?,
    val cloneUrl: String,
    val defaultBranch: String,
    val isPrivateList: Boolean, // differentiate cached public vs private main feeds
    val updatedAt: String? = null,
    val isFork: Boolean = false
)

@Entity(tableName = "cached_users")
data class CachedUserEntity(
    @PrimaryKey val login: String,
    val id: Long,
    val avatarUrl: String?,
    val name: String?,
    val htmlUrl: String?,
    val bio: String?,
    val blog: String?,
    val company: String?,
    val location: String?,
    val followers: Int,
    val following: Int,
    val publicRepos: Int
)

@Entity(tableName = "cached_files")
data class CachedFileEntity(
    @PrimaryKey val pathId: String, // Format: "fullName:path"
    val fullName: String, // owner/repo
    val path: String, // path e.g. "src/main/java"
    val parentPath: String, // path of the parent directory e.g. "src/main" or "" for root
    val name: String, // file or folder name
    val type: String, // "file" or "dir"
    val contentBase64: String?, // files content, null for dir
    val downloadUrl: String? = null // raw github user content url
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "IMPORT", "DELETE", "FORK", "SUCCESS", "FAILED", "DOWNLOAD", "UPLOAD", "COMMIT", "BOOKMARK"
    val title: String,
    val message: String,
    val details: String? = null,
    val status: String = "SUCCESS", // "SUCCESS", "FAILED", "INFO", "WARNING"
    val timestamp: Long = System.currentTimeMillis(),
    val repoName: String? = null,
    val isRead: Boolean = false
)
