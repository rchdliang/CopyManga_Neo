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

data class ShelfChapter(val id: String, val name: String, val group: String = "", val latest: Boolean = false)

/** 续读位置、追更资格与本轮提醒分别保存；云端章节位置不代表读完或曾经追平。 */
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
    val latestChapterId: String = "",
    val knownWhenCaughtUp: Set<String> = emptySet(),
    /** 兼容旧存档；迁移时撤销这种缺乏阅读证据的追平假设。 */
    val assumedFromCloud: Boolean = false,
    // 保留离线期间实际读过的章节，联网后再判定是否已开始追平基线之后的新内容。
    val startedSinceCaughtUp: Set<String> = emptySet(),
    /** 已确认这一轮提醒时的目录；回读其中章节不会消除下一轮提醒。 */
    val knownWhenAcknowledged: Set<String> = emptySet()
) {
    val status: ShelfReadingStatus get() {
        val latest = latestChapterId.ifBlank { catalog.lastOrNull().orEmpty() }
        return when {
            caughtUpChapter != null && latest.isNotBlank() && latest != caughtUpChapter &&
                !assumedFromCloud && latest !in knownWhenCaughtUp && latest !in knownWhenAcknowledged -> ShelfReadingStatus.UPDATED
            chapter.isBlank() -> ShelfReadingStatus.UNREAD
            caughtUpChapter != null && !assumedFromCloud && completed &&
                chapter == caughtUpChapter && chapter == latest -> ShelfReadingStatus.CAUGHT_UP
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
        if (chapters.count { it.latest } > 1) return this
        val explicitLatest = chapters.singleOrNull { it.latest }
        val latest = explicitLatest ?: chapters.last()
        var next = copy(catalog = ids, latestName = latest.name, latestChapterId = latest.id)
        // 删除、重编号或换目录不能用“不相等”推断新增。
        val frontier = catalog.indexOf(caughtUpChapter)
        val newFrontier = ids.indexOf(caughtUpChapter)
        if (caughtUpChapter != null && !assumedFromCloud && (newFrontier < 0 ||
                explicitLatest == null && (frontier < 0 || catalog.take(frontier + 1) != ids.take(newFrontier + 1)))) {
            next = next.copy(caughtUpChapter = null, startedSinceCaughtUp = emptySet(), knownWhenAcknowledged = emptySet())
        }
        next = next.consumeStartedUpdate()
        return if (next.completed && next.chapter == latest.id) {
            next.copy(caughtUpChapter = next.chapter, startedSinceCaughtUp = emptySet(),
                knownWhenCaughtUp = ids.toSet(), assumedFromCloud = false, knownWhenAcknowledged = emptySet())
        } else next
    }

    private fun consumeStartedUpdate(): ShelfReadingRecord {
        val frontier = caughtUpChapter ?: return this
        val latest = latestChapterId.ifBlank { catalog.lastOrNull().orEmpty() }
        // 离线打开的新章先保留事件，目录确认后再消除本轮提醒；回读旧章不消除。
        return if (!assumedFromCloud && latest.isNotBlank() && latest != frontier &&
            startedSinceCaughtUp.any { it in catalog && it !in knownWhenCaughtUp && it !in knownWhenAcknowledged }) {
            copy(knownWhenAcknowledged = catalog.toSet(), startedSinceCaughtUp = emptySet())
        } else this
    }

    /** 撤销旧版首次云端导入产生的假追平，不影响真实阅读获得的资格。 */
    fun withoutCloudAssumption(): ShelfReadingRecord =
        if (!assumedFromCloud) this else copy(caughtUpChapter = null, knownWhenCaughtUp = emptySet(),
            assumedFromCloud = false, startedSinceCaughtUp = emptySet(), knownWhenAcknowledged = emptySet())

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
