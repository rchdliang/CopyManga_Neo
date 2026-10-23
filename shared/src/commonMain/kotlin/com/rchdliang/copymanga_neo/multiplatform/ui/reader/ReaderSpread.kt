package com.rchdliang.copymanga_neo.multiplatform.ui.reader

/** Display groups contain raw list indices. Never persist a group index as reading progress. */
internal data class ReaderSpread(val indices: List<Int>) {
    fun visualIndices(rtl: Boolean) = if (rtl) indices.reversed() else indices
    fun anchor(preferred: Int): Int = preferred.takeIf { it in indices } ?: indices.first()
}

internal fun readerUsesSpreads(mode: String, widthDp: Float, heightDp: Float): Boolean =
    mode != "SINGLE" && widthDp >= (if (mode == "AUTO") 840f else 600f) && widthDp > heightDp

/** A restart creates a boundary before that original page; multiple boundaries are independent. */
internal fun readerSpreads(
    pages: List<ReaderPageEntry>, enabled: Boolean,
    wideKeys: Set<String> = emptySet(), restarts: Map<String, List<Int>> = emptyMap()
): List<ReaderSpread> = buildList {
    var index = 0
    while (index < pages.size) {
        val page = pages[index]
        val next = pages.getOrNull(index + 1)
        val breaks = restarts[page.chapterUuid].orEmpty()
        val pair = enabled && page.source != null && next?.source != null &&
            page.chapterUuid == next.chapterUuid && next.page == page.page + 1 &&
            (page.page != 0 || 0 in breaks) && next.page !in breaks &&
            page.key !in wideKeys && next.key !in wideKeys
        add(ReaderSpread(if (pair) listOf(index, index + 1) else listOf(index)))
        index += if (pair) 2 else 1
    }
}

internal fun List<ReaderSpread>.spreadIndex(rawIndex: Int): Int =
    indexOfFirst { rawIndex in it.indices }.coerceAtLeast(0)

internal fun List<ReaderSpread>.rawIndex(groupIndex: Int, preferred: Int): Int =
    getOrNull(groupIndex)?.anchor(preferred) ?: 0

internal fun readerWideImage(width: Float, height: Float) =
    width.isFinite() && height.isFinite() && height > 0 && width > height * 1.15f
