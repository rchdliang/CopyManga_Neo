package com.rchdliang.copymanga_neo.data.repository

import android.content.Context
import android.os.StatFs
import coil.imageLoader
import com.rchdliang.copymanga_neo.domain.model.StorageUsage
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.domain.repository.NovelRepository
import com.rchdliang.copymanga_neo.domain.repository.StorageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(coil.annotation.ExperimentalCoilApi::class)
class StorageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val novels: NovelRepository,
    private val downloads: DownloadRepository
) :
    StorageRepository {
    override suspend fun getUsage(): Result<StorageUsage> = result {
        val downloadRoot = File(context.filesDir, "downloads")
        val bytes = if (downloadRoot.exists()) downloadRoot.walkTopDown().sumOf { file ->
            currentCoroutineContext().ensureActive()
            if (file.isFile) file.length() else 0L
        } else 0L
        val novelUsage = novels.cacheUsage().fold({ it }, { throw it })
        StorageUsage(
            imageCacheBytes = context.imageLoader.diskCache?.size ?: 0L,
            downloadBytes = bytes,
            availableBytes = StatFs(context.filesDir.absolutePath).availableBytes,
            novelCacheBytes = novelUsage.bytes,
            novelCachedVolumes = novelUsage.volumes
        )
    }

    override suspend fun clearImageCache(): Result<Unit> = result {
        // 通过图片库清理，避免破坏正在使用的缓存索引；不触碰下载、账号或偏好文件。
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
    }

    override suspend fun clearNovelCache(): Result<Unit> = result {
        // 等待已有任务退出再清理，防止后台响应重新写回已删除的卷。
        downloads.cancelAllNovels()
        novels.clearCache().fold({}, { throw it })
    }

    private suspend fun <T> result(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
