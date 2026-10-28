package com.rchdliang.copymanga_neo.ui.favorites

import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.NovelShelfEntry

data class NovelShelfReadingStatus(
    val text: String = "还未读过",
    val highlighted: Boolean = true
)

/** 与漫画保持相同优先级；仅使用当前账号的小说记录，不混入漫画或书签。 */
internal fun novelShelfReadingStatuses(
    entries: List<NovelShelfEntry>,
    records: List<LocalReadingRecord>,
    owner: String?
): Map<String, NovelShelfReadingStatus> {
    val latest = records.filter {
        owner != null && it.owner == owner && it.kind == "novel" && it.chapter.isNotBlank()
    }.groupBy { it.path }.mapValues { (_, history) -> history.maxBy { it.updatedAt } }
    return entries.associate { entry ->
        val read = latest[entry.book.path]
        entry.book.path to when {
            read == null -> NovelShelfReadingStatus()
            entry.lastChapterId.isNotBlank() && read.chapter != entry.lastChapterId ->
                NovelShelfReadingStatus("有新内容：${entry.lastChapterName.ifBlank { "新卷" }}")
            else -> NovelShelfReadingStatus(
                "上次读到：${read.name.ifBlank { "未知卷" }}", highlighted = false
            )
        }
    }
}
