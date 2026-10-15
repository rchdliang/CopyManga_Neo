package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import com.rchdliang.copymanga_neo.domain.model.NovelTextRange
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class NovelSourceSelectionTest {
    private fun document(text: String) = NovelTextDocument.decode(text.encodeToByteArray(), "UTF-8")

    @Test
    fun identicalWordsInsideSameParagraphResolveToActualSelectedOccurrence() {
        val doc = document("卷首\n" + "x".repeat(20) + "重复重复")
        val selected = novelSourceText("重复重复", 1, 20).subSequence(2, 4)
        val target = novelSelectedSource(listOf(selected), doc)!!
        assertEquals("重复", target.text)
        assertEquals(NovelTextRange(1, 22, 1, 24), target.range)
    }

    @Test
    fun sourceSelectionUsesUtf16OffsetsForEmoji() {
        val doc = document("A😀B")
        val selected = novelSourceText("A😀B", 0).subSequence(1, 3)
        val target = novelSelectedSource(listOf(selected), doc)!!
        assertEquals("😀", target.text)
        assertEquals(NovelTextRange(0, 1, 0, 3), target.range)
    }

    @Test
    fun selectionAcrossEmptyParagraphsUsesRawTextRatherThanRenderedNonbreakingSpace() {
        val doc = document("开头甲\n\n乙结束")
        val target = novelSelectedSource(
            listOf(
                novelSourceText(doc.lines[0], 0).subSequence(2, 3),
                novelSourceText("", 1),
                novelSourceText(doc.lines[2], 2).subSequence(0, 1)
            ), doc
        )!!
        assertEquals("甲\n\n乙", target.text)
        assertEquals(NovelTextRange(0, 2, 2, 1), target.range)
    }

    @Test
    fun emptyAndUnanchoredSelectionCannotCreateAnnotation() {
        assertNull(novelSelectedSource(emptyList(), document("正文")))
        assertNull(novelSelectedSource(listOf(novelSourceText("", 0)), document("")))
    }

    @Test
    fun highlightIsClippedToCurrentPageFragment() {
        val range = NovelTextRange(2, 20, 2, 40)
        assertEquals(0 to 10, novelRangeSegment(range, 2, 30, 30))
        assertEquals(10 to 30, novelRangeSegment(range, 2, 10, 30))
        assertNull(novelRangeSegment(range, 3, 0, 30))
    }

    @Test
    fun finalEmptyLineOfMultilineSelectionDoesNotHighlightUnselectedText() {
        val range = NovelTextRange(0, 2, 2, 0)
        assertEquals(2 to 5, novelRangeSegment(range, 0, 0, 5))
        assertEquals(0 to 5, novelRangeSegment(range, 1, 0, 5))
        assertNull(novelRangeSegment(range, 2, 0, 5))
    }
    @Test
    fun clipboardMatchesComposeSelectionJoiningIncludingEmptySelectable() {
        val parts = listOf(
            novelSourceText("甲", 0),
            novelSourceText("", 1),
            novelSourceText("😀乙", 2)
        )
        val copied = novelClipboardText(parts)
        assertEquals("甲\n\u00a0\n😀乙", copied.text)
        assertEquals(3, copied.getStringAnnotations("novel-source", 4, copied.length).size)
    }

    @Test
    fun emptyClipboardSelectionStaysEmpty() {
        assertEquals("", novelClipboardText(emptyList()).text)
    }
}
