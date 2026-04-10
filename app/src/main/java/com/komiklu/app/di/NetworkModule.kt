package com.komiklu.app.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.komiklu.app.BuildConfig
import com.komiklu.app.data.remote.AuthApiService
import com.komiklu.app.data.remote.KomikluApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val CDN_CACHE_SIZE   = 50L * 1024 * 1024   // 50 MB untuk JSON
    private const val IMAGE_CACHE_SIZE = 200L * 1024 * 1024  // 200 MB disk image cache
    private const val IMAGE_MEM_CACHE  = 50L * 1024 * 1024   // 50 MB memory image cache

    @Provides
    @Singleton
    fun provideOkHttpCache(@ApplicationContext ctx: Context): Cache =
        Cache(ctx.cacheDir.resolve("http_cache"), CDN_CACHE_SIZE)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cache: Cache,
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.HEADERS
            else
                HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .cache(cache)
            // Cache JSON CDN selama 5 menit (stale-while-revalidate)
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=300, stale-while-revalidate=600")
                    .build()
            }
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("cdn")
    fun provideCdnRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    @Named("api")
    fun provideApiRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideKomikluApiService(@Named("cdn") retrofit: Retrofit): KomikluApiService =
        retrofit.create(KomikluApiService::class.java)

    @Provides
    @Singleton
    fun provideAuthApiService(@Named("api") retrofit: Retrofit): AuthApiService =
        retrofit.create(AuthApiService::class.java)

    /**
     * Coil ImageLoader dengan:
     * - Disk cache 200MB (CDN images)
     * - Memory cache 50MB
     * - Crossfade animation
     * - Retry on failure
     */
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext ctx: Context): ImageLoader =
        ImageLoader.Builder(ctx)
            .memoryCache {
                MemoryCache.Builder(ctx)
                    .maxSizeBytes(IMAGE_MEM_CACHE)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(ctx.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(IMAGE_CACHE_SIZE)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .crossfade(200)
            .respectCacheHeaders(false) // pakai disk cache sendiri
            .build()
}
