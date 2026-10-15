package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.novel.portable.PortableNovelCache
import com.rchdliang.copymanga_neo.data.storage.CacheStorage
import com.rchdliang.copymanga_neo.domain.model.StorageUsage
import com.rchdliang.copymanga_neo.domain.repository.StorageRepository
import kotlinx.coroutines.CancellationException

/** Reports actual persisted bytes, including incomplete/corrupt files that still occupy storage. */
class PortableStorageRepository(
    private val storage: CacheStorage,
    private val novels: PortableNovelCache
) : StorageRepository {
    override suspend fun getUsage(): Result<StorageUsage> = attempt {
        val usage = novels.usage()
        StorageUsage(
            imageCacheBytes = storage.list("images").sumOf { storage.size(it) },
            downloadBytes = storage.list("downloads").sumOf { storage.size(it) },
            availableBytes = storage.availableBytes(),
            novelCacheBytes = usage.bytes,
            novelCachedVolumes = usage.volumes
        )
    }
    override suspend fun clearImageCache(): Result<Unit> = attempt { storage.remove("images") }
    override suspend fun clearNovelCache(): Result<Unit> = attempt { novels.clear() }
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancel: CancellationException) { throw cancel }
    catch (error: Exception) { Result.failure(error) }
}
