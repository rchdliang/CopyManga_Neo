package com.rchdliang.copymanga_neo.domain.model

import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterEntity
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedComicEntity

data class DownloadedComicInfo(
    val pathWord: String,
    val name: String,
    val cover: String?,
    val authorNames: String,
    val statusDisplay: String,
    val lastUpdate: String?,
    val lastChapterName: String?,
    val brief: String?,
    val totalChapters: Int,
    val downloadedChapters: Int,
    val downloadAt: Long
)

fun DownloadedComicEntity.toInfo() = DownloadedComicInfo(
    pathWord = pathWord,
    name = name,
    cover = cover,
    authorNames = authorNames,
    statusDisplay = statusDisplay,
    lastUpdate = lastUpdate,
    lastChapterName = lastChapterName,
    brief = brief,
    totalChapters = totalChapters,
    downloadedChapters = downloadedChapters,
    downloadAt = downloadAt
)

data class DownloadedChapterInfo(
    val uuid: String,
    val comicPathWord: String,
    val name: String,
    val index: Int,
    val imageCount: Int,
    val imagePaths: List<String>
)

fun DownloadedChapterEntity.toInfo() = DownloadedChapterInfo(
    uuid = uuid,
    comicPathWord = comicPathWord,
    name = name,
    index = index,
    imageCount = imageCount,
    imagePaths = imagePaths.split(",").filter { it.isNotBlank() }
)
