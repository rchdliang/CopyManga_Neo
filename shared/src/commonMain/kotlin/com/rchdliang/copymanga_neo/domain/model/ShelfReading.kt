package com.rchdliang.copymanga_neo.domain.model

import com.rchdliang.copymanga_neo.domain.reading.ReadingMergePolicy
import kotlinx.serialization.Serializable

enum class ShelfReadingStatus { UNREAD, READING, CAUGHT_UP, UPDATED }

@Serializable
data class ShelfReadingKey(
    val owner: String, val mode: RequestMode, val kind: String, val path: String, val group: String = ""
) {
    // mode 仅记录请求来源；同一账号的阅读事实在 Web / App 间共用。
    val id: String get() = ReadingMergePolicy.key(owner, kind, path, group)
    val bookKey: String get() = ReadingMergePolicy.key(owner, kind, path)
}

data class ShelfChapter(val id: String, val name: String)

/** 续读位置与追更基线分开保存；只有在线完整目录能建立/更新追平基线。 */
@Serializable
data class ShelfReadingRecord(
    val key: ShelfReadingKey,
    val chapter: String = "",
    val chapterName: String = "",
    val completed: Boolean = false,
    val readAt: Long = 0,
    val historyCutoff: Long = -1,
    val catalog: List<String> = emptyList(),
    val latestName: String = "",
    val caughtUpChapter: String? = null,
    // 保留离线期间实际读过的章节，联网后再判定是否已开始追平基线之后的新内容。
    val startedSinceCaughtUp: Set<String> = emptySet()
) {
    val status: ShelfReadingStatus get() {
        val frontier = caughtUpChapter?.let(catalog::indexOf) ?: -1
        return when {
            frontier >= 0 && frontier < catalog.lastIndex -> ShelfReadingStatus.UPDATED
            chapter.isBlank() -> ShelfReadingStatus.UNREAD
            frontier >= 0 && chapter == caughtUpChapter -> ShelfReadingStatus.CAUGHT_UP
            else -> ShelfReadingStatus.READING
        }
    }

    fun read(id: String, name: String, finished: Boolean, at: Long): ShelfReadingRecord {
        val next = copy(chapter = id, chapterName = name,
            completed = finished || (chapter == id && completed), readAt = at,
            startedSinceCaughtUp = if (caughtUpChapter == null) emptySet() else startedSinceCaughtUp + id)
        return next.consumeStartedUpdate()
    }

    fun observeCatalog(chapters: List<ShelfChapter>): ShelfReadingRecord {
        if (chapters.isEmpty() || chapters.any { it.id.isBlank() } || chapters.map { it.id }.distinct().size != chapters.size) return this
        val ids = chapters.map { it.id }
        var next = copy(catalog = ids, latestName = chapters.last().name)
        // 删除、重编号或换目录不能用“不相等”推断新增。
        val frontier = catalog.indexOf(caughtUpChapter)
        val newFrontier = ids.indexOf(caughtUpChapter)
        if (caughtUpChapter != null && (newFrontier < 0 || frontier < 0 ||
                catalog.take(frontier + 1) != ids.take(newFrontier + 1))) {
            next = next.copy(caughtUpChapter = null, startedSinceCaughtUp = emptySet())
        }
        next = next.consumeStartedUpdate()
        return if (next.completed && next.chapter == ids.last()) {
            next.copy(caughtUpChapter = next.chapter, startedSinceCaughtUp = emptySet())
        } else next
    }

    private fun consumeStartedUpdate(): ShelfReadingRecord {
        val frontier = caughtUpChapter?.let(catalog::indexOf) ?: return this
        return if (frontier >= 0 && startedSinceCaughtUp.any { catalog.indexOf(it) > frontier }) {
            copy(caughtUpChapter = null, startedSinceCaughtUp = emptySet())
        } else this
    }
}

@Serializable
data class ShelfReadingSnapshot(val records: Map<String, ShelfReadingRecord> = emptyMap()) {
    /** 合并早期按模式分开的记录，以最后一次真实阅读为准；不复活已消费的追更提醒。 */
    fun unified(): ShelfReadingSnapshot = copy(records = records.values.groupBy { it.key.id }.mapValues { (_, values) ->
        values.maxWith(compareBy<ShelfReadingRecord> { it.readAt }.thenBy { if (it.caughtUpChapter == null) 1 else 0 })
    })

    fun books(session: LocalReadingSession?, kind: String): Map<String, ShelfReadingRecord> =
        records.values.filter { it.key.owner == session?.owner && it.key.kind == kind }
            .groupBy { it.key.path }.mapValues { (_, groups) -> groups.maxBy { it.readAt } }
}
