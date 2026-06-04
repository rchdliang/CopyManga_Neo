package com.rchdliang.copymanga_neo.domain.model

/**
 * 章节内容领域模型
 */
data class ChapterContent(
    val imageUrls: List<String>,
    val prevUuid: String?,
    val nextUuid: String?,
    val chapterName: String = ""
)
