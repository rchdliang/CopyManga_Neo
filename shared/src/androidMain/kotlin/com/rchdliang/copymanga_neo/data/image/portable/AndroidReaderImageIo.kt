package com.rchdliang.copymanga_neo.data.image.portable

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import coil3.disk.DiskCache
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Extracted from the original Android ReaderImageRepositoryImpl; preserves MediaStore/SAF rollback. */
class AndroidReaderImageIo(context: Context, private val diskCache: DiskCache? = null) : ReaderImageIo {
    private val context = context.applicationContext
    private val store = FileReaderImageStore(this.context.cacheDir.absolutePath)

    override suspend fun prunePrepared(beforeMillis: Long) = withContext(Dispatchers.IO) { store.prune(beforeMillis) }
    override suspend fun readCached(source: String, maximumBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        readCoilReaderImageCache(diskCache, source, maximumBytes)
    }
    override suspend fun readLocal(source: String, maximumBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        store.readLocal(source, maximumBytes)
    }
    override suspend fun inspect(bytes: ByteArray): ReaderImageMetadata = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        ReaderImageMetadata(bounds.outMimeType ?: throw IOException("图片内容无效或格式不受支持"), bounds.outWidth, bounds.outHeight)
    }
    override suspend fun publish(displayName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        store.publish(displayName, bytes)
    }
    override suspend fun readPrepared(image: ReaderExportImage, maximumBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        store.readPrepared(image, maximumBytes)
    }
    override suspend fun saveToGallery(image: ReaderExportImage, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, image.displayName)
            put(MediaStore.Images.Media.MIME_TYPE, image.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CopyManga Neo")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IOException("无法创建相册图片")
        try {
            write(uri, bytes)
            if (resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) != 1) {
                throw IOException("图片保存未完成")
            }
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }
    override suspend fun saveToDocument(image: ReaderExportImage, destination: String, readValidatedBytes: suspend () -> ByteArray) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(destination)
        require(uri.scheme == "content") { "请选择系统文档保存位置" }
        try { write(uri, readValidatedBytes()) }
        catch (error: Exception) {
            // The system CreateDocument picker creates this destination; remove partial data on failure.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw error
        }
    }
    private suspend fun write(uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            var offset = 0
            while (offset < bytes.size) {
                currentCoroutineContext().ensureActive()
                val count = minOf(8192, bytes.size - offset)
                output.write(bytes, offset, count)
                offset += count
            }
            currentCoroutineContext().ensureActive()
        } ?: throw IOException("无法写入所选位置")
    }
}
