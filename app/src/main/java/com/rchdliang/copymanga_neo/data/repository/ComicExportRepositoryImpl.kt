package com.rchdliang.copymanga_neo.data.repository

import android.content.Context
import com.rchdliang.copymanga_neo.data.download.hasCompleteFiles
import com.rchdliang.copymanga_neo.data.export.ComicExportManager
import com.rchdliang.copymanga_neo.data.export.exportUnits
import com.rchdliang.copymanga_neo.data.export.imageExtension
import com.rchdliang.copymanga_neo.data.export.readImageHeader
import com.rchdliang.copymanga_neo.data.local.db.DownloadedChapterDao
import com.rchdliang.copymanga_neo.data.local.db.DownloadedComicDao
import com.rchdliang.copymanga_neo.domain.model.ComicExportChapter
import com.rchdliang.copymanga_neo.domain.model.ComicExportComic
import com.rchdliang.copymanga_neo.domain.model.ComicExportDraft
import com.rchdliang.copymanga_neo.domain.model.ComicExportFormat
import com.rchdliang.copymanga_neo.domain.model.ComicExportLocation
import com.rchdliang.copymanga_neo.domain.model.ComicExportOutput
import com.rchdliang.copymanga_neo.domain.model.ComicExportPage
import com.rchdliang.copymanga_neo.domain.model.ComicExportPlan
import com.rchdliang.copymanga_neo.domain.model.ComicExportRequest
import com.rchdliang.copymanga_neo.domain.repository.ComicExportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComicExportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val comicDao: DownloadedComicDao,
    private val chapterDao: DownloadedChapterDao,
    private val manager: ComicExportManager
) : ComicExportRepository {
    override val state = manager.state
    override val messages = manager.messages

    override suspend fun saveDraft(
        plan: ComicExportPlan,
        outputs: List<ComicExportOutput>
    ): Result<String> =
        result { manager.saveDraft(plan, outputs) }

    override suspend fun readDraft(id: String): Result<ComicExportDraft?> =
        result { manager.readDraft(id) }

    override suspend fun start(
        plan: ComicExportPlan, format: ComicExportFormat, destination: String, directory: Boolean,
        previousOutputs: List<ComicExportOutput>
    ): Result<Unit> = result {
        manager.start(plan, format, destination, directory, previousOutputs).fold({}, { throw it })
    }

    override suspend fun awaitRestoration(): Result<Unit> = result { manager.awaitRestoration() }
    override fun cancel() = manager.cancel()
    override suspend fun dismiss(): Result<Unit> = result { manager.dismiss() }

    override fun describeLocation(
        plan: ComicExportPlan, format: ComicExportFormat, previousOutputs: List<ComicExportOutput>
    ): ComicExportLocation? {
        val units =
            exportUnits(plan, format).filter { unit -> previousOutputs.none { it.id == unit.id } }
        val first = units.firstOrNull() ?: return null
        return ComicExportLocation(
            format == ComicExportFormat.IMAGES || units.size != 1,
            first.name,
            format.mimeType
        )
    }

    override suspend fun prepare(request: ComicExportRequest): Result<ComicExportPlan> =
        withContext(Dispatchers.IO) {
            try {
                val skipped = mutableListOf<String>()
                val found = mutableSetOf<String>()
                val comics = request.comics.distinct().mapNotNull { pathWord ->
                    currentCoroutineContext().ensureActive()
                    val comic = comicDao.getComic(pathWord)
                    if (comic == null) {
                        skipped += "$pathWord：没有本地漫画记录"
                        return@mapNotNull null
                    }
                    val chapters = chapterDao.getChaptersOnce(pathWord)
                        .filter { request.chapters == null || it.uuid in request.chapters }
                        .sortedWith(
                            compareBy(
                                { it.groupPathWord.orEmpty() },
                                { it.index },
                                { it.uuid })
                        )
                        .mapNotNull { chapter ->
                            found += chapter.uuid
                            if (!chapter.hasCompleteFiles()) {
                                skipped += "${comic.name}／${chapter.name}：图片未完整下载"
                                return@mapNotNull null
                            }
                            try {
                                val root = File(
                                    context.filesDir,
                                    "downloads/$pathWord/${chapter.uuid}"
                                ).canonicalFile
                                val downloadRoot = File(context.filesDir, "downloads").canonicalFile
                                require(
                                    root.toPath().startsWith(downloadRoot.toPath())
                                ) { "下载路径无效" }
                                val pages = chapter.imagePaths.split(',').map { path ->
                                    currentCoroutineContext().ensureActive()
                                    val file = File(path).canonicalFile
                                    require(file.parentFile == root) { "图片路径不属于该章节" }
                                    val extension = file.inputStream()
                                        .use { imageExtension(readImageHeader(it)) }
                                    ComicExportPage(file.absolutePath, file.length(), extension)
                                }
                                ComicExportChapter(
                                    chapter.uuid, chapter.name, chapter.index,
                                    chapter.groupPathWord?.ifBlank { null } ?: "未分组", pages)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                skipped += "${comic.name}／${chapter.name}：${error.message}"
                                null
                            }
                        }
                    if (chapters.isEmpty()) null else ComicExportComic(
                        pathWord,
                        comic.name,
                        comic.authorNames,
                        chapters
                    )
                }
                request.chapters?.minus(found)?.takeIf { it.isNotEmpty() }?.let {
                    skipped += "${it.size} 个所选章节没有下载记录"
                }
                if (comics.isEmpty()) throw IOException("没有完整下载、可导出的章节")
                Result.success(ComicExportPlan(comics, skipped))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
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
