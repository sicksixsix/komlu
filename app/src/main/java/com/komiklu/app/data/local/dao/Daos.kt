package com.komiklu.app.data.local.dao

import androidx.room.*
import com.komiklu.app.data.model.ComicCacheEntity
import com.komiklu.app.data.model.FavoriteEntity
import com.komiklu.app.data.model.ReadHistoryEntity
import kotlinx.coroutines.flow.Flow

// ─── FAVORITE DAO ─────────────────────────────────────────────────────────────

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE comicTitle = :title)")
    fun isFavorite(title: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE comicTitle = :title")
    suspend fun removeFavorite(title: String)

    @Query("SELECT COUNT(*) FROM favorites")
    fun getFavoriteCount(): Flow<Int>
}

// ─── READ HISTORY DAO ─────────────────────────────────────────────────────────

@Dao
interface ReadHistoryDao {

    @Query("SELECT * FROM read_history ORDER BY readAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 20): Flow<List<ReadHistoryEntity>>

    @Query("SELECT * FROM read_history WHERE comicTitle = :title ORDER BY chapterNumber DESC LIMIT 1")
    suspend fun getLastReadChapter(title: String): ReadHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: ReadHistoryEntity)

    @Query("UPDATE read_history SET lastPage = :page, readAt = :time WHERE id = :id")
    suspend fun updateProgress(id: String, page: Int, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM read_history WHERE readAt < :threshold")
    suspend fun pruneOldHistory(threshold: Long)

    @Query("SELECT COUNT(*) FROM read_history")
    fun getHistoryCount(): Flow<Int>
}

// ─── COMIC CACHE DAO ──────────────────────────────────────────────────────────

@Dao
interface ComicCacheDao {

    @Query("SELECT * FROM comic_cache WHERE title = :title")
    suspend fun getCachedComic(title: String): ComicCacheEntity?

    @Query("SELECT jsonData FROM comic_cache ORDER BY cachedAt DESC LIMIT 1")
    suspend fun getCachedComicList(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheComicList(entity: ComicCacheEntity)

    /** Hapus cache yang lebih dari 1 jam */
    @Query("DELETE FROM comic_cache WHERE cachedAt < :threshold")
    suspend fun pruneExpiredCache(threshold: Long = System.currentTimeMillis() - 3_600_000)
}
