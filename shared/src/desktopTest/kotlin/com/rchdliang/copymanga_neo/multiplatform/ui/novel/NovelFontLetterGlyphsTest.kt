package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NovelFontLetterGlyphsTest {
    @Test
    fun clockFontsWithOnlyAccentRemnantsAreNotTreatedAsReadableText() {
        assertFalse(novelFontHasReadableLetters((1..123).asSequence()) { it <= 4 })
        assertFalse(novelFontHasReadableLetters(generateSequence { 1 }.take(100)) { true })
        assertTrue(novelFontHasReadableLetters((1..100).asSequence()) { it >= 50 })
    }
    @Test
    fun formatTwelveIncludesNonLatinAndSupplementaryLettersButNotClockDigits() {
        val subtable = ByteBuffer.allocate(16 + 4 * 12)
        subtable.putShort(12).putShort(0).putInt(subtable.capacity()).putInt(0).putInt(4)
        for ((codePoint, glyph) in listOf(0x31 to 2, 0x41 to 3, 0x4e2d to 20, 0x10400 to 40)) {
            subtable.putInt(codePoint).putInt(codePoint).putInt(glyph)
        }
        assertEquals(listOf(3, 20, 40), novelFontLetterGlyphs(font(subtable.array()), 0)?.toList())
    }

    @Test
    fun formatFourAppliesRangeOffsetsAndDeltasWithoutTurningMissingGlyphsIntoLetters() {
        val table = ByteBuffer.allocate(52)
        table.putShort(4).putShort(52).putShort(0).putShort(8)
        table.putShort(0).putShort(0).putShort(0)
        listOf(0x39, 0x42, 0x4e2d, 0xffff).forEach { table.putShort(it.toShort()) } // endCode
        table.putShort(0)
        listOf(0x30, 0x41, 0x4e2d, 0xffff).forEach { table.putShort(it.toShort()) } // startCode
        listOf(0, 5, 0, 1).forEach { table.putShort(it.toShort()) }
        listOf(0, 6, 0, 0).forEach { table.putShort(it.toShort()) } // array starts 6 bytes after second rangeOffset
        table.putShort(4).putShort(0)
        assertEquals(listOf(9, 0x4e2d), novelFontLetterGlyphs(font(table.array()), 0)?.toList())
    }

    @Test
    fun digitOnlyIsDifferentFromAnUnreadableCmap() {
        val table = ByteBuffer.allocate(28)
        table.putShort(12).putShort(0).putInt(28).putInt(0).putInt(1)
        table.putInt(0x30).putInt(0x39).putInt(1)
        assertTrue(novelFontLetterGlyphs(font(table.array()), 0)!!.none())
        assertNull(novelFontLetterGlyphs(ByteBuffer.allocate(3), 0))
    }

    private fun font(subtable: ByteArray): ByteBuffer {
        val data = ByteBuffer.allocate(40 + subtable.size)
        data.putInt(0x00010000).putShort(1).position(12)
        data.putInt(0x636d6170).putInt(0).putInt(28).putInt(12 + subtable.size)
        data.putShort(0).putShort(1).putShort(0).putShort(4).putInt(12)
        data.put(subtable).position(0)
        return data
    }
}
