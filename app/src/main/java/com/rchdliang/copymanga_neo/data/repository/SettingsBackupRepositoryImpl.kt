package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.local.LocalReadingCodec
import com.rchdliang.copymanga_neo.data.local.LocalReadingStore
import com.rchdliang.copymanga_neo.data.local.ReaderToolsBackupCodec
import com.rchdliang.copymanga_neo.data.local.SettingsBackupCodec
import com.rchdliang.copymanga_neo.domain.model.SettingsBackup
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import com.rchdliang.copymanga_neo.domain.repository.ReaderPreferencesRepository
import com.rchdliang.copymanga_neo.domain.repository.ReaderToolsRepository
import com.rchdliang.copymanga_neo.domain.repository.SettingsBackupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsBackupRepositoryImpl @Inject constructor(
    private val settings: AppSettingsRepository,
    private val reader: ReaderPreferencesRepository,
    private val localReading: LocalReadingStore,
    private val tools: ReaderToolsRepository
) : SettingsBackupRepository {
    private val mutex = Mutex()

    override suspend fun exportSettings(): Result<String> = mutex.withLock {
        result { SettingsBackupCodec.encode(snapshot()) }
    }

    override suspend fun importSettings(serialized: String): Result<Unit> = mutex.withLock {
        result {
            val incoming = SettingsBackupCodec.decode(serialized)
            val previous = snapshot()
            val incomingTools = ReaderToolsBackupCodec.decode(incoming.readerTools)
            val previousTools = ReaderToolsBackupCodec.decode(previous.readerTools)
            // Once a validated import starts, navigation cannot leave a half-written import.
            withContext(NonCancellable) {
                var comicChange: com.rchdliang.copymanga_neo.domain.model.ComicPreferencesChange? =
                    null
                try {
                    applyPreferences(incoming)
                    comicChange = reader.mergeComicPreferences(incoming.comicPreferences)
                        .fold({ it }, { throw it })
                    tools.updateSettings { incomingTools }.fold({}, { throw it })
                    // This atomic merge is last; no later persistence operation can fail.
                    localReading.merge(LocalReadingCodec.decode(incoming.localReading))
                        .fold({}, { throw it })
                } catch (error: Exception) {
                    val rollback = result {
                        applyPreferences(previous)
                        comicChange?.let {
                            reader.rollbackComicPreferences(it).fold({}, { throw it })
                        }
                        tools.updateSettings { previousTools }.fold({}, { throw it })
                    }
                    rollback.fold(
                        onSuccess = { throw IllegalStateException("导入失败，已恢复原设置", error) },
                        onFailure = {
                            throw IllegalStateException(
                                "导入未完成，请检查设置后重试",
                                error
                            )
                        }
                    )
                }
            }
        }
    }

    private suspend fun snapshot(): SettingsBackup {
        localReading.flush().fold({}, { throw it })
        localReading.error.value?.let { error(it) }
        return SettingsBackup(
            settings.settings.first(), reader.globalPreferences.first(),
            settings.getHomeRankPreferences().fold({ it }, { throw it }),
            LocalReadingCodec.encode(localReading.snapshot.value),
            ReaderToolsBackupCodec.encode(tools.settings.value),
            reader.exportComicPreferences().fold({ it }, { throw it })
        )
    }

    private suspend fun applyPreferences(value: SettingsBackup) {
        settings.setThemeMode(value.settings.themeMode).fold({}, { throw it })
        settings.setWifiOnlyDownloads(value.settings.wifiOnlyDownloads).fold({}, { throw it })
        settings.setBlurNovelIllustrationPreviews(value.settings.blurNovelIllustrationPreviews)
            .fold({}, { throw it })
        settings.setComicImageConcurrency(value.settings.comicImageConcurrency)
            .fold({}, { throw it })
        settings.setComicDownloadLimitKiB(value.settings.comicDownloadLimitKiB)
            .fold({}, { throw it })
        settings.setHomeRankPreferences(value.homeRank).fold({}, { throw it })
        reader.updateGlobal { value.readerDefaults }.fold({}, { throw it })

    }

    private suspend fun <T> result(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
