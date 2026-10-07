package org.audimmory.mobile.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import org.audimmory.mobile.BuildConfig
import org.audimmory.mobile.data.download.clearDownloadedContent
import org.audimmory.mobile.data.local.BookDao
import org.audimmory.mobile.data.local.BookFacetDao
import org.audimmory.mobile.data.local.BookmarkDao
import org.audimmory.mobile.data.local.CachedLibraryDao
import org.audimmory.mobile.data.local.ChapterDao
import org.audimmory.mobile.data.local.CollectionDao
import org.audimmory.mobile.data.local.DownloadDao
import org.audimmory.mobile.data.local.AudimmoryDatabase
import org.audimmory.mobile.data.local.PlaybackHistoryDao
import org.audimmory.mobile.data.local.PlaylistDao
import org.audimmory.mobile.data.local.ProgressDao
import org.audimmory.mobile.data.local.SeriesDao
import org.audimmory.mobile.data.local.SessionStore
import org.audimmory.mobile.data.remote.AuthInterceptor
import org.audimmory.mobile.data.remote.BaseUrlInterceptor
import org.audimmory.mobile.data.remote.PagelessApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Provides
    @Singleton
    fun provideSessionStore(
        @ApplicationContext context: Context,
    ): SessionStore = SessionStore(context)

    @Provides
    @Singleton
    fun provideOkHttp(
        baseUrlInterceptor: BaseUrlInterceptor,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient {
        val builder =
            OkHttpClient
                .Builder()
                .addInterceptor(baseUrlInterceptor)
                .addInterceptor(authInterceptor)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit
            .Builder()
            // Placeholder base URL; BaseUrlInterceptor rewrites host at runtime.
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): PagelessApi = retrofit.create(PagelessApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AudimmoryDatabase =
        Room
            .databaseBuilder(context, AudimmoryDatabase::class.java, "audimmory.db")
            // Local cache is re-synced from the server, so a destructive
            // migration on schema changes is acceptable and simplest.
            // dropAllTables = true is Room's recommended value: it also clears
            // tables that stop being part of the schema, so renamed or removed
            // entities cannot leave obsolete rows behind.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(
                object : RoomDatabase.Callback() {
                    // A destructive migration drops the rows that name the
                    // downloaded .m4b files and cached covers, which would strand
                    // those files on disk: unreachable by the app, and impossible
                    // to reclaim from inside it. The download is re-creatable from
                    // the server; the orphan is not removable. Same reasoning as
                    // the sign-out path in AuthRepository.clearLocalContent.
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        clearDownloadedContent(context.filesDir)
                    }
                },
            ).build()

    @Provides fun provideBookDao(db: AudimmoryDatabase): BookDao = db.bookDao()

    @Provides fun provideBookFacetDao(db: AudimmoryDatabase): BookFacetDao = db.bookFacetDao()

    @Provides fun provideCachedLibraryDao(db: AudimmoryDatabase): CachedLibraryDao = db.cachedLibraryDao()

    @Provides fun provideChapterDao(db: AudimmoryDatabase): ChapterDao = db.chapterDao()

    @Provides fun provideProgressDao(db: AudimmoryDatabase): ProgressDao = db.progressDao()

    @Provides fun provideDownloadDao(db: AudimmoryDatabase): DownloadDao = db.downloadDao()

    @Provides fun provideBookmarkDao(db: AudimmoryDatabase): BookmarkDao = db.bookmarkDao()

    @Provides fun providePlaybackHistoryDao(db: AudimmoryDatabase): PlaybackHistoryDao = db.playbackHistoryDao()

    @Provides fun provideSeriesDao(db: AudimmoryDatabase): SeriesDao = db.seriesDao()

    @Provides fun provideCollectionDao(db: AudimmoryDatabase): CollectionDao = db.collectionDao()

    @Provides fun providePlaylistDao(db: AudimmoryDatabase): PlaylistDao = db.playlistDao()
}
