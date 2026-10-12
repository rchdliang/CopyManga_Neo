package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.BookDownloadTask
import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadProgressInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadedChapterInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getAllDownloadedComics(): Flow<List<DownloadedComicInfo>>
    suspend fun getDownloadedComic(pathWord: String): DownloadedComicInfo?
    suspend fun getDownloadedChapters(comicPathWord: String): List<DownloadedChapterInfo>
    suspend fun getDownloadedChapter(uuid: String): DownloadedChapterInfo?
    suspend fun getDownloadedChapterUuids(comicPathWord: String): Set<String>
    suspend fun saveComicCache(info: DownloadedComicInfo)
    suspend fun updateChapterMetadata(
        comicPathWord: String,
        group: String,
        chapters: List<ChapterInfo>
    )

    suspend fun isComicDownloaded(pathWord: String): Boolean
    suspend fun deleteComic(pathWord: String)
    suspend fun deleteChapter(uuid: String)
    suspend fun deleteChapters(comicPathWord: String, uuids: List<String>)
    suspend fun deleteComicAndChapters(pathWord: String)
    suspend fun removeChaptersFromTask(comicPathWord: String, chapterUuids: Set<String>)
    fun getDownloadProgress(): kotlinx.coroutines.flow.StateFlow<Map<String, DownloadProgressInfo>>
    fun getActiveTask(): kotlinx.coroutines.flow.StateFlow<BookDownloadTask?>
    fun getTasks(): kotlinx.coroutines.flow.StateFlow<List<BookDownloadTask>>
    fun enqueueDownload(task: BookDownloadTask)
    fun pauseDownload(taskKey: String)
    fun resumeDownload(taskKey: String)
    suspend fun cancelDownload(taskKey: String)
    suspend fun cancelAllNovels()
    suspend fun removeNovelVolumes(
        mode: RequestMode,
        owner: String,
        path: String,
        volumeIds: Set<String>
    ): Result<Unit>
    fun pauseComic(pathWord: String)
    fun resumeComic(pathWord: String)
    fun isPaused(pathWord: String): Boolean
    suspend fun cancelComic(pathWord: String)
    suspend fun cancelAll()
    fun setImageConcurrency(count: Int)
    fun getChapterStatus(comicPathWord: String, chapterUuid: String): ChapterDownloadStatus?
}
