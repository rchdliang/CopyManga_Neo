package com.rchdliang.copymanga_neo.multiplatform.ui.favorites

import com.rchdliang.copymanga_neo.domain.model.ShelfReadingRecord
import com.rchdliang.copymanga_neo.domain.model.ShelfReadingStatus

internal data class FavoritesReadingStatus(val text: String, val emphasized: Boolean)

/** 追更提醒决定文案；有后续章节可读的补旧作品也使用主题强调色。 */
internal fun favoritesReadingStatus(
    tracking: ShelfReadingRecord?,
    lastReadChapterId: String,
    lastReadChapterName: String,
    latestChapterId: String,
    latestChapterName: String = ""
): FavoritesReadingStatus {
    val chapter = tracking?.chapter?.ifBlank { lastReadChapterId } ?: lastReadChapterId
    val name = tracking?.chapterName?.ifBlank { lastReadChapterName } ?: lastReadChapterName
    val latest = latestChapterId.ifBlank { tracking?.latestChapterId.orEmpty() }
    val status = tracking?.status ?: if (chapter.isBlank()) ShelfReadingStatus.UNREAD else ShelfReadingStatus.READING
    return when (status) {
        ShelfReadingStatus.UNREAD -> FavoritesReadingStatus("还未读过", true)
        ShelfReadingStatus.UPDATED -> {
            val latestName = tracking?.latestName?.ifBlank { latestChapterName } ?: latestChapterName
            FavoritesReadingStatus(if (latestName.isBlank()) "有新内容" else "有新内容：$latestName", true)
        }
        ShelfReadingStatus.CAUGHT_UP -> FavoritesReadingStatus("已读至最新", false)
        ShelfReadingStatus.READING -> FavoritesReadingStatus(
            if (name.isBlank()) "继续阅读" else "继续阅读：$name",
            chapter.isNotBlank() && latest.isNotBlank() && chapter != latest)
    }
}
