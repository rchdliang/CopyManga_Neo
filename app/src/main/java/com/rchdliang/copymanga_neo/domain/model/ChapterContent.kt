package com.rchdliang.copymanga_neo.domain.model

/**
 * 章节内容领域模型
 */
data class ChapterContent(
    val imageUrls: List<String>,
    val prevUuid: String?,
    val nextUuid: String?,
    val chapterName: String = "",
    val navigationResolved: Boolean = true
)

/** 仅在元数据请求失败时使用任务已有关系；已确认的章首、章尾保持 null。 */
fun ChapterContent.withFallbackNavigation(chapter: ChapterInfo): ChapterContent =
    if (navigationResolved) this else copy(
        prevUuid = chapter.prevChapter,
        nextUuid = chapter.nextChapter,
        navigationResolved = true
    )
