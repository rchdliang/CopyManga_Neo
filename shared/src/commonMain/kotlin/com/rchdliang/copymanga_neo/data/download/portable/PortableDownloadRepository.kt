package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.domain.model.BookDownloadTask
import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadProgressInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadedChapterInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class PortableDownloadRepository(
    private val records: DownloadRecords,
    private val downloadManager: DownloadManager,
    private val localAccess: LocalDownloadAccess
) : DownloadRepository {
    override suspend fun awaitReady(): Result<Unit> = try {
        records.awaitReady()
        downloadManager.awaitRestoration()
        Result.success(Unit)
    } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
    catch (error: Exception) {
        Result.failure(com.rchdliang.copymanga_neo.domain.platform.PlatformIOException(
            "下载存储无法读取，原数据已保留，下载操作已停用；修复存储后请重新启动", error))
    }


    override fun getAllDownloadedComics(): Flow<List<DownloadedComicInfo>> {
        return combine(records.getAllComics(), records.getAllChapters()) { comics, chapters ->
            val counts = chapters.filter { records.hasCompleteFiles(it) }
                .groupingBy { it.comicPathWord }.eachCount()
            comics.mapNotNull { comic ->
                val count = counts[comic.pathWord] ?: return@mapNotNull null
                comic.copy(downloadedChapters = count, totalChapters = count)
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun getDownloadedComic(pathWord: String): DownloadedComicInfo? {
        val comic = records.getComic(pathWord) ?: return null
        val count = getDownloadedChapters(pathWord).size
        return comic.copy(downloadedChapters = count, totalChapters = count)
    }

    override suspend fun getDownloadedChapters(comicPathWord: String): List<DownloadedChapterInfo> {
        return withContext(Dispatchers.Default) {
            records.getChaptersOnce(comicPathWord)
                .filter { records.hasCompleteFiles(it) }.map { records.toInfo(it) }
        }
    }

    override suspend fun getDownloadedChapter(uuid: String): DownloadedChapterInfo? {
        return withContext(Dispatchers.Default) {
            records.getChapter(uuid)?.takeIf { records.hasCompleteFiles(it) }?.let { records.toInfo(it) }
        }
    }

    override suspend fun getDownloadedChapterUuids(comicPathWord: String): Set<String> {
        return getDownloadedChapters(comicPathWord).map { it.uuid }.toSet()
    }

    override suspend fun isComicDownloaded(pathWord: String): Boolean {
        return getDownloadedChapters(pathWord).isNotEmpty()
    }

    override suspend fun saveComicCache(info: DownloadedComicInfo) {
        records.insertComic(info)
    }

    override suspend fun updateChapterMetadata(
        comicPathWord: String, group: String, chapters: List<ChapterInfo>
    ) {
        val downloaded = records.getChapterUuids(comicPathWord).toSet()
        chapters.filter { it.uuid in downloaded }.forEach { chapter ->
            records.updateMetadata(
                comicPathWord, chapter.uuid, chapter.name, chapter.index,
                chapter.groupPathWord ?: group, chapter.prevChapter, chapter.nextChapter
            )
        }
    }

    override suspend fun deleteComic(pathWord: String) {
        localAccess.mutate("$pathWord/").use {
            downloadManager.cancelComic(pathWord)
            downloadManager.deleteComicFiles(pathWord)
            records.deleteComic(pathWord)
        }
    }

    override suspend fun deleteChapters(comicPathWord: String, uuids: List<String>) {
        // 先取得所有写租约再执行，避免多章删除只执行一半后遇到导出中的章节。
        val leases = mutableListOf<Lease>()
        try {
            uuids.forEach { leases += localAccess.mutate("$comicPathWord/$it") }
            downloadManager.removeChaptersFromTask(comicPathWord, uuids.toSet())
            uuids.forEach { downloadManager.deleteChapterFiles(comicPathWord, it) }
            records.deleteChaptersByUuids(uuids)
            val remainingCount = records.getChapterUuids(comicPathWord).size
            records.updateChapterCount(comicPathWord, remainingCount)
        } finally {
            leases.asReversed().forEach { it.release() }
        }
    }

    override suspend fun deleteComicAndChapters(pathWord: String) {
        localAccess.mutate("$pathWord/").use {
            downloadManager.cancelComic(pathWord)
            downloadManager.deleteComicFiles(pathWord)
            records.deleteChaptersByComic(pathWord)
            records.deleteComic(pathWord)
        }
    }

    override suspend fun removeChaptersFromTask(comicPathWord: String, chapterUuids: Set<String>) {
        downloadManager.removeChaptersFromTask(comicPathWord, chapterUuids)
    }

    override suspend fun deleteChapter(uuid: String) {
        val chapter = records.getChapter(uuid) ?: return
        deleteChapters(chapter.comicPathWord, listOf(uuid))
    }

    override fun getDownloadProgress(): StateFlow<Map<String, DownloadProgressInfo>> {
        return downloadManager.downloadProgress
    }

    override fun getActiveTask(): StateFlow<BookDownloadTask?> {
        return downloadManager.activeTask
    }

    override fun pauseComic(pathWord: String) {
        downloadManager.pauseComic(pathWord)
    }

    override fun resumeComic(pathWord: String) {
        downloadManager.resumeComic(pathWord)
    }

    override fun isPaused(pathWord: String): Boolean {
        return downloadManager.isPaused(pathWord)
    }

    override fun getTasks(): StateFlow<List<BookDownloadTask>> = downloadManager.tasks
    override fun pauseDownload(taskKey: String) {
        downloadManager.pauseDownload(taskKey)
    }

    override fun resumeDownload(taskKey: String) {
        downloadManager.resumeDownload(taskKey)
    }

    override suspend fun cancelDownload(taskKey: String) = downloadManager.cancelDownload(taskKey)
    override suspend fun cancelAllNovels() = downloadManager.cancelAllNovels()
    override suspend fun removeNovelVolumes(
        mode: RequestMode,
        owner: String,
        path: String,
        volumeIds: Set<String>
    ): Result<Unit> =
        downloadManager.removeNovelVolumes(mode, owner, path, volumeIds)

    override fun enqueueDownload(task: BookDownloadTask) {
        downloadManager.enqueue(task)
    }

    override suspend fun cancelNovelVolumes(
        mode: RequestMode, owner: String, path: String, volumeIds: Set<String>
    ): Result<Unit> = downloadManager.cancelNovelVolumes(mode, owner, path, volumeIds)

    override suspend fun cancelComic(pathWord: String) {
        downloadManager.cancelComic(pathWord)
    }

    override suspend fun cancelAll() {
        downloadManager.cancelAll()
    }

    override fun setImageConcurrency(count: Int) {
        downloadManager.setImageConcurrency(count)
    }

    override fun getChapterStatus(
        comicPathWord: String,
        chapterUuid: String
    ): ChapterDownloadStatus? {
        return downloadManager.getTask(comicPathWord)?.chapters
            ?.find { it.chapter.uuid == chapterUuid }?.status
    }
}
