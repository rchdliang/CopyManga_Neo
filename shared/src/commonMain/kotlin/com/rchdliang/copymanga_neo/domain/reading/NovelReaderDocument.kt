package com.rchdliang.copymanga_neo.domain.reading

import com.rchdliang.copymanga_neo.domain.model.NovelSection

/** 展示序号与官方原始行号分离，插图不会覆盖、删除或重排原始正文。 */
sealed interface NovelReaderEntry {
    val key: String

    data class Text(val line: Int, val text: String) : NovelReaderEntry {
        override val key = "text:$line"
    }

    data class Image(val section: NovelSection, val line: Int) : NovelReaderEntry {
        override val key = "image:${section.key}"
    }
}

class NovelReaderDocument(val source: NovelTextDocument, sections: List<NovelSection>) {
    val images = sections.filter { it.type == 2 && !it.image.isNullOrBlank() }.distinctBy { it.key }
    val entries: List<NovelReaderEntry>
    private val textItems = IntArray(source.lines.size)
    private val imageItems = mutableMapOf<String, Int>()

    init {
        val atLine = images.mapNotNull { section ->
            inlineImageLine(section, source.lines.size)?.let { it to section }
        }.groupBy({ it.first }, { it.second })
        entries = buildList {
            for (line in 0..source.lines.size) {
                atLine[line].orEmpty().forEach { section ->
                    imageItems[section.key] = size
                    add(NovelReaderEntry.Image(section, line))
                }
                if (line < source.lines.size) {
                    textItems[line] = size
                    add(NovelReaderEntry.Text(line, source.lines[line]))
                }
            }
        }
    }

    fun textItem(line: Int) = textItems[source.clampLine(line)]
    fun imageItem(key: String): Int? = imageItems[key]
    fun sectionAt(line: Int, sections: List<NovelSection>): String =
        sections.firstOrNull { it.type == 1 && line >= it.start && line < it.end }?.key.orEmpty()
}

/** 0/0 无位置；一端为 0 使用另一端；两端均非 0 使用起始行。负数/空值不插入。 */
fun inlineImageLine(section: NovelSection, lineCount: Int): Int? {
    if (section.type != 2 || section.image.isNullOrBlank() || section.start < 0 || section.end < 0) return null
    val line = if (section.start != 0) section.start else section.end
    return line.takeIf { it > 0 && it <= lineCount }
}
