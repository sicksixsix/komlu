package com.komiklu.app.di

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.komiklu.app.data.local.dao.ComicCacheDao
import com.komiklu.app.data.local.dao.FavoriteDao
import com.komiklu.app.data.local.dao.ReadHistoryDao
import com.komiklu.app.data.model.ComicCacheEntity
import com.komiklu.app.data.model.FavoriteEntity
import com.komiklu.app.data.model.ReadHistoryEntity
import com.komiklu.app.data.session.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

// ─── AUTH INTERCEPTOR ─────────────────────────────────────────────────────────

class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionManager.getAccessToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

// ─── ROOM DATABASE ────────────────────────────────────────────────────────────

@Database(
    entities = [
        FavoriteEntity::class,
        ReadHistoryEntity::class,
        ComicCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KomikluDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun readHistoryDao(): ReadHistoryDao
    abstract fun comicCacheDao(): ComicCacheDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): KomikluDatabase =
        Room.databaseBuilder(ctx, KomikluDatabase::class.java, "komiklu.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideFavoriteDao(db: KomikluDatabase) = db.favoriteDao()
    @Provides fun provideHistoryDao(db: KomikluDatabase) = db.readHistoryDao()
    @Provides fun provideCacheDao(db: KomikluDatabase) = db.comicCacheDao()
}
