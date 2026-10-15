package com.rchdliang.copymanga_neo.multiplatform

import com.rchdliang.copymanga_neo.domain.repository.*
import com.rchdliang.copymanga_neo.domain.reading.NovelImagePositionDebugStore
import com.rchdliang.copymanga_neo.multiplatform.app.AppRepositories
import javax.inject.Inject
import javax.inject.Singleton

/** Same Hilt repositories, Room history, Android services and account stores as the existing app. */
@Singleton
class AndroidSharedDependencies @Inject constructor(
    manga: MangaRepository,
    discovery: ComicDiscoveryRepository,
    novels: NovelRepository,
    novelShelf: NovelShelfRepository,
    users: UserRepository,
    sessions: ReadingSessionRepository,
    downloads: DownloadRepository,
    comments: CommentRepository,
    shares: ShareLinkRepository,
    localReading: LocalReadingRepository,
    readingProgress: ReadingProgressRepository,
    appSettings: AppSettingsRepository,
    readerPreferences: ReaderPreferencesRepository,
    readerTools: ReaderToolsRepository,
    novelPreferences: NovelReaderPreferencesRepository,
    annotations: NovelAnnotationRepository,
    blacklist: ThemeBlacklistRepository,
    network: NetworkSettingsRepository,
    risk: RiskControlRepository,
    storage: StorageRepository,
    backup: SettingsBackupRepository,
    networkLog: NetworkLogRepository,
    images: ReaderImageRepository,
    export: ComicExportRepository,
    debug: NovelImagePositionDebugStore,
    imageLoaders: AndroidSharedImageLoaders
) {
    val repositories = AppRepositories(
        manga, discovery, novels, novelShelf, users, sessions, downloads, comments, shares,
        localReading, readingProgress, appSettings, readerPreferences, readerTools,
        novelPreferences, annotations, blacklist, network, risk, AndroidSharedStorage(storage, imageLoaders), backup, networkLog,
        AndroidSharedReaderImages(images, imageLoaders), export, debug
    )
}
