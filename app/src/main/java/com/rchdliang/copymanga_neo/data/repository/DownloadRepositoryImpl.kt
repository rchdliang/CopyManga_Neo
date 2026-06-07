package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.download.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.data.download.ComicDownloadTask
import com.rchdliang.copymanga_neo.data.download.DownloadManager
import com.rchdliang.copymanga_neo.data.download.DownloadProgressInfo
import com.rchdliang.copymanga_neo.data.local.db.DownloadedChapterDao
import com.rchdliang.copymanga_neo.data.local.db.DownloadedComicDao
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedComicEntity
import com.rchdliang.copymanga_neo.domain.model.DownloadedChapterInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.domain.model.toInfo
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val comicDao: DownloadedComicDao,
    private val chapterDao: DownloadedChapterDao,
    private val downloadManager: DownloadManager
) : DownloadRepository {

    override fun getAllDownloadedComics(): Flow<List<DownloadedComicInfo>> {
        return comicDao.getAllComics().map { entities -> entities.map { it.toInfo() } }
    }

    override suspend fun getDownloadedComic(pathWord: String): DownloadedComicInfo? {
        return comicDao.getComic(pathWord)?.toInfo()
    }

    override suspend fun getDownloadedChapters(comicPathWord: String): List<DownloadedChapterInfo> {
        return chapterDao.getChaptersOnce(comicPathWord).map { it.toInfo() }
    }

    override suspend fun getDownloadedChapter(uuid: String): DownloadedChapterInfo? {
        return chapterDao.getChapter(uuid)?.toInfo()
    }

    override suspend fun getDownloadedChapterUuids(comicPathWord: String): Set<String> {
        return chapterDao.getChapterUuids(comicPathWord).toSet()
    }

    override suspend fun isComicDownloaded(pathWord: String): Boolean {
        return comicDao.getComic(pathWord) != null
    }

    override suspend fun saveComicCache(entity: DownloadedComicEntity) {
        val existing = comicDao.getComic(entity.pathWord)
        if (existing != null) {
            comicDao.updateComic(entity)
        } else {
            comicDao.insertComic(entity)
        }
    }

    override suspend fun deleteComic(pathWord: String) {
        comicDao.deleteComic(pathWord)
    }

    override suspend fun deleteChapter(uuid: String) {
        chapterDao.deleteChapter(uuid)
    }

    override fun getDownloadProgress(): StateFlow<Map<String, DownloadProgressInfo>> {
        return downloadManager.downloadProgress
    }

    override fun getActiveTask(): StateFlow<ComicDownloadTask?> {
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

    override fun enqueueDownload(task: ComicDownloadTask) {
        downloadManager.enqueue(task)
    }

    override fun cancelComic(pathWord: String) {
        downloadManager.cancelComic(pathWord)
    }

    override fun cancelAll() {
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
