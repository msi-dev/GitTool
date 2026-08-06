package com.msi.gittool.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.*

interface GitHubApiService {

    @GET("user")
    suspend fun getCurrentUser(): GitHubUser

    @GET("user/repos")
    suspend fun getUserRepos(
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int,
        @Query("sort") sort: String = "updated"
    ): List<GitHubRepo>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRepo

    @POST("user/repos")
    suspend fun createRepo(
        @Body request: CreateRepoRequest
    ): GitHubRepo

    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateBlobRequest
    ): CreateBlobResponse

    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateTreeRequest
    ): CreateTreeResponse

    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateCommitRequest
    ): CreateCommitResponse

    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateReference(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Body request: UpdateRefRequest
    ): UpdateRefResponse

    @GET("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun getReference(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): RepoRefResponse

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createReference(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateRefRequest
    ): RepoRefResponse

    // --- Search ---
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("sort") sort: String? = null,
        @Query("order") order: String? = null,
        @Query("per_page") perPage: Int = 20
    ): GitHubSearchResponse<GitHubRepo>

    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("per_page") perPage: Int = 20
    ): GitHubSearchResponse<GitHubUser>

    // --- Contents & Files ---
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getRepoContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): List<GitHubContentItem>

    @GET("repos/{owner}/{repo}/contents")
    suspend fun getRepoRootContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitHubContentItem>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getRepoFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): GitHubFileContentResponse

    @GET("repos/{owner}/{repo}/languages")
    suspend fun getRepoLanguages(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Map<String, Long>

    // --- Forks & Imports ---
    @POST("repos/{owner}/{repo}/forks")
    suspend fun createFork(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRepo

    // --- User Profile ---
    @PATCH("user")
    suspend fun updateCurrentUser(
        @Body body: UpdateUserRequest
    ): GitHubUser

    // --- Notifications ---
    @GET("notifications")
    suspend fun getNotifications(
        @Query("all") all: Boolean = true,
        @Query("per_page") perPage: Int = 30
    ): List<GitHubNotification>

    @PATCH("notifications/threads/{id}")
    suspend fun markNotificationAsRead(
        @Path("id") id: String
    ): retrofit2.Response<Unit>

    @PUT("notifications")
    suspend fun markAllNotificationsAsRead(
        @Body body: MarkAllReadRequest = MarkAllReadRequest()
    ): retrofit2.Response<Unit>

    @DELETE("repos/{owner}/{repo}")
    suspend fun deleteRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): retrofit2.Response<Unit>

    @GET("users/{username}")
    suspend fun getUserDetails(
        @Path("username") username: String
    ): GitHubUser

    @GET("users/{username}/repos")
    suspend fun getUserReposList(
        @Path("username") username: String,
        @Query("per_page") perPage: Int = 100
    ): List<GitHubRepo>

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}

@JsonClass(generateAdapter = true)
data class CreateRefRequest(
    val ref: String,
    val sha: String
)
