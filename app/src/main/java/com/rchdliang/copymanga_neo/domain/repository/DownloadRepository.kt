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
    suspend fun saveComicCache(entity: com.rchdliang.copymanga_neo.data.local.entity.DownloadedComicEntity)
    suspend fun isComicDownloaded(pathWord: String): Boolean
    suspend fun deleteComic(pathWord: String)
    suspend fun deleteChapter(uuid: String)
    fun getDownloadProgress(): kotlinx.coroutines.flow.StateFlow<Map<String, DownloadProgressInfo>>
    fun getActiveTask(): kotlinx.coroutines.flow.StateFlow<ComicDownloadTask?>
    fun enqueueDownload(task: ComicDownloadTask)
    fun cancelComic(pathWord: String)
    fun cancelAll()
    fun setImageConcurrency(count: Int)
    fun getChapterStatus(comicPathWord: String, chapterUuid: String): ChapterDownloadStatus?
}
