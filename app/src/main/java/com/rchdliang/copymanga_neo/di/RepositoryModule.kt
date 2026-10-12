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

// Hilt 通过生成的组件安装此模块及绑定，源码没有直接调用。
@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindLocalReadingRepository(
        implementation: com.rchdliang.copymanga_neo.data.local.LocalReadingStore
    ): com.rchdliang.copymanga_neo.domain.repository.LocalReadingRepository

    @Binds
    @Singleton
    abstract fun bindReadingSessionRepository(
        implementation: com.rchdliang.copymanga_neo.data.local.AuthDataStore
    ): com.rchdliang.copymanga_neo.domain.repository.ReadingSessionRepository

    @Binds
    @Singleton
    abstract fun bindNovelRepository(
        implementation: com.rchdliang.copymanga_neo.data.novel.OfficialNovelRepository
    ): com.rchdliang.copymanga_neo.domain.repository.NovelRepository

    @Binds
    @Singleton
    abstract fun bindNovelShelfRepository(
        implementation: com.rchdliang.copymanga_neo.data.repository.NovelShelfRepositoryImpl
    ): com.rchdliang.copymanga_neo.domain.repository.NovelShelfRepository

    @Binds
    @Singleton
    abstract fun bindComicExportRepository(
        implementation: com.rchdliang.copymanga_neo.data.repository.ComicExportRepositoryImpl
    ): com.rchdliang.copymanga_neo.domain.repository.ComicExportRepository

    @Binds
    @Singleton
    abstract fun bindShareLinkRepository(
        implementation: com.rchdliang.copymanga_neo.data.repository.ShareLinkRepositoryImpl
    ): com.rchdliang.copymanga_neo.domain.repository.ShareLinkRepository

    @Binds
    @Singleton
    abstract fun bindReaderToolsRepository(
        implementation: com.rchdliang.copymanga_neo.data.local.ReaderToolsStore
    ): com.rchdliang.copymanga_neo.domain.repository.ReaderToolsRepository

    @Binds
    @Singleton
    abstract fun bindReaderImageRepository(
        implementation: com.rchdliang.copymanga_neo.data.repository.ReaderImageRepositoryImpl
    ): com.rchdliang.copymanga_neo.domain.repository.ReaderImageRepository

    @Binds
    @Singleton
    abstract fun bindSettingsBackupRepository(
        implementation: com.rchdliang.copymanga_neo.data.repository.SettingsBackupRepositoryImpl
    ): com.rchdliang.copymanga_neo.domain.repository.SettingsBackupRepository

    @Binds
    @Singleton
    abstract fun bindNetworkLogRepository(
        implementation: com.rchdliang.copymanga_neo.data.local.LocalNetworkLogStore
    ): com.rchdliang.copymanga_neo.domain.repository.NetworkLogRepository

    @Binds
    @Singleton
    abstract fun bindComicDiscoveryRepository(
        implementation: com.rchdliang.copymanga_neo.data.repository.ComicDiscoveryRepositoryImpl
    ): com.rchdliang.copymanga_neo.domain.repository.ComicDiscoveryRepository

    @Binds
    @Singleton
    abstract fun bindCommentRepository(
        implementation: com.rchdliang.copymanga_neo.data.repository.CommentRepositoryImpl
    ): com.rchdliang.copymanga_neo.domain.repository.CommentRepository

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
