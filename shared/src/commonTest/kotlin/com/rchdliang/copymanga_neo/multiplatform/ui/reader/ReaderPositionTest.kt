package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/** Original stable-anchor fixtures now exercise the shared production helper. */
class ReaderPositionTest {
    private val pages = listOf(
        ReaderPageEntry("a:image:0", "a", "A", "one", 0, 2),
        ReaderPageEntry("a:image:1", "a", "A", "two", 1, 2),
        ReaderPageEntry("a:comments", "a", "A", null, 1, 2),
        ReaderPageEntry("b:image:0", "b", "B", "three", 0, 1)
    )

    @Test
    fun bookmarkTargetsChapterPageInsteadOfFlattenedIndex() {
        assertEquals(3, resolveReaderPageIndex(pages, null, "b", 0, 0))
    }

    @Test
    fun stalePageStopsAtLastImageOfItsChapter() {
        assertEquals(1, resolveReaderPageIndex(pages, null, "a", 20, 0))
    }

    @Test
    fun commentsAndStableKeysSurviveAppend() {
        assertEquals(2, resolveReaderPageIndex(pages, "a:comments", "a", 1, 0))
    }

    @Test
    fun removedCommentReturnsToSameChapterLastImage() {
        assertEquals(
            1,
            resolveReaderPageIndex(pages.filter { it.source != null }, "a:comments", "a", 1, 0)
        )
    }

    @Test
    fun indicatorRejectsStaleChapterAndProvisionalPair() {
        val provisional = ReaderSpread(listOf(0, 1))
        val actual = ReaderSpread(listOf(1))
        val previousChapterKey = ReaderSpread(listOf(3)).key(pages)
        assertNull(provisional.displayedPosition(pages, previousChapterKey, provisional.key(pages), 1))
        assertNull(provisional.displayedPosition(pages, provisional.key(pages), null, 1))
        assertEquals(ReaderDisplayedPosition(pages[1], "2"),
            actual.displayedPosition(pages, actual.key(pages), actual.key(pages), 1))
    }
}
