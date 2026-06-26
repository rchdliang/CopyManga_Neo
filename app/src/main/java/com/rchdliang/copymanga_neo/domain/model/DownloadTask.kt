package com.rchdliang.copymanga_neo.domain.model

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

data class DownloadedChapterInfo(
    val uuid: String,
    val comicPathWord: String,
    val name: String,
    val index: Int,
    val imageCount: Int,
    val imagePaths: List<String>
)

