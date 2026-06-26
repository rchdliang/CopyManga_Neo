package com.rchdliang.copymanga_neo.domain.model

/**
 * 章节信息
 */
data class ChapterInfo(
    val index: Int,
    val uuid: String,
    val count: Int,
    val ordered: Int,
    val size: Int?,
    val name: String,
    val comicId: String,
    val comicPathWord: String,
    val groupId: String?,
    val groupPathWord: String?,
    val type: Int,
    val news: String?,
    val datetimeCreated: String?,
    val prevChapter: String?,
    val nextChapter: String?
)

/**
 * 章节列表
 */
data class ChapterList(
    val list: List<ChapterInfo>,
    val total: Int
)
