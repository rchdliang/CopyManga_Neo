package com.rchdliang.copymanga_neo.multiplatform.ui.reader

/** 只保留相邻章节；加载期间的额外槽位避免在手势尚未结束时裁剪列表。 */
internal object ReaderWindowPolicy {
    const val MAX_RESIDENT_CHAPTERS = 5
    const val PAGE_STATE_RADIUS = 4

    /** 列表裁剪/前插后，下标会改变；仍用已布局页面的稳定 key 识别当前内容。 */
    fun pageAt(pages: List<ReaderPageEntry>, index: Int, laidOutKey: Any?): ReaderPageEntry? =
        pages.firstOrNull { it.key == laidOutKey } ?: pages.getOrNull(index)

    fun <T> around(items: List<T>, active: String, key: (T) -> String): List<T> {
        val index = items.indexOfFirst { key(it) == active }
        if (index < 0) return items.take(MAX_RESIDENT_CHAPTERS)
        return items.subList((index - 1).coerceAtLeast(0), (index + 2).coerceAtMost(items.size)).toList()
    }

    fun pageKeys(pages: List<ReaderPageEntry>, index: Int, visible: Set<String> = emptySet()): Set<String> {
        if (pages.isEmpty()) return emptySet()
        val current = index.coerceIn(pages.indices)
        return pages.subList((current - PAGE_STATE_RADIUS).coerceAtLeast(0),
            (current + PAGE_STATE_RADIUS + 1).coerceAtMost(pages.size)).mapTo(mutableSetOf()) { it.key } + visible
    }
}
