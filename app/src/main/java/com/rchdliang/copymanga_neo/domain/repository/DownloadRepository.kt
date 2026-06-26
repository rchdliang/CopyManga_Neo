package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.data.download.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.data.download.ComicDownloadTask
import com.rchdliang.copymanga_neo.data.download.DownloadProgressInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadedChapterInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getAllDownloadedComics(): Flow<List<DownloadedComicInfo>>
    suspend fun getDownloadedComic(pathWord: String): DownloadedComicInfo?
    suspend fun getDownloadedChapters(comicPathWord: String): List<DownloadedChapterInfo>
    suspend fun getDownloadedChapter(uuid: String): DownloadedChapterInfo?
    suspend fun getDownloadedChapterUuids(comicPathWord: String): Set<String>
    suspend fun saveComicCache(info: DownloadedComicInfo)
    suspend fun isComicDownloaded(pathWord: String): Boolean
    suspend fun deleteComic(pathWord: String)
    suspend fun deleteChapter(uuid: String)
    suspend fun deleteChapters(comicPathWord: String, uuids: List<String>)
    suspend fun deleteComicAndChapters(pathWord: String)
    fun removeChaptersFromTask(comicPathWord: String, chapterUuids: Set<String>)
    fun getDownloadProgress(): kotlinx.coroutines.flow.StateFlow<Map<String, DownloadProgressInfo>>
    fun getActiveTask(): kotlinx.coroutines.flow.StateFlow<ComicDownloadTask?>
    fun enqueueDownload(task: ComicDownloadTask)
    fun pauseComic(pathWord: String)
    fun resumeComic(pathWord: String)
    fun isPaused(pathWord: String): Boolean
    fun cancelComic(pathWord: String)
    fun cancelAll()
    fun setImageConcurrency(count: Int)
    fun getChapterStatus(comicPathWord: String, chapterUuid: String): ChapterDownloadStatus?
}
