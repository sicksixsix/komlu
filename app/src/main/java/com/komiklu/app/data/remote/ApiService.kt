package com.komiklu.app.data.remote

import com.komiklu.app.data.model.ChapterDto
import com.komiklu.app.data.model.ComicDto
import com.komiklu.app.data.model.PageListDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Semua endpoint mengambil JSON statis dari CDN.
 * Tidak ada database langsung — sangat ringan di server.
 */
interface KomikluApiService {

    /** GET https://cdn.komiklu.com/comics.json */
    @GET("comics.json")
    suspend fun getAllComics(): Response<List<ComicDto>>

    /**
     * GET https://cdn.komiklu.com/chapters/Berserk.json
     * Karakter spesial di-encode otomatis oleh Retrofit.
     */
    @GET
    suspend fun getChapters(@Url url: String): Response<List<ChapterDto>>

    /**
     * GET https://cdn.komiklu.com/Manga/Berserk/Chapter 1/index.json
     * URL penuh dari field `url` di chapter JSON.
     */
    @GET
    suspend fun getPageList(@Url url: String): Response<PageListDto>
}

// ─── Auth API (lightweight — bisa pakai Firebase Auth / Supabase Auth) ────────

interface AuthApiService {

    @retrofit2.http.POST("auth/login")
    suspend fun login(
        @retrofit2.http.Body body: LoginRequest
    ): Response<AuthResponse>

    @retrofit2.http.POST("auth/register")
    suspend fun register(
        @retrofit2.http.Body body: RegisterRequest
    ): Response<AuthResponse>

    @retrofit2.http.POST("auth/refresh")
    suspend fun refreshToken(
        @retrofit2.http.Body body: RefreshRequest
    ): Response<AuthResponse>
}

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val username: String, val email: String, val password: String)
data class RefreshRequest(val refreshToken: String)
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse
)
data class UserResponse(
    val id: String,
    val username: String,
    val email: String,
    val avatarUrl: String?
)
