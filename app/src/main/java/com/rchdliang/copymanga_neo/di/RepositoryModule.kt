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
    abstract fun bindNetworkSettingsRepository(
        implementation: com.rchdliang.copymanga_neo.data.repository.NetworkSettingsRepositoryImpl
    ): com.rchdliang.copymanga_neo.domain.repository.NetworkSettingsRepository
    @Binds
    @Singleton
    abstract fun bindRiskControlRepository(
        implementation: com.rchdliang.copymanga_neo.data.remote.RiskControlGate
    ): com.rchdliang.copymanga_neo.domain.repository.RiskControlRepository

    @Binds
    @Singleton
    abstract fun bindReadingProgressRepository(
        implementation: com.rchdliang.copymanga_neo.data.repository.ReadingProgressRepositoryImpl
    ): com.rchdliang.copymanga_neo.domain.repository.ReadingProgressRepository

    @Binds
    @Singleton
    abstract fun bindStorageRepository(
        implementation: com.rchdliang.copymanga_neo.data.repository.StorageRepositoryImpl
    ): com.rchdliang.copymanga_neo.domain.repository.StorageRepository

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(
        implementation: com.rchdliang.copymanga_neo.data.repository.AppSettingsRepositoryImpl
    ): com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository

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
