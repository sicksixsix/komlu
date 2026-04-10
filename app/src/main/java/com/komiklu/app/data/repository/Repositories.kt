package com.komiklu.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.komiklu.app.BuildConfig
import com.komiklu.app.data.local.dao.ComicCacheDao
import com.komiklu.app.data.local.dao.FavoriteDao
import com.komiklu.app.data.local.dao.ReadHistoryDao
import com.komiklu.app.data.model.*
import com.komiklu.app.data.remote.AuthApiService
import com.komiklu.app.data.remote.KomikluApiService
import com.komiklu.app.data.remote.LoginRequest
import com.komiklu.app.data.remote.RegisterRequest
import com.komiklu.app.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComicRepository @Inject constructor(
    private val api: KomikluApiService,
    private val favoriteDao: FavoriteDao,
    private val cacheDao: ComicCacheDao,
    private val gson: Gson
) {
    private val imageCdn = BuildConfig.IMAGE_CDN_URL

    /**
     * Fetch comics.json dari CDN.
     * Strategi: cache-first, network jika cache expired (> 1 jam).
     */
    suspend fun getAllComics(): Result<List<Comic>> = withContext(Dispatchers.IO) {
        try {
            // 1. Coba ambil dari Room cache dulu
            val cached = cacheDao.getCachedComicList()
            if (cached != null) {
                val type = object : TypeToken<List<ComicDto>>() {}.type
                val dtos = gson.fromJson<List<ComicDto>>(cached, type)
                return@withContext Result.success(dtos.map { it.toDomain(imageCdn) })
            }

            // 2. Fetch dari CDN
            val response = api.getAllComics()
            if (response.isSuccessful) {
                val dtos = response.body() ?: emptyList()

                // Simpan ke cache Room
                cacheDao.cacheComicList(
                    ComicCacheEntity(
                        title = "_all_comics_",
                        jsonData = gson.toJson(dtos)
                    )
                )

                Result.success(dtos.map { it.toDomain(imageCdn) })
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch chapters/[title].json
     * Title di-encode untuk URL-safe.
     */
    suspend fun getChapters(comicTitle: String): Result<List<Chapter>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(comicTitle, "UTF-8").replace("+", "%20")
            val url = "${BuildConfig.BASE_URL}chapters/$encoded.json"
            val response = api.getChapters(url)

            if (response.isSuccessful) {
                val dtos = response.body() ?: emptyList()
                Result.success(dtos.map { it.toDomain(comicTitle) })
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch index.json dari URL chapter.
     * Kembalikan list URL gambar lengkap dengan CDN param quality.
     */
    suspend fun getPages(chapterUrl: String, quality: ImageQuality): Result<List<MangaPage>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getPageList(chapterUrl)
                if (response.isSuccessful) {
                    val files = response.body() ?: emptyList()
                    val baseDir = chapterUrl.substringBeforeLast("/")
                    val pages = files.mapIndexed { index, file ->
                        MangaPage(
                            pageNumber = index + 1,
                            imageUrl = "$baseDir/$file?${quality.cdnParam}"
                        )
                    }
                    Result.success(pages)
                } else {
                    Result.failure(Exception("HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── FAVORITE ─────────────────────────────────────────────────────────────

    fun isFavorite(title: String): Flow<Boolean> = favoriteDao.isFavorite(title)
    fun getAllFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()
    fun getFavoriteCount(): Flow<Int> = favoriteDao.getFavoriteCount()

    suspend fun toggleFavorite(comic: Comic) {
        val isFav = favoriteDao.isFavorite(comic.title)
        // Ambil nilai saat ini (sekali, bukan Flow)
        withContext(Dispatchers.IO) {
            val entity = FavoriteEntity(
                comicTitle = comic.title,
                coverUrl = comic.coverUrl,
                author = comic.author,
                rating = comic.rating.toString(),
                status = comic.status,
                genres = gson.toJson(comic.genres)
            )
            favoriteDao.addFavorite(entity)
        }
    }

    suspend fun addFavorite(comic: Comic) = withContext(Dispatchers.IO) {
        favoriteDao.addFavorite(
            FavoriteEntity(
                comicTitle = comic.title,
                coverUrl = comic.coverUrl,
                author = comic.author,
                rating = comic.rating.toString(),
                status = comic.status,
                genres = gson.toJson(comic.genres)
            )
        )
    }

    suspend fun removeFavorite(title: String) = withContext(Dispatchers.IO) {
        favoriteDao.removeFavorite(title)
    }
}

// ─── READ HISTORY REPOSITORY ──────────────────────────────────────────────────

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: ReadHistoryDao
) {
    fun getRecentHistory(limit: Int = 20) = historyDao.getRecentHistory(limit)
    fun getHistoryCount() = historyDao.getHistoryCount()

    suspend fun saveProgress(
        comicTitle: String,
        chapter: String,
        chapterNumber: Int,
        coverUrl: String,
        page: Int,
        totalPages: Int
    ) {
        val id = "${comicTitle}_${chapter}"
        historyDao.upsertHistory(
            ReadHistoryEntity(
                id = id,
                comicTitle = comicTitle,
                chapter = chapter,
                chapterNumber = chapterNumber,
                coverUrl = coverUrl,
                lastPage = page,
                totalPages = totalPages
            )
        )
    }

    suspend fun getLastRead(comicTitle: String) =
        historyDao.getLastReadChapter(comicTitle)
}

// ─── AUTH REPOSITORY ──────────────────────────────────────────────────────────

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApiService,
    private val sessionManager: SessionManager
) {
    fun isLoggedIn() = sessionManager.isLoggedIn()
    fun getCurrentUser() = sessionManager.getUser()

    suspend fun login(email: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.login(LoginRequest(email, password))
                if (response.isSuccessful) {
                    val body = response.body()!!
                    sessionManager.saveSession(body)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Email atau password salah"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun register(username: String, email: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.register(RegisterRequest(username, email, password))
                if (response.isSuccessful) {
                    val body = response.body()!!
                    sessionManager.saveSession(body)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Registrasi gagal. Coba lagi."))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun logout() = sessionManager.clearSession()
}

// ─── MAPPERS ──────────────────────────────────────────────────────────────────

fun ComicDto.toDomain(imageCdnBase: String): Comic {
    val resolvedCover = if (cover.startsWith("http")) cover
                        else "$imageCdnBase$cover"
    return Comic(
        title = title,
        author = author,
        year = year,
        desc = desc,
        coverUrl = resolvedCover,
        genres = genres,
        status = status,
        rating = rating.toFloatOrNull() ?: 0f,
        isProject = project == "1",
        isRekomen = rekomendasi == "1",
        viewCount = view
    )
}

fun ChapterDto.toDomain(comicTitle: String): Chapter {
    val num = chapter.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
    return Chapter(
        comicTitle = comicTitle,
        chapter = chapter,
        chapterNumber = num,
        url = url
    )
}
