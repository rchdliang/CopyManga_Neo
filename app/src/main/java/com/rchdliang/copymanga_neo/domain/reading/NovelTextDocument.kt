package com.rchdliang.copymanga_neo.domain.reading

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** 保留官方原始行和 UTF-16 字符坐标，不改变正文身份相等性。 */
class NovelTextDocument private constructor(val lines: List<String>) {
    fun validateRange(start: Int, end: Int) {
        require(start >= 0 && end >= start && end <= lines.size) {
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
            val charset = when (encoding.trim().uppercase(Locale.ROOT).replace("_", "-")) {
                "UTF-8", "UTF8" -> Charsets.UTF_8
                "GBK" -> Charset.forName("GBK")
                else -> throw IllegalArgumentException("Unsupported official text encoding: $encoding")
            }
            val skip = if (charset == Charsets.UTF_8 && raw.size >= 3 &&
                raw[0] == 0xef.toByte() && raw[1] == 0xbb.toByte() && raw[2] == 0xbf.toByte()
            ) 3 else 0
            val text = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(raw, skip, raw.size - skip)).toString()
            // Kotlin split 保留末尾空行；空正文也保留一行，保证既有行号可恢复。
            return NovelTextDocument(text.split("\r\n", "\n", "\r"))
        }

        fun sectionKey(volume: String, type: Int, start: Int, end: Int, imageUrl: String?): String =
            "$volume:$type:$start:$end:" + sha256(imageUrl.orEmpty())

        fun sha256(text: String): String =
            ReadingContentHash.sha256(text.toByteArray(Charsets.UTF_8))
    }
}
