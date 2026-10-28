package com.rchdliang.copymanga_neo.multiplatform.app

import coil3.ImageLoader
import com.rchdliang.copymanga_neo.multiplatform.ui.downloads.DownloadNotificationPlatform
import com.rchdliang.copymanga_neo.domain.repository.*
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ReaderPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.novel.NovelReaderPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.export.ComicExportPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.profile.ProfilePlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.profile.AppBuildInfo

/** All screens consume the original contracts; platform hosts must supply real implementations. */
data class AppRepositories(
    val manga: MangaRepository,
    val discovery: ComicDiscoveryRepository,
    val novels: NovelRepository,
    val novelShelf: NovelShelfRepository,
    val users: UserRepository,
    val sessions: ReadingSessionRepository,
    val downloads: DownloadRepository,
    val comments: CommentRepository,
    val shares: ShareLinkRepository,
    val localReading: LocalReadingRepository,
    val readingProgress: ReadingProgressRepository,
    val appSettings: AppSettingsRepository,
    val readerPreferences: ReaderPreferencesRepository,
    val readerTools: ReaderToolsRepository,
    val novelPreferences: NovelReaderPreferencesRepository,
    val annotations: NovelAnnotationRepository,
    val blacklist: ThemeBlacklistRepository,
    val network: NetworkSettingsRepository,
    val risk: RiskControlRepository,
    val storage: StorageRepository,
    val backup: SettingsBackupRepository,
    val networkLog: NetworkLogRepository,
    val images: ReaderImageRepository,
    val export: ComicExportRepository,
    val novelImagePositionDebug: com.rchdliang.copymanga_neo.domain.reading.NovelImagePositionDebugStore? = null,
    val startBackground: () -> Unit = {},
    val comicMetadata: ComicMetadataRepository,
    val novelIllustrations: NovelIllustrationExportRepository,
    val chineseDictionary: ChineseDictionaryRepository,
    val appInfo: AppInfoRepository,
    val shelfReading: ShelfReadingRepository,
    val retryCloudReadingSync: () -> Unit = {},
    val startupTrace: StartupTrace = StartupTrace.None
)

data class AppPlatforms(
    val ui: UiPlatform,
    val reader: ReaderPlatform,
    val novel: NovelReaderPlatform,
    val export: ComicExportPlatform,
    val profile: ProfilePlatform,
    val imageLoader: (Int) -> ImageLoader,
    val notifications: DownloadNotificationPlatform? = null,
    val buildInfo: AppBuildInfo = portableReleaseBuildInfo
)
