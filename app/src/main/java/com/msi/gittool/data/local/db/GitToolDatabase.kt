package com.msi.gittool.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookmarkEntity::class,
        CachedRepoEntity::class,
        CachedFileEntity::class,
        CachedUserEntity::class,
        RecentSearchEntity::class,
        ActivityLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class GitToolDatabase : RoomDatabase() {
    abstract fun gitToolDao(): GitToolDao

    companion object {
        @Volatile
        private var Instance: GitToolDatabase? = null

        fun getDatabase(context: Context): GitToolDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    GitToolDatabase::class.java,
                    "git_tool_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { Instance = it }
            }
        }
    }
}
