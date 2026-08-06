package com.msi.gittool.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GitToolDao {

    // --- Bookmarks ---
    @Query("SELECT * FROM bookmarks ORDER BY name ASC")
    fun getBookmarksFlow(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY name ASC")
    suspend fun getBookmarks(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE id = :id)")
    fun isBookmarkedFlow(id: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE id = :id)")
    suspend fun isBookmarked(id: Long): Boolean


    // --- Cached Repos ---
    @Query("SELECT * FROM cached_repos WHERE isPrivateList = :isPrivate ORDER BY name ASC")
    suspend fun getCachedRepos(isPrivate: Boolean): List<CachedRepoEntity>

    // --- Cached Users ---
    @Query("SELECT * FROM cached_users WHERE login = :login")
    suspend fun getCachedUser(login: String): CachedUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedUser(user: CachedUserEntity)

    @Query("DELETE FROM cached_users WHERE login = :login")
    suspend fun clearCachedUser(login: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedRepos(repos: List<CachedRepoEntity>)

    @Query("DELETE FROM cached_repos WHERE isPrivateList = :isPrivate")
    suspend fun clearCachedRepos(isPrivate: Boolean)

    @Query("DELETE FROM cached_repos WHERE fullName = :fullName")
    suspend fun deleteCachedRepoByName(fullName: String)


    // --- Cached Files & Directories ---
    @Query("SELECT * FROM cached_files WHERE fullName = :fullName AND parentPath = :parentPath ORDER BY type DESC, name ASC")
    suspend fun getCachedDirectoryContents(fullName: String, parentPath: String): List<CachedFileEntity>

    @Query("SELECT * FROM cached_files WHERE pathId = :pathId")
    suspend fun getCachedFile(pathId: String): CachedFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedFiles(files: List<CachedFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedFile(file: CachedFileEntity)

    @Query("DELETE FROM cached_files WHERE fullName = :fullName")
    suspend fun clearCachedFilesForRepo(fullName: String)

    // --- Recent Searches ---
    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT 15")
    fun getRecentSearchesFlow(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE query = :query")
    suspend fun deleteRecentSearch(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearAllRecentSearches()

    // --- Activity & Notification Logs ---
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getActivityLogsFlow(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    suspend fun getActivityLogs(): List<ActivityLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLogEntity): Long

    @Query("DELETE FROM activity_logs WHERE id = :id")
    suspend fun deleteActivityLogById(id: Long)

    @Query("DELETE FROM activity_logs")
    suspend fun clearAllActivityLogs()

    @Query("UPDATE activity_logs SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllActivityLogsAsRead()

    @Query("SELECT COUNT(*) FROM activity_logs WHERE isRead = 0")
    fun getUnreadActivityLogsCountFlow(): Flow<Int>
}
