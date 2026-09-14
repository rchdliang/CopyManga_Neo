package com.rchdliang.copymanga_neo.di

import com.rchdliang.copymanga_neo.data.repository.DownloadRepositoryImpl
import com.rchdliang.copymanga_neo.data.repository.MangaRepositoryImpl
import com.rchdliang.copymanga_neo.data.repository.ThemeBlacklistRepositoryImpl
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.ThemeBlacklistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindThemeBlacklistRepository(
        implementation: ThemeBlacklistRepositoryImpl
    ): ThemeBlacklistRepository

    
    @Binds
    @Singleton
    abstract fun bindMangaRepository(
        mangaRepositoryImpl: MangaRepositoryImpl
    ): MangaRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(
        downloadRepositoryImpl: DownloadRepositoryImpl
    ): DownloadRepository
}