package com.komiklu.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

// ─── NETWORK MODELS ──────────────────────────────────────────────────────────

/** Sesuai format comics.json */
data class ComicDto(
    @SerializedName("title")       val title: String,
    @SerializedName("author")      val author: String,
    @SerializedName("year")        val year: Int,
    @SerializedName("desc")        val desc: String,
    @SerializedName("cover")       val cover: String,
    @SerializedName("genres")      val genres: List<String>,
    @SerializedName("status")      val status: String,
    @SerializedName("rating")      val rating: String,
    @SerializedName("project")     val project: String,
    @SerializedName("rekomendasi") val rekomendasi: String,
    @SerializedName("view")        val view: Int
)

/** Sesuai format chapters/[title].json */
data class ChapterDto(
    @SerializedName("chapter") val chapter: String,
    @SerializedName("url")     val url: String
)

/** Sesuai format index.json */
typealias PageListDto = List<String>

// ─── DOMAIN MODELS ───────────────────────────────────────────────────────────

data class Comic(
    val title: String,
    val author: String,
    val year: Int,
    val desc: String,
    val coverUrl: String,   // sudah di-resolve dengan CDN base URL
    val genres: List<String>,
    val status: String,
    val rating: Float,
    val isProject: Boolean,
    val isRekomen: Boolean,
    val viewCount: Int,
    val isFavorite: Boolean = false
)

data class Chapter(
    val comicTitle: String,
    val chapter: String,      // e.g. "Chapter 1"
    val chapterNumber: Int,   // 1 (parsed dari string)
    val url: String           // index.json CDN URL
)

data class MangaPage(
    val pageNumber: Int,
    val imageUrl: String,     // CDN URL lengkap
    val isLoaded: Boolean = false
)

// ─── ROOM ENTITIES ───────────────────────────────────────────────────────────

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val comicTitle: String,
    val coverUrl: String,
    val author: String,
    val rating: String,
    val status: String,
    val genres: String,       // JSON string dari list
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "read_history")
data class ReadHistoryEntity(
    @PrimaryKey
    val id: String,           // "${comicTitle}_${chapter}"
    val comicTitle: String,
    val chapter: String,
    val chapterNumber: Int,
    val coverUrl: String,
    val lastPage: Int = 1,
    val totalPages: Int = 0,
    val readAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "comic_cache")
data class ComicCacheEntity(
    @PrimaryKey
    val title: String,
    val jsonData: String,     // serialized ComicDto
    val cachedAt: Long = System.currentTimeMillis()
)

// ─── UI STATES ───────────────────────────────────────────────────────────────

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
    object Empty : UiState<Nothing>()
}

data class HomeUiState(
    val bannerComics: List<Comic> = emptyList(),
    val projectComics: List<Comic> = emptyList(),
    val popularComics: List<Comic> = emptyList(),
    val latestChapters: List<LatestChapter> = emptyList(),
    val newestComics: List<Comic> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class LatestChapter(
    val comicTitle: String,
    val coverUrl: String,
    val chapter: String,
    val chapterUrl: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val isNew: Boolean = false
)

data class ReaderSettings(
    val readMode: ReadMode = ReadMode.VERTICAL,
    val imageQuality: ImageQuality = ImageQuality.HIGH,
    val autoNextChapter: Boolean = true,
    val keepScreenOn: Boolean = true
)

enum class ReadMode { VERTICAL, HORIZONTAL }
enum class ImageQuality(val cdnParam: String) {
    LOW("q=40&w=480"),
    MEDIUM("q=70&w=800"),
    HIGH("q=90&w=1200")
}
