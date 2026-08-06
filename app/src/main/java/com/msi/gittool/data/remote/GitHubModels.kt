package com.msi.gittool.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUser(
    val login: String,
    val id: Long,
    val avatar_url: String?,
    val name: String?,
    val html_url: String?,
    val bio: String? = null,
    val blog: String? = null,
    val company: String? = null,
    val location: String? = null,
    val followers: Int? = 0,
    val following: Int? = 0,
    val public_repos: Int? = 0
)

@JsonClass(generateAdapter = true)
data class GitHubRepo(
    val id: Long,
    val name: String,
    val full_name: String,
    val private: Boolean,
    val html_url: String,
    val description: String?,
    val stargazers_count: Int,
    val forks_count: Int,
    val language: String?,
    val clone_url: String,
    val default_branch: String? = "main",
    val updated_at: String? = null,
    val fork: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequest(
    val name: String,
    val description: String?,
    val private: Boolean,
    val auto_init: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CreateBlobRequest(
    val content: String,
    val encoding: String = "base64"
)

@JsonClass(generateAdapter = true)
data class CreateBlobResponse(
    val sha: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class TreeEntry(
    val path: String,
    val mode: String = "100644", // normal file
    val type: String = "blob",
    val sha: String
)

@JsonClass(generateAdapter = true)
data class CreateTreeRequest(
    val base_tree: String? = null,
    val tree: List<TreeEntry>
)

@JsonClass(generateAdapter = true)
data class CreateTreeResponse(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class CreateCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String>
)

@JsonClass(generateAdapter = true)
data class CreateCommitResponse(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class UpdateRefRequest(
    val sha: String,
    val force: Boolean = true
)

@JsonClass(generateAdapter = true)
data class UpdateRefResponse(
    val ref: String,
    val url: String,
    val `object`: RefObject
)

@JsonClass(generateAdapter = true)
data class RefObject(
    val sha: String,
    val type: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class RepoRefResponse(
    val ref: String,
    val `object`: RefObject
)

@JsonClass(generateAdapter = true)
data class GitHubSearchResponse<T>(
    val total_count: Int,
    val incomplete_results: Boolean,
    val items: List<T>
)

@JsonClass(generateAdapter = true)
data class GitHubContentItem(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val url: String,
    val html_url: String?,
    val git_url: String?,
    val download_url: String?,
    val type: String
)

@JsonClass(generateAdapter = true)
data class GitHubFileContentResponse(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val url: String,
    val html_url: String?,
    val download_url: String?,
    val type: String,
    val content: String?,
    val encoding: String?
)

@JsonClass(generateAdapter = true)
data class UpdateUserRequest(
    val name: String? = null,
    val bio: String? = null,
    val blog: String? = null,
    val location: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubNotification(
    val id: String,
    val unread: Boolean,
    val reason: String,
    val updated_at: String,
    val subject: NotificationSubject,
    val repository: GitHubRepo
)

@JsonClass(generateAdapter = true)
data class NotificationSubject(
    val title: String,
    val url: String?,
    val latest_comment_url: String?,
    val type: String
)

@JsonClass(generateAdapter = true)
data class MarkAllReadRequest(
    val last_read_at: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    val tag_name: String,
    val name: String?,
    val body: String?,
    val html_url: String?
)


