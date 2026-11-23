package dev.milkthe.qbitplan.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.milkthe.qbitplan.BuildConfig
import dev.milkthe.qbitplan.core.edupage.EdupageApi
import dev.milkthe.qbitplan.core.edupage.OkHttpEdupageApi
import dev.milkthe.qbitplan.data.local.AppDatabase
import dev.milkthe.qbitplan.data.local.CachedResponseDao
import dev.milkthe.qbitplan.data.local.ResponseCache
import dev.milkthe.qbitplan.data.local.RoomResponseCache
import okhttp3.OkHttpClient
import java.time.Clock
import java.time.Duration
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(30))
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "QbitPlan/${BuildConfig.VERSION_NAME} (Android; nieoficjalna apka)")
                    .build(),
            )
        }
        .build()

    @Provides
    @Singleton
    fun edupageApi(client: OkHttpClient): EdupageApi = OkHttpEdupageApi(client)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "qbitplan.db")
            // Only a cache, safe to drop on schema change.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun cachedResponseDao(db: AppDatabase): CachedResponseDao = db.cachedResponseDao()

    @Provides
    @Singleton
    fun preferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("user_preferences") }

    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun responseCache(impl: RoomResponseCache): ResponseCache
}
