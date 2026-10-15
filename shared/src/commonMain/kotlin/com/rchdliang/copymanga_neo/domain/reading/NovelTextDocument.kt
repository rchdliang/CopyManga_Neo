package com.rchdliang.copymanga_neo.domain.reading


/** 保留官方原始行和 UTF-16 字符坐标，不改变正文身份相等性。 */
class NovelTextDocument private constructor(val lines: List<String>) {
    fun validateRange(start: Int, end: Int) {
        require(start in 0..end && end <= lines.size) {
            "Official contents line range is outside the downloaded volume"
        }
    }

    fun clampLine(line: Int): Int = line.coerceIn(0, lines.lastIndex)

    fun clampCharacter(line: Int, offset: Int): Int {
        val text = lines[clampLine(line)]
        val at = offset.coerceIn(0, text.length)
        return if (at > 0 && at < text.length && text[at].isLowSurrogate() && text[at - 1].isHighSurrogate()) at - 1 else at
    }

    companion object {
        fun decode(raw: ByteArray, encoding: String): NovelTextDocument {
            val text = decodeNovelText(raw, encoding).removePrefix("\uFEFF")
            // Kotlin split 保留末尾空行；空正文也保留一行，保证既有行号可恢复。
            return NovelTextDocument(text.split("\r\n", "\n", "\r"))
        }

        fun sectionKey(volume: String, type: Int, start: Int, end: Int, imageUrl: String?): String =
            "$volume:$type:$start:$end:" + sha256(imageUrl.orEmpty())

        fun sha256(text: String): String =
            ReadingContentHash.sha256(text.encodeToByteArray())
    }
}
