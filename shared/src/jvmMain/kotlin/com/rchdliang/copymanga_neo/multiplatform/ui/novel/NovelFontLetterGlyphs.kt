package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import java.nio.ByteBuffer

/** A minimal alphabet-sized sample, rather than incidental accent remnants in a clock font. */
internal fun novelFontHasReadableLetters(glyphs: Sequence<Int>, hasInk: (Int) -> Boolean): Boolean =
    glyphs.distinct().filter(hasInk).take(8).count() == 8

/** Own glyphs for Unicode letters, across scripts. Null means an unsupported/invalid cmap. */
internal fun novelFontLetterGlyphs(buffer: ByteBuffer, ttcIndex: Int): Sequence<Int>? = runCatching {
    val data = novelOpenTypeTable(buffer, ttcIndex, 0x636d6170) ?: return null // cmap
    fun u16(at: Int) = data.getShort(at).toInt() and 0xffff
    val count = u16(2)
    require(4L + count * 8L <= data.limit())
    val tables = (0 until count).mapNotNull { index ->
        val record = 4 + index * 8
        if (u16(record) != 0 && !(u16(record) == 3 && u16(record + 2) in setOf(1, 10))) return@mapNotNull null
        val start = data.getInt(record + 4)
        if (start < 0 || start.toLong() + 2 > data.limit()) return@mapNotNull null
        start.takeIf { u16(start) in setOf(4, 12, 13) }
    }
    val start = tables.maxByOrNull { if (u16(it) == 4) 0 else 1 } ?: return null
    val format = u16(start)
    if (format == 12 || format == 13) {
        val length = data.getInt(start + 4)
        val groups = data.getInt(start + 12)
        require(length >= 16 && start.toLong() + length <= data.limit() && groups >= 0 && 16L + groups * 12L <= length)
        sequence {
            for (group in 0 until groups) {
                val at = start + 16 + group * 12
                val first = data.getInt(at)
                val last = data.getInt(at + 4)
                val glyph = data.getInt(at + 8)
                require(first in 0..0x10ffff && last in first..0x10ffff && glyph >= 0)
                for (codePoint in first..last) {
                    if (!Character.isLetter(codePoint)) continue
                    val id = if (format == 13) glyph.toLong() else glyph.toLong() + codePoint - first
                    if (id in 1..65535) yield(id.toInt())
                }
            }
        }
    } else {
        val length = u16(start + 2)
        val segments = u16(start + 6) / 2
        require(length >= 16 && start.toLong() + length <= data.limit() && 16L + segments * 8L <= length)
        val ends = start + 14
        val starts = ends + segments * 2 + 2
        val deltas = starts + segments * 2
        val ranges = deltas + segments * 2
        sequence {
            for (segment in 0 until segments) {
                val first = u16(starts + segment * 2)
                val last = u16(ends + segment * 2)
                require(first <= last)
                val delta = data.getShort(deltas + segment * 2).toInt()
                val at = ranges + segment * 2
                val offset = u16(at)
                for (codePoint in first..last) {
                    if (!Character.isLetter(codePoint)) continue
                    val glyph = if (offset == 0) (codePoint + delta) and 0xffff else {
                        val address = at + offset + (codePoint - first) * 2
                        require(address >= start && address.toLong() + 2 <= start.toLong() + length)
                        val raw = u16(address)
                        if (raw == 0) 0 else (raw + delta) and 0xffff
                    }
                    if (glyph != 0) yield(glyph)
                }
            }
        }
    }
}.getOrNull()
