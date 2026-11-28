package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.lazy.LazyListLayoutInfo

internal data class ReaderDisplayedPosition(val page: ReaderPageEntry, val rangeLabel: String?)

/** 手动定位的目标完整可见时优先显示；滚动时按可见高度选页，平局取上方。 */
internal fun LazyListLayoutInfo.mostVisiblePage(pages: List<ReaderPageEntry>, selectedKey: String? = null): ReaderPageEntry? {
    val selected = selectedKey?.let { key -> visibleItemsInfo.firstOrNull {
        it.key == key && it.size > 0 && it.offset >= viewportStartOffset && it.offset + it.size <= viewportEndOffset
    } }
    val item = selected ?: visibleItemsInfo.maxByOrNull {
        minOf(it.offset + it.size, viewportEndOffset) - maxOf(it.offset, viewportStartOffset)
    } ?: return null
    if (item.size <= 0 || item.offset >= viewportEndOffset || item.offset + item.size <= viewportStartOffset) return null
    return pages.getOrNull(item.index)?.takeIf { it.key == item.key } ?: pages.firstOrNull { it.key == item.key }
}

/** 跨章或进入章末评论前先读完整张边界图；预取是否已将相邻章节加入列表不影响翻动距离。 */
internal fun LazyListLayoutInfo.chapterEdgeScroll(
    page: ReaderPageEntry, adjacent: ReaderPageEntry?, forward: Boolean
): Float {
    if (adjacent?.chapterUuid == page.chapterUuid && adjacent.source != null) return 0f
    val item = visibleItemsInfo.firstOrNull { it.key == page.key } ?: return 0f
    val remaining = if (forward) item.offset + item.size - viewportEndOffset else viewportStartOffset - item.offset
    if (remaining <= 0) return 0f
    val distance = minOf(remaining.toFloat(), (viewportEndOffset - viewportStartOffset) * 0.9f)
    return if (forward) distance else -distance
}

/** 只有下标、实际测量的分组和可绘制的分组一致时，才发布新页码。 */
internal fun ReaderSpread.displayedPosition(
    pages: List<ReaderPageEntry>, measuredKey: Any?, displayReadyKey: String?, preferredIndex: Int
): ReaderDisplayedPosition? {
    val groupKey = key(pages)
    if (measuredKey != groupKey || displayReadyKey != groupKey) return null
    return ReaderDisplayedPosition(pages[anchor(preferredIndex)],
        readerPageRangeLabel(indices.mapNotNull { index -> pages[index].takeIf { it.source != null }?.page?.plus(1) }))
}

/** 优先保留稳定页标识；刷新页数减少时停在原章节的最后一张图片。 */
internal fun resolveReaderPageIndex(
    pages: List<ReaderPageEntry>,
    anchorKey: String?,
    chapterUuid: String?,
    page: Int,
    initial: Int
): Int {
    if (pages.isEmpty()) return 0
    anchorKey?.let { key ->
        pages.indexOfFirst { it.key == key }.takeIf { it >= 0 }?.let { return it }
    }
    val chapterImages = pages.withIndex().filter {
        it.value.chapterUuid == chapterUuid && it.value.source != null
    }
    if (chapterImages.isNotEmpty()) {
        return chapterImages.firstOrNull { it.value.page == page }?.index
            ?: chapterImages[page.coerceIn(chapterImages.indices)].index
    }
    return initial.coerceIn(pages.indices)
}
