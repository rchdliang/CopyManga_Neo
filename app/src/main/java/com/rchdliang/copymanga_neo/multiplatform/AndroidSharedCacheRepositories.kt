package com.rchdliang.copymanga_neo.multiplatform

import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.domain.model.StorageUsage
import com.rchdliang.copymanga_neo.domain.repository.ReaderImageRepository
import com.rchdliang.copymanga_neo.domain.repository.StorageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Original MediaStore/SAF writer, with the shared renderer's original-byte cache consulted first. */
internal class AndroidSharedReaderImages(
    private val original: ReaderImageRepository,
    private val loaders: AndroidSharedImageLoaders
) : ReaderImageRepository {
    override suspend fun prepare(source: String, title: String): Result<ReaderExportImage> = withContext(Dispatchers.IO) {
        try {
            if (source.startsWith("https://") || source.startsWith("http://")) {
                loaders.openSnapshot(source)?.use { snapshot ->
                    // Hold the read-only snapshot until the original writer finishes its byte copy.
                    return@withContext original.prepare(snapshot.data.toString(), title)
                }
            }
            original.prepare(source, title)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { Result.failure(error) }
    }
    override suspend fun saveToGallery(image: ReaderExportImage) = original.saveToGallery(image)
    override suspend fun saveToDocument(image: ReaderExportImage, destination: String) = original.saveToDocument(image, destination)
}

/** Account for and clear both live image libraries without deleting either cache journal manually. */
internal class AndroidSharedStorage(
    private val original: StorageRepository,
    private val loaders: AndroidSharedImageLoaders
) : StorageRepository {
    override suspend fun getUsage(): Result<StorageUsage> = original.getUsage().map { usage ->
        usage.copy(imageCacheBytes = usage.imageCacheBytes + loaders.diskSize)
    }
    override suspend fun clearImageCache(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            original.clearImageCache().getOrThrow()
            loaders.clearCaches()
            Result.success(Unit)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { Result.failure(error) }
    }
    override suspend fun clearNovelCache() = original.clearNovelCache()
}
