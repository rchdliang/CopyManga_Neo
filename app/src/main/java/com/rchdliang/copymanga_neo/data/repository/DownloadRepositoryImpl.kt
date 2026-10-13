package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.download.DownloadManager
import com.rchdliang.copymanga_neo.data.download.LocalDownloadAccess
import com.rchdliang.copymanga_neo.data.download.hasCompleteFiles
import com.rchdliang.copymanga_neo.data.local.db.DownloadedChapterDao
import com.rchdliang.copymanga_neo.data.local.db.DownloadedComicDao
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedComicEntity
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val comicDao: DownloadedComicDao,
    private val chapterDao: DownloadedChapterDao,
    private val downloadManager: DownloadManager,
    private val localAccess: LocalDownloadAccess = LocalDownloadAccess()
) : DownloadRepository {

    override fun getAllDownloadedComics(): Flow<List<DownloadedComicInfo>> {
        return combine(comicDao.getAllComics(), chapterDao.getAllChapters()) { comics, chapters ->
            val counts = chapters.filter { it.hasCompleteFiles() }
                .groupingBy { it.comicPathWord }.eachCount()
            comics.mapNotNull { comic ->
                val count = counts[comic.pathWord] ?: return@mapNotNull null
                comic.toInfo().copy(downloadedChapters = count, totalChapters = count)
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getDownloadedComic(pathWord: String): DownloadedComicInfo? {
        val comic = comicDao.getComic(pathWord) ?: return null
        val count = getDownloadedChapters(pathWord).size
        return comic.toInfo().copy(downloadedChapters = count, totalChapters = count)
    }

    override suspend fun getDownloadedChapters(comicPathWord: String): List<DownloadedChapterInfo> {
        return withContext(Dispatchers.IO) {
            chapterDao.getChaptersOnce(comicPathWord)
                .filter { it.hasCompleteFiles() }.map { it.toInfo() }
        }
    }

    override suspend fun getDownloadedChapter(uuid: String): DownloadedChapterInfo? {
        return withContext(Dispatchers.IO) {
            chapterDao.getChapter(uuid)?.takeIf { it.hasCompleteFiles() }?.toInfo()
        }
    }

    override suspend fun getDownloadedChapterUuids(comicPathWord: String): Set<String> {
        return getDownloadedChapters(comicPathWord).map { it.uuid }.toSet()
    }

    override suspend fun isComicDownloaded(pathWord: String): Boolean {
        return getDownloadedChapters(pathWord).isNotEmpty()
    }

    override suspend fun saveComicCache(info: DownloadedComicInfo) {
        val entity = DownloadedComicEntity(
            pathWord = info.pathWord,
            name = info.name,
            cover = info.cover,
            authorNames = info.authorNames,
            statusDisplay = info.statusDisplay,
            lastUpdate = info.lastUpdate,
            lastChapterName = info.lastChapterName,
            brief = info.brief,
            totalChapters = info.totalChapters,
            downloadedChapters = info.downloadedChapters,
            downloadAt = info.downloadAt
        )
        comicDao.insertComic(entity)
    }

    override suspend fun updateChapterMetadata(
        comicPathWord: String, group: String, chapters: List<ChapterInfo>
    ) {
        val downloaded = chapterDao.getChapterUuids(comicPathWord).toSet()
        chapters.filter { it.uuid in downloaded }.forEach { chapter ->
            chapterDao.updateMetadata(
                comicPathWord, chapter.uuid, chapter.name, chapter.index,
                chapter.groupPathWord ?: group, chapter.prevChapter, chapter.nextChapter
            )
        }
    }

    override suspend fun deleteComic(pathWord: String) {
        localAccess.mutate("$pathWord/").use {
            downloadManager.cancelComic(pathWord)
            downloadManager.deleteComicFiles(pathWord)
            comicDao.deleteComic(pathWord)
        }
    }

    override suspend fun deleteChapters(comicPathWord: String, uuids: List<String>) {
        // 先取得所有写租约再执行，避免多章删除只执行一半后遇到导出中的章节。
        val leases = mutableListOf<java.io.Closeable>()
        try {
            uuids.forEach { leases += localAccess.mutate("$comicPathWord/$it") }
            downloadManager.removeChaptersFromTask(comicPathWord, uuids.toSet())
            uuids.forEach { downloadManager.deleteChapterFiles(comicPathWord, it) }
            chapterDao.deleteChaptersByUuids(uuids)
            val remainingCount = chapterDao.getChapterUuids(comicPathWord).size
            comicDao.updateChapterCount(comicPathWord, remainingCount)
        } finally {
            leases.asReversed().forEach { it.close() }
        }
    }

    override suspend fun deleteComicAndChapters(pathWord: String) {
        localAccess.mutate("$pathWord/").use {
            downloadManager.cancelComic(pathWord)
            downloadManager.deleteComicFiles(pathWord)
            chapterDao.deleteChaptersByComic(pathWord)
            comicDao.deleteComic(pathWord)
        }
    }

    override suspend fun removeChaptersFromTask(comicPathWord: String, chapterUuids: Set<String>) {
        downloadManager.removeChaptersFromTask(comicPathWord, chapterUuids)
    }

    override suspend fun deleteChapter(uuid: String) {
        val chapter = chapterDao.getChapter(uuid) ?: return
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
