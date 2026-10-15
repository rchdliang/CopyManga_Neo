package com.rchdliang.copymanga_neo.domain.model

@kotlinx.serialization.Serializable
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

@kotlinx.serialization.Serializable
data class DownloadedChapterInfo(
    val uuid: String,
    val comicPathWord: String,
    val name: String,
    val index: Int,
    val imageCount: Int,
    val imagePaths: List<String>,
    val groupPathWord: String? = null,
    val prevChapter: String? = null,
    val nextChapter: String? = null
)

/** 离线仅沿已知关系跳转，不把下载列表中相邻的条目误当成连续章节。 */
fun DownloadedChapterInfo.offlineContent(chapters: List<DownloadedChapterInfo>): ChapterContent {
    val available = chapters.filter {
        it.comicPathWord == comicPathWord && it.groupPathWord == groupPathWord
    }.map { it.uuid }.toSet()
    return ChapterContent(
        imageUrls = imagePaths,
        prevUuid = prevChapter?.takeIf { it in available && it != uuid },
        nextUuid = nextChapter?.takeIf { it in available && it != uuid },
        chapterName = name
    )
}
