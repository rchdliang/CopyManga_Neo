package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import com.rchdliang.copymanga_neo.data.local.portable.DocumentStore
import com.rchdliang.copymanga_neo.data.storage.CacheStorage
import com.rchdliang.copymanga_neo.domain.model.DownloadedChapterComments
import com.rchdliang.copymanga_neo.domain.model.DownloadedChapterInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Serializable
internal data class DownloadedChapterRecord(
    val uuid: String, val comicPathWord: String, val name: String, val index: Int,
    val imageCount: Int, val imagePaths: List<String>, val status: String, val downloadAt: Long,
    val groupPathWord: String? = null, val prevChapter: String? = null, val nextChapter: String? = null
)

@Serializable
internal data class DownloadRecordsSnapshot(
    val version: Int = 1,
    val comics: Map<String, DownloadedComicInfo> = emptyMap(),
    val chapters: Map<String, DownloadedChapterRecord> = emptyMap(),
    val comments: Map<String, DownloadedChapterComments> = emptyMap()
)

/** Atomic metadata commits occur only after all image files have been published successfully. */
class DownloadRecords(storage: DocumentStorage, private val cache: CacheStorage) {
    private val document = DocumentStore(storage, "download-records", DownloadRecordsSnapshot.serializer(), DownloadRecordsSnapshot())
    private val loadMutex = Mutex()
    private var ready = false
    suspend fun awaitReady() = ensureReady()
    private suspend fun ensureReady() = loadMutex.withLock {
        if (!ready) {
            try {
                document.load().getOrThrow()
                check(document.state.value.version == 1) { "不支持的下载记录版本" }
                ready = true
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (error: Exception) {
                throw com.rchdliang.copymanga_neo.domain.platform.PlatformIOException(
                    "本地漫画下载记录无法读取，原数据已保留；修复存储后请重新启动", error)
            }
        }
    }
    internal fun getAllComics(): Flow<List<DownloadedComicInfo>> = flow {
        ensureReady()
        emitAll(document.state.map { it.comics.values.sortedByDescending { c -> c.downloadAt } })
    }
    internal fun getAllChapters(): Flow<List<DownloadedChapterRecord>> = flow {
        ensureReady(); emitAll(document.state.map { it.chapters.values.toList() })
    }
    internal suspend fun getComic(path: String): DownloadedComicInfo? {
        ensureReady(); return document.state.value.comics[path]
    }
    internal suspend fun insertComic(comic: DownloadedComicInfo) {
        ensureReady()
        document.update { it.copy(comics = it.comics + (comic.pathWord to comic)) }.getOrThrow()
    }
    internal suspend fun updateChapterCount(path: String, count: Int) {
        ensureReady()
        document.update { state ->
            val comic = state.comics[path] ?: return@update state
            state.copy(comics = state.comics + (path to comic.copy(downloadedChapters = count)))
        }.getOrThrow()
    }
    internal suspend fun deleteComic(path: String) {
        ensureReady()
        document.update { it.copy(comics = it.comics - path, chapters = it.chapters.filterValues { c -> c.comicPathWord != path }, comments = it.comments.filterKeys { id -> it.chapters[id]?.comicPathWord != path }) }.getOrThrow()
    }
    internal suspend fun getChaptersOnce(path: String): List<DownloadedChapterRecord> {
        ensureReady(); return document.state.value.chapters.values.filter { it.comicPathWord == path }.sortedBy { it.index }
    }
    internal suspend fun getChapter(uuid: String): DownloadedChapterRecord? {
        ensureReady(); return document.state.value.chapters[uuid]
    }
    internal suspend fun getChapterUuids(path: String): List<String> = getChaptersOnce(path).map { it.uuid }
    internal suspend fun insertChapter(chapter: DownloadedChapterRecord) {
        check(hasCompleteFiles(chapter)) { "章节文件不完整，不能提交本地记录" }
        ensureReady()
        document.update { it.copy(chapters = it.chapters + (chapter.uuid to chapter)) }.getOrThrow()
    }
    internal suspend fun deleteChaptersByComic(path: String) {
        ensureReady()
        document.update { it.copy(chapters = it.chapters.filterValues { c -> c.comicPathWord != path }, comments = it.comments.filterKeys { id -> it.chapters[id]?.comicPathWord != path }) }.getOrThrow()
    }
    internal suspend fun deleteChaptersByUuids(uuids: List<String>) {
        val removed = uuids.toSet()
        ensureReady()
        document.update { it.copy(chapters = it.chapters.filterKeys { id -> id !in removed }, comments = it.comments.filterKeys { id -> id !in removed }) }.getOrThrow()
    }
    internal suspend fun updateMetadata(path: String, uuid: String, name: String, index: Int, group: String, prev: String?, next: String?) {
        ensureReady()
        document.update { state ->
            val old = state.chapters[uuid]?.takeIf { it.comicPathWord == path } ?: return@update state
            state.copy(chapters = state.chapters + (uuid to old.copy(name = name, index = index, groupPathWord = group, prevChapter = prev, nextChapter = next)))
        }.getOrThrow()
    }
    internal suspend fun hasCompleteFiles(chapter: DownloadedChapterRecord): Boolean =
        chapter.status == "downloaded" && chapter.imageCount > 0 &&
            chapter.imagePaths.size == chapter.imageCount && chapter.imagePaths.distinct().size == chapter.imageCount &&
            chapter.imagePaths.all { path ->
                try { cache.size(path) > 0 }
                catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (_: Exception) { false }
            }

    internal suspend fun readComments(uuid: String): DownloadedChapterComments? {
        ensureReady(); return document.state.value.comments[uuid]
    }
    internal suspend fun saveComments(uuid: String, comments: DownloadedChapterComments) {
        ensureReady()
        val chapter = getChapter(uuid) ?: error("请先下载章节图片")
        check(hasCompleteFiles(chapter)) { "请先下载章节图片" }
        document.update {
            check(uuid in it.chapters) { "章节已删除，不能保存评论" }
            it.copy(comments = it.comments + (uuid to comments))
        }.getOrThrow()
    }

    internal suspend fun toInfo(chapter: DownloadedChapterRecord): DownloadedChapterInfo = DownloadedChapterInfo(
        chapter.uuid, chapter.comicPathWord, chapter.name, chapter.index, chapter.imageCount,
        chapter.imagePaths.map { cache.imageLocation(it) }, chapter.groupPathWord, chapter.prevChapter, chapter.nextChapter
    )
}
