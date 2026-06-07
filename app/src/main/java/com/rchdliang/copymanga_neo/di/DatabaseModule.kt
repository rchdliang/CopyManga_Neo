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
        ).build()
    }

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
}
