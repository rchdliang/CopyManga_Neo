package com.rchdliang.copymanga_neo.data.image.portable

import coil3.disk.DiskCache
import com.rchdliang.copymanga_neo.data.export.portable.imageExtension
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat

/** Windows/Linux desktop adapter. Sharing references are real absolute files, never synthetic URIs. */
class FileReaderImageIo(
    cacheDirectory: String,
    private val diskCache: DiskCache? = null,
    picturesDirectory: String = File(System.getProperty("user.home"), "Pictures").absolutePath,
) : ReaderImageIo {
    private val store = FileReaderImageStore(cacheDirectory)
    private val gallery = File(picturesDirectory, "CopyManga Neo")

    override suspend fun prunePrepared(beforeMillis: Long) = withContext(Dispatchers.IO) { store.prune(beforeMillis) }
    override suspend fun readCached(source: String, maximumBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        readCoilReaderImageCache(diskCache, source, maximumBytes)
    }
    override suspend fun readLocal(source: String, maximumBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        store.readLocal(source, maximumBytes)
    }
    override suspend fun inspect(bytes: ByteArray): ReaderImageMetadata = withContext(Dispatchers.IO) {
        val extension = imageExtension(bytes.copyOf(minOf(bytes.size, 64)))
        // Raw-byte export does not require a raster codec. HEIF/AVIF metadata is bounded ISO-BMFF
        // inspection; this does not claim that the desktop renderer can display either format.
        if (extension == "avif" || extension == "heic") return@withContext readerImageHeader(bytes)
        Data.makeFromBytes(bytes).use { data ->
            // Codec reads dimensions/format without readPixels(), avoiding long-image raster allocation.
            Codec.makeFromData(data).use { codec ->
                val mime = when (codec.encodedImageFormat) {
                    EncodedImageFormat.JPEG -> "image/jpeg"
                    EncodedImageFormat.PNG -> "image/png"
                    EncodedImageFormat.GIF -> "image/gif"
                    EncodedImageFormat.WEBP -> "image/webp"
                    EncodedImageFormat.HEIF -> if (imageExtension(bytes.copyOf(minOf(bytes.size, 64))) == "avif") "image/avif" else "image/heif"
                    else -> error("不支持导出此图片格式")
                }
                ReaderImageMetadata(mime, codec.width, codec.height)
            }
        }
    }
    override suspend fun publish(displayName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        store.publish(displayName, bytes)
    }
    override suspend fun readPrepared(image: ReaderExportImage, maximumBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        store.readPrepared(image, maximumBytes)
    }
    override suspend fun saveToGallery(image: ReaderExportImage, bytes: ByteArray) = withContext(Dispatchers.IO) {
        store.saveGallery(gallery, image, bytes)
    }
    override suspend fun saveToDocument(image: ReaderExportImage, destination: String, readValidatedBytes: suspend () -> ByteArray) = withContext(Dispatchers.IO) {
        store.saveDocument(destination, readValidatedBytes())
    }
}
