package com.rchdliang.copymanga_neo.ui.reader

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
