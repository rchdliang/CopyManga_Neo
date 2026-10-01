package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.local.SettingsBackupCodec
import com.rchdliang.copymanga_neo.domain.model.SettingsBackup
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import com.rchdliang.copymanga_neo.domain.repository.ReaderPreferencesRepository
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
    private val reader: ReaderPreferencesRepository
) : SettingsBackupRepository {
    private val mutex = Mutex()

    override suspend fun exportSettings(): Result<String> = mutex.withLock {
        result { SettingsBackupCodec.encode(snapshot()) }
    }

    override suspend fun importSettings(serialized: String): Result<Unit> = mutex.withLock {
        result {
            val incoming = SettingsBackupCodec.decode(serialized)
            val previous = snapshot()
            try {
                apply(incoming)
            } catch (error: Exception) {
                // 两个偏好仓库独立存储；写入失败或取消时恢复导入前的快照。
                val rollback = withContext(NonCancellable) { result { apply(previous) } }
                if (error is CancellationException) throw error
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

    private suspend fun snapshot() = SettingsBackup(
        settings.settings.first(),
        reader.globalPreferences.first(),
        settings.getHomeRankPreferences().fold(onSuccess = { it }, onFailure = { throw it })
    )

    private suspend fun apply(value: SettingsBackup) {
        settings.setThemeMode(value.settings.themeMode).fold({}, { throw it })
        settings.setWifiOnlyDownloads(value.settings.wifiOnlyDownloads).fold({}, { throw it })
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
