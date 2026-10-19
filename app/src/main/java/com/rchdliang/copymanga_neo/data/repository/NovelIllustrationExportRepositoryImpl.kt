package com.rchdliang.copymanga_neo.data.repository

import android.content.Context
import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import com.rchdliang.copymanga_neo.domain.model.NovelSection
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.domain.reading.NovelReaderDocument
import com.rchdliang.copymanga_neo.domain.repository.NovelIllustrationExportRepository
import com.rchdliang.copymanga_neo.domain.repository.ReaderImageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** 复用原图获取和系统文档保存；预览模糊不参与导出，ZIP 按图集顺序流式写入。 */
@Singleton
class NovelIllustrationExportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val images: ReaderImageRepository
) : NovelIllustrationExportRepository {
    override suspend fun prepareImage(volume: NovelReadingVolume, image: NovelSection) =
        images.prepare(
            volume.cachedImages[image.image]?.takeIf { File(it).isFile } ?: image.image.orEmpty(),
            "${volume.book.name}_${volume.volume.name}_${image.name}"
        )

    override suspend fun prepareVolume(volume: NovelReadingVolume): Result<ReaderExportImage> =
        withContext(Dispatchers.IO) {
            var archive: File? = null
            try {
                val illustrations = NovelReaderDocument(volume.document, volume.sections).images
                require(illustrations.isNotEmpty()) { "本卷没有可导出的插图" }
                val directory = File(context.cacheDir, "novel_illustration_exports")
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建导出临时目录")
                val cutoff = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
                directory.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }
                    ?.forEach { it.delete() }
                val target = File(directory, "${UUID.randomUUID()}.zip")
                archive = target
                ZipOutputStream(target.outputStream().buffered(), Charsets.UTF_8).use { zip ->
                    illustrations.forEachIndexed { index, illustration ->
                        currentCoroutineContext().ensureActive()
                        val prepared = prepareImage(volume, illustration).fold({ it }, { throw it })
                        val source = File(prepared.filePath)
                        try {
                            val extension = source.extension
                            val name = safeName(illustration.name.ifBlank { "插图" })
                            zip.putNextEntry(
                                ZipEntry(
                                    "${
                                        (index + 1).toString().padStart(3, '0')
                                    }_$name.$extension"
                                )
                            )
                            source.inputStream().use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    zip.write(buffer, 0, count)
                                }
                            }
                            zip.closeEntry()
                        } finally {
                            source.delete()
                        }
                    }
                }
                Result.success(
                    ReaderExportImage(
                        target.absolutePath, "application/zip",
                        "${safeName(volume.book.name)}_${safeName(volume.volume.name)}_插图.zip"
                    )
                )
            } catch (error: CancellationException) {
                archive?.delete()
                throw error
            } catch (error: Exception) {
                archive?.delete()
                Result.failure(error)
            }
        }

    override suspend fun saveToDocument(
        file: ReaderExportImage,
        destination: String
    ): Result<Unit> =
        images.saveToDocument(file, destination)

    private fun safeName(value: String) =
        value.map { if (it in "\\/:*?\"<>|" || it.isISOControl()) '_' else it }
            .joinToString("").trim().trim('.').take(60).ifBlank { "插图" }
}
