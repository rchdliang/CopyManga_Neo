package com.rchdliang.copymanga_neo.domain.model

/**
 * 漫画收藏/阅读状态
 */
@kotlinx.serialization.Serializable
data class ComicStatus(
    val browse: BrowseInfo?,
    val collect: Int?
)

/**
 * 阅读进度信息
 */
@kotlinx.serialization.Serializable
data class BrowseInfo(
    val comicUuid: String?,
    val pathWord: String?,
    val chapterUuid: String?,
    val chapterName: String?
)
