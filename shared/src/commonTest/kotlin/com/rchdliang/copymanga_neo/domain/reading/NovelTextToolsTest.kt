package com.rchdliang.copymanga_neo.domain.reading

import com.rchdliang.copymanga_neo.domain.model.NovelTextRange
import kotlinx.coroutines.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class NovelTextToolsTest {
    private fun document(text: String) = NovelTextDocument.decode(text.encodeToByteArray(), "UTF-8")

    @Test
    fun multilineRangePreservesEmptyLines() {
        assertEquals("甲\n\n乙", document("开头甲\n\n乙结束").textIn(NovelTextRange(0, 2, 2, 1)))
    }

    @Test
    fun invalidSourceRangeIsRejected() {
        val doc = document("AB\nCD")
        assertNull(doc.textIn(NovelTextRange(0, 0, 4, 1)))
        assertNull(doc.textIn(NovelTextRange(0, 3, 1, 1)))
        assertNull(doc.textIn(NovelTextRange(0, 1, 0, 1)))
    }

    @Test
    fun halfSurrogateIsNotAValidAnnotationBoundary() {
        val doc = document("A😀B")
        assertNull(doc.textIn(NovelTextRange(0, 2, 0, 3)))
        assertEquals("😀", doc.textIn(NovelTextRange(0, 1, 0, 3)))
    }

    @Test
    fun repeatedAndOverlappingMatchesKeepDistinctCoordinates() {
        val result = document("人人人").findMatches("人人")
        assertEquals(2, result.total)
        assertEquals(listOf(0, 1), result.matches.map { it.range.startCharacter })
    }

    @Test
    fun searchAcrossLineBoundaryKeepsOriginalLineAndCharacter() {
        val doc = document("前文\n后文")
        val match = doc.findMatches("文\n后").matches.single()
        assertEquals(NovelTextRange(0, 1, 1, 1), match.range)
        assertEquals("文\n后", doc.textIn(match.range))
    }

    @Test
    fun searchIsLiteralAndCaseInsensitive() {
        val doc = document("A.b[] aXb[] a.B[]")
        val result = doc.findMatches("a.b[]")
        assertEquals(2, result.total)
        assertEquals(listOf("A.b[]", "a.B[]"), result.matches.map { doc.textIn(it.range) })
    }

    @Test
    fun limitedResultsStillCountAllMatches() {
        val result = document("呀".repeat(100)).findMatches("呀", limit = 2)
        assertEquals(100, result.total)
        assertEquals(2, result.matches.size)
    }

    @Test
    fun whitespaceDoesNotMatchEveryPosition() {
        assertEquals(0, document("一 二").findMatches(" ").total)
        assertEquals(0, document("文字").findMatches("").total)
    }

    @Test
    fun searchExcerptDoesNotSplitEmojiAtEitherEdge() {
        val text = "😀" + "x".repeat(23) + "中" + "x".repeat(47) + "😀"
        val excerpt = document(text).findMatches("中").matches.single().excerpt
        assertTrue(excerpt.startsWith("😀"))
        assertTrue(excerpt.endsWith("😀"))
    }

    @Test
    fun largeSearchCooperatesWithCancellation() {
        var checked = 0
        kotlin.test.assertFailsWith<CancellationException> {
            document("中".repeat(10000)).findMatches("中") {
                if (++checked > 10) throw CancellationException()
            }
        }
    }
}
