package com.rchdliang.copymanga_neo.domain.model

/** 云端浏览记录；阅读进度与漫画最新章节分别保存。 */
@kotlinx.serialization.Serializable
data class BrowseHistoryItem(
    val id: Long,
    val name: String,
    val pathWord: String,
    val cover: String?,
    val authors: List<Author>,
    val lastReadChapterId: String?,
    val lastReadChapterName: String?,
    val latestChapterId: String?,
    val latestChapterName: String?
)

@kotlinx.serialization.Serializable
data class BrowseHistoryPage(
    val items: List<BrowseHistoryItem>,
    val nextOffset: Int?
)
