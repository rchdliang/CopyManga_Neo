package com.rchdliang.copymanga_neo.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.domain.repository.ReaderImageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

/** 导出原始字节，避免长漫画整图解码带来的内存峰值和格式损失。 */
@Singleton
class ReaderImageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) : ReaderImageRepository {
    @OptIn(ExperimentalCoilApi::class)
    override suspend fun prepare(source: String, title: String): Result<ReaderExportImage> =
        result {
            require(source.isNotBlank()) { "没有可导出的图片" }
            val directory = File(context.cacheDir, "reader_shared_images").apply {
                if (!isDirectory && !mkdirs()) throw IOException("无法创建图片临时目录")
            }
            // 分享后保留副本供接收方读取；只清理本导出目录中超过两天的旧文件。
            val cutoff = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
            directory.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }
                ?.forEach { it.delete() }
            val temporary = File(directory, UUID.randomUUID().toString() + ".tmp")
            try {
                temporary.outputStream().use { output ->
                    if (source.startsWith("https://") || source.startsWith("http://")) {
                        val snapshot = context.imageLoader.diskCache?.openSnapshot(source)
                        if (snapshot != null) {
                            snapshot.use {
                                it.data.toFile().inputStream().use { input -> copy(input, output) }
                            }
                        } else {
                            client.newCall(Request.Builder().url(source).build())
                                .awaitImageResponse().use { response ->
                                    if (!response.isSuccessful) throw IOException("图片获取失败（HTTP ${response.code}）")
                                    response.body.byteStream().use { input -> copy(input, output) }
                                }
                        }
                    } else {
                        val file =
                            if (source.startsWith("file://")) File(source.toUri().path.orEmpty()) else File(
                                source
                            )
                        if (!file.isFile) throw IOException("本地图片不存在，请重新下载")
                        file.inputStream().use { input -> copy(input, output) }
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temporary.absolutePath, bounds)
                val mime = bounds.outMimeType ?: throw IOException("图片内容无效或格式不受支持")
                val extension = when (mime) {
                    "image/jpeg" -> "jpg"
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    "image/avif" -> "avif"
                    "image/heif", "image/heic" -> "heic"
                    else -> throw IOException("不支持导出此图片格式")
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("图片内容无效")
                val safeTitle =
                    title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(80)
                        .ifBlank { "漫画图片" }
                val name = "${safeTitle}_${UUID.randomUUID().toString().take(8)}.$extension"
                val file = File(directory, name)
                if (!temporary.renameTo(file)) throw IOException("图片临时文件准备失败")
                ReaderExportImage(file.absolutePath, mime, name)
            } finally {
                temporary.delete()
            }
        }

    override suspend fun saveToGallery(image: ReaderExportImage): Result<Unit> = result {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, image.displayName)
            put(MediaStore.Images.Media.MIME_TYPE, image.mimeType)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/CopyManga Neo"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values
        )
            ?: throw IOException("无法创建相册图片")
        try {
            write(image, uri)
            if (resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null
                ) != 1
            ) {
                throw IOException("图片保存未完成")
            }
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    override suspend fun saveToDocument(
        image: ReaderExportImage,
        destination: String
    ): Result<Unit> = result {
        val uri = destination.toUri()
        try {
            write(image, uri)
        } catch (error: Exception) {
            // 目标是系统选择器本次新建的文档；失败时移除未完成的副本。
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw error
        }
    }

    private suspend fun write(image: ReaderExportImage, uri: Uri) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            File(image.filePath).inputStream().use { input -> copy(input, output) }
        } ?: throw IOException("无法写入所选位置")
    }

    private suspend fun copy(input: InputStream, output: OutputStream) =
        withContext(Dispatchers.IO) {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
        }

    private suspend fun <T> result(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
}

/** 图片获取沿用现有网络配置；取消导出时同时取消请求。 */
private suspend fun Call.awaitImageResponse(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }
