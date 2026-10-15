package com.rchdliang.copymanga_neo.domain.reading

import com.rchdliang.copymanga_neo.domain.model.NovelTextRange

data class NovelTextMatch(
    val range: NovelTextRange, val excerpt: String,
    val matchStart: Int = 0, val matchEnd: Int = 0
)

data class NovelTextSearchResult(
    val matches: List<NovelTextMatch>,
    val total: Int,
    val paragraphs: Int = 0
)

/** 不修剪或重排原始行；选区失效时返回 null，避免把标注落到另一个重复句子。 */
fun NovelTextDocument.textIn(range: NovelTextRange): String? {
    if (!range.valid || range.startLine !in lines.indices || range.endLine !in lines.indices) return null
    if (range.startCharacter > lines[range.startLine].length || range.endCharacter > lines[range.endLine].length) return null
    if (clampCharacter(range.startLine, range.startCharacter) != range.startCharacter ||
        clampCharacter(range.endLine, range.endCharacter) != range.endCharacter
    ) return null
    return if (range.startLine == range.endLine) lines[range.startLine].substring(
        range.startCharacter,
        range.endCharacter
    )
    else buildString {
        append(lines[range.startLine].substring(range.startCharacter))
        for (line in range.startLine + 1 until range.endLine) {
            append('\n'); append(lines[line])
        }
        append('\n'); append(lines[range.endLine].substring(0, range.endCharacter))
    }
}

/** 本卷内字面查找，保留重复及重叠结果；结果列表有上限，仍统计完整命中数。 */
fun NovelTextDocument.findMatches(
    query: String, limit: Int = 500, checkCancellation: () -> Unit = {}
): NovelTextSearchResult {
    require(limit > 0)
    if (query.isBlank()) return NovelTextSearchResult(emptyList(), 0)
    val starts = IntArray(lines.size)
    var offset = 0
    lines.forEachIndexed { index, line -> starts[index] = offset; offset += line.length + 1 }
    val text = lines.joinToString("\n")
    val matches = ArrayList<NovelTextMatch>()
    var total = 0
    var paragraphs = 0
    var previousParagraph = -1
    var from = 0
    fun lineAt(at: Int): Int {
        var low = 0
        var high = starts.lastIndex
        while (low <= high) {
            val middle = low + (high - low) / 2
            if (starts[middle] <= at) low = middle + 1 else high = middle - 1
        }
        return high.coerceAtLeast(0)
    }
    while (from <= text.length - query.length) {
        checkCancellation()
        val at = text.indexOf(query, from, ignoreCase = true)
        if (at < 0) break
        val end = at + query.length
        val first = lineAt(at)
        val last = lineAt(end)
        val range = NovelTextRange(first, at - starts[first], last, end - starts[last])
        if (textIn(range) != null) {
            total++
            if (first != previousParagraph) {
                paragraphs++; previousParagraph = first
            }
            if (matches.size < limit) {
                var excerptStart = (at - 24).coerceAtLeast(0)
                var excerptEnd = (end + 48).coerceAtMost(text.length)
                if (excerptStart > 0 && text[excerptStart].isLowSurrogate() && text[excerptStart - 1].isHighSurrogate()) excerptStart--
                if (excerptEnd < text.length && text[excerptEnd].isLowSurrogate() && text[excerptEnd - 1].isHighSurrogate()) excerptEnd++
                val excerpt = text.substring(excerptStart, excerptEnd)
                    .replace('\n', ' ')
                matches += NovelTextMatch(range, excerpt, at - excerptStart, end - excerptStart)
            }
        }
        from = at + 1
    }
    return NovelTextSearchResult(matches, total, paragraphs)
}
