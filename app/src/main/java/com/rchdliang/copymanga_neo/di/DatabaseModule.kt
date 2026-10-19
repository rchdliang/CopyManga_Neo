package com.rchdliang.copymanga_neo.di

import android.content.Context
import androidx.room.Room
import com.rchdliang.copymanga_neo.data.local.db.AppDatabase
import com.rchdliang.copymanga_neo.data.local.db.DownloadedChapterDao
import com.rchdliang.copymanga_neo.data.local.db.DownloadedComicDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "copymanga_downloads.db"
        )
            .addMigrations(*AppDatabase.MIGRATIONS)
            // 首发后必须提供完整迁移链；缺失迁移时保留数据库，禁止静默重建。
            .build()
    }

    @Provides
    @Singleton
    fun provideComicCardCacheDao(db: AppDatabase): com.rchdliang.copymanga_neo.data.local.db.ComicCardCacheDao =
        db.comicCardCacheDao()

    @Provides
    @Singleton
    fun provideComicThemeCacheDao(db: AppDatabase): com.rchdliang.copymanga_neo.data.local.db.ComicThemeCacheDao =
        db.comicThemeCacheDao()

    @Provides
    @Singleton
    fun provideComicThemeCache(cache: com.rchdliang.copymanga_neo.data.local.PersistentComicThemeCache):
            com.rchdliang.copymanga_neo.data.local.ComicThemeCache = cache

    @Provides
    @Singleton
    fun provideDownloadQueueDao(db: AppDatabase): com.rchdliang.copymanga_neo.data.local.db.DownloadQueueDao =
        db.downloadQueueDao()

    @Provides
    @Singleton
    fun provideDownloadedComicDao(db: AppDatabase): DownloadedComicDao {
        return db.downloadedComicDao()
    }

    @Provides
    @Singleton
    fun provideDownloadedChapterDao(db: AppDatabase): DownloadedChapterDao {
        return db.downloadedChapterDao()
    }

    @Provides
    @Singleton
    fun provideDownloadedChapterCommentsDao(db: AppDatabase): com.rchdliang.copymanga_neo.data.local.db.DownloadedChapterCommentsDao =
        db.downloadedChapterCommentsDao()
}
