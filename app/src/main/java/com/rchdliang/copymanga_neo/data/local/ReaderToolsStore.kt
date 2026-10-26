package com.rchdliang.copymanga_neo.data.local

import android.annotation.SuppressLint
import android.content.Context
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.domain.repository.ReaderToolsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 阅读工具偏好串行提交；书签由统一的本地阅读仓库管理。 */
@Singleton
class ReaderToolsStore @Inject constructor(@ApplicationContext context: Context) :
    ReaderToolsRepository {
    private val prefs = context.getSharedPreferences("reader_tools", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableSettings = MutableStateFlow(
        ReaderToolsSettings(
            inlineComments = prefs.getBoolean("inline_comments", true),
            continuousChapters = prefs.getBoolean("continuous_chapters", true),
            volumeKeys = prefs.getBoolean("volume_keys", false),
            autoIntervalSeconds = prefs.getInt("auto_interval", 8),
            imageRetries = prefs.getInt("image_retries", 2),
            imageTimeoutSeconds = prefs.getInt("image_timeout", 15),
            preloadPages = prefs.getInt("preload_pages", 2),
            pageLayout = prefs.getString("page_layout", "SINGLE") ?: "SINGLE",
            pageAnimation = prefs.getBoolean("page_animation", true),
            sharpness = prefs.getFloat("sharpness", 0f),
            sharpnessRestore = prefs.getFloat("sharpness_restore", 0.20f),
            pairingRestarts = runCatching { ReaderToolsBackupCodec.decodePairing(prefs.getString("pairing_restarts", "{}") ?: "{}") }.getOrDefault(emptyMap())
        ).normalized()
    )
    override val settings = mutableSettings.asStateFlow()

    // KTX edit 不返回 commit 的成败，这里必须在写入成功后发布状态。
    @SuppressLint("UseKtx")
    override suspend fun updateSettings(transform: (ReaderToolsSettings) -> ReaderToolsSettings): Result<Unit> =
        result {
            mutex.withLock {
                val next = transform(mutableSettings.value).normalized()
                if (!prefs.edit().putBoolean("inline_comments", next.inlineComments)
                        .putBoolean("continuous_chapters", next.continuousChapters)
                        .putBoolean("volume_keys", next.volumeKeys)
                        .putInt("auto_interval", next.autoIntervalSeconds)
                        .putInt("image_retries", next.imageRetries)
                        .putInt("image_timeout", next.imageTimeoutSeconds)
                        .putInt("preload_pages", next.preloadPages)
                        .putString("page_layout", next.pageLayout)
                        .putBoolean("page_animation", next.pageAnimation)
                        .putFloat("sharpness", next.sharpness)
                        .putFloat("sharpness_restore", next.sharpnessRestore)
                        .putString("pairing_restarts", ReaderToolsBackupCodec.encodePairing(next.pairingRestarts)).commit()
                ) throw IOException("阅读设置保存失败")
                mutableSettings.value = next
            }
        }

    private suspend fun result(block: suspend () -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                block(); Result.success(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
}
