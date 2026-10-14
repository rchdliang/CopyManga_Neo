package com.rchdliang.copymanga_neo.domain.reading

/** 页面片段保留原始文字坐标，分页序号永远不作为持久阅读位置。 */
data class NovelPageBlock(
    val entry: NovelReaderEntry,
    val character: Int = 0,
    val text: String = "",
    val heightPx: Int,
    val spacingPx: Int = 0
)

data class NovelReaderPage(val blocks: List<NovelPageBlock>)

fun novelPageFor(pages: List<NovelReaderPage>, line: Int, character: Int, section: String): Int {
    if (section.isNotEmpty()) {
        val imagePage = pages.indexOfFirst { page ->
            page.blocks.any { (it.entry as? NovelReaderEntry.Image)?.section?.key == section }
        }
        if (imagePage >= 0) return imagePage
    }
    val exact = pages.indexOfFirst { page ->
        page.blocks.any {
            val text = it.entry as? NovelReaderEntry.Text
            text?.line == line && character >= it.character &&
                    character < it.character + it.text.length.coerceAtLeast(1)
        }
    }
    if (exact >= 0) return exact
    val following = pages.indexOfFirst { page ->
        page.blocks.any {
            val text = it.entry as? NovelReaderEntry.Text
            text != null && (text.line > line || text.line == line && it.character >= character)
        }
    }
    return if (following >= 0) following else pages.lastIndex.coerceAtLeast(0)
}
