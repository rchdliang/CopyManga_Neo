package com.rchdliang.copymanga_neo.data.image.portable

import com.rchdliang.copymanga_neo.data.export.portable.imageExtension
import com.rchdliang.copymanga_neo.data.remote.portable.CopyMangaClient
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.domain.repository.ReaderImageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Native metadata only: implementations must not allocate a full decoded long-comic bitmap. */
data class ReaderImageMetadata(val mimeType: String, val width: Int, val height: Int)

/** Explicit native file/document capabilities; unsupported operations must throw, never report success. */
interface ReaderImageIo {
    /** Remove only prepared-export files older than the cutoff, retaining files for share recipients. */
    suspend fun prunePrepared(beforeMillis: Long)
    /** null means a genuine cache miss, not a corrupt/unreadable cache entry. */
    suspend fun readCached(source: String, maximumBytes: Int): ByteArray?
    suspend fun readLocal(source: String, maximumBytes: Int): ByteArray
    suspend fun inspect(bytes: ByteArray): ReaderImageMetadata
    /** Commit atomically in the app's private export directory and return a native shareable reference. */
    suspend fun publish(displayName: String, bytes: ByteArray): String
    /** Confine references to the prepared-export directory, including after saved-state restoration. */
    suspend fun readPrepared(image: ReaderExportImage, maximumBytes: Int): ByteArray
    suspend fun saveToGallery(image: ReaderExportImage, bytes: ByteArray)
    /** Destination was selected explicitly by the native picker. Read/validate within rollback scope so
     * a newly created document is removed even if a restored prepared image has disappeared. */
    suspend fun saveToDocument(image: ReaderExportImage, destination: String, readValidatedBytes: suspend () -> ByteArray)
}

fun interface ReaderImageTransport {
    suspend fun read(source: String, maximumBytes: Int): ByteArray
}

/** Uses the same proxy, session-generation cancellation and site risk gate as ordinary API traffic. */
class KtorReaderImageTransport(private val client: CopyMangaClient) : ReaderImageTransport {
    override suspend fun read(source: String, maximumBytes: Int): ByteArray =
        client.resource(source, maximumBytes, allowHttp = true)
}

/**
 * Port of CopyManga_Neo ReaderImageRepositoryImpl at 466cef7c6acd2f082a9f7432cc06a082d83bc1c9.
 * Keeps original encoded bytes, cache-first reads, six format families, names and two-day share leases.
 * The portable boundary additionally caps encoded payloads at 64 MiB before any native image probe.
 */
class PortableReaderImageRepository(
    private val transport: ReaderImageTransport,
    private val io: ReaderImageIo,
    private val maximumImageBytes: Int = DEFAULT_MAXIMUM_IMAGE_BYTES,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newId: () -> String = { Uuid.random().toString() },
) : ReaderImageRepository {
    init { require(maximumImageBytes > 0) }

    override suspend fun prepare(source: String, title: String): Result<ReaderExportImage> = result {
        require(source.isNotBlank()) { "没有可导出的图片" }
        io.prunePrepared(nowMillis() - SHARE_RETENTION_MILLIS)
        val bytes = if (source.startsWith("https://") || source.startsWith("http://")) {
            io.readCached(source, maximumImageBytes) ?: transport.read(source, maximumImageBytes)
        } else io.readLocal(source, maximumImageBytes)
        val metadata = validate(bytes)
        val extension = readerImageExtension(metadata.mimeType)
        val id = newId().take(8)
        require(id.isNotBlank() && id.all { it.isLetterOrDigit() || it == '-' }) { "图片文件标识无效" }
        val name = "${readerImageSafeTitle(title)}_$id.$extension"
        currentCoroutineContext().ensureActive()
        val location = io.publish(name, bytes)
        require(location.isNotBlank()) { "图片临时文件准备失败" }
        ReaderExportImage(location, metadata.mimeType, name)
    }

    override suspend fun saveToGallery(image: ReaderExportImage): Result<Unit> = result {
        io.saveToGallery(image, preparedBytes(image))
    }

    override suspend fun saveToDocument(image: ReaderExportImage, destination: String): Result<Unit> = result {
        require(destination.isNotBlank()) { "无法写入所选位置" }
        io.saveToDocument(image, destination) { preparedBytes(image) }
    }

    private suspend fun preparedBytes(image: ReaderExportImage): ByteArray {
        require(image.displayName.isNotBlank() && image.displayName.none { it in "\\/:*?\"<>|" || it.code < 32 || it.code == 127 }) {
            "图片文件名无效"
        }
        val bytes = io.readPrepared(image, maximumImageBytes)
        val metadata = validate(bytes)
        require(metadata.mimeType == image.mimeType) { "图片格式已变化，请重新选择图片" }
        require(image.displayName.endsWith(".${readerImageExtension(metadata.mimeType)}")) { "图片扩展名无效" }
        return bytes
    }

    private suspend fun validate(bytes: ByteArray): ReaderImageMetadata {
        currentCoroutineContext().ensureActive()
        require(bytes.isNotEmpty()) { "图片内容无效" }
        require(bytes.size <= maximumImageBytes) { "图片超过单张安全大小限制" }
        val extension = imageExtension(bytes.copyOf(minOf(bytes.size, 64)))
        val metadata = io.inspect(bytes)
        require(metadata.width > 0 && metadata.height > 0) { "图片内容无效" }
        require(readerImageExtension(metadata.mimeType) == extension) { "图片格式与内容不符" }
        currentCoroutineContext().ensureActive()
        return metadata
    }

    private suspend fun <T> result(block: suspend () -> T): Result<T> = try {
        currentCoroutineContext().ensureActive()
        Result.success(block())
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        Result.failure(error)
    }

    companion object {
        const val DEFAULT_MAXIMUM_IMAGE_BYTES = 64 * 1024 * 1024
        const val SHARE_RETENTION_MILLIS = 2 * 24 * 60 * 60 * 1000L
    }
}

internal fun readerImageSafeTitle(title: String): String = buildString {
    title.forEach { append(if (it in "\\/:*?\"<>|" || it.code < 32 || it.code == 127) '_' else it) }
}.trim().take(80).ifBlank { "漫画图片" }

internal fun readerImageExtension(mimeType: String): String = when (mimeType) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "image/avif" -> "avif"
    "image/heif", "image/heic" -> "heic"
    else -> error("不支持导出此图片格式")
}
