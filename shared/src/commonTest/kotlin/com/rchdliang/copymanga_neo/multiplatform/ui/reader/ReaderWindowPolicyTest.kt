package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import kotlin.test.*

class ReaderWindowPolicyTest {
    private fun pages(chapter: String) = (0..19).map {
        ReaderPageEntry("$chapter:image:$it", chapter, chapter, "$chapter/$it", it, 20)
    } + ReaderPageEntry("$chapter:comments", chapter, chapter, null, 19, 20)

    @Test fun stableImageAndCommentAnchorsSurvivePrependAndTrim() {
        val old = pages("b") + pages("c")
        val expanded = pages("a") + old
        for (key in listOf("b:image:12", "b:comments")) {
            val index = old.indexOfFirst { it.key == key }
            assertEquals(key, ReaderWindowPolicy.pageAt(expanded, index, key)?.key)
            val resolved = resolveReaderPageIndex(expanded, key, "b", 12, index)
            assertEquals(key, expanded[resolved].key)
            val trimmed = expanded.filter { it.chapterUuid != "a" }
            assertEquals(key, ReaderWindowPolicy.pageAt(trimmed, resolved, key)?.key)
        }
    }

    @Test fun longReadingSessionRetainsOnlyNeighborsInEitherDirection() {
        val chapters = (0..500).map(Int::toString)
        for (active in chapters + chapters.reversed()) {
            val window = ReaderWindowPolicy.around(chapters, active) { it }
            assertTrue(window.size <= 3)
            assertTrue(active in window)
            assertTrue(window.all { kotlin.math.abs(it.toInt() - active.toInt()) <= 1 })
        }
    }

    @Test fun pageStateTracksViewportWithoutAccumulatingVisitedPages() {
        val pages = (0..20).flatMap { pages(it.toString()) }
        val retained = mutableSetOf<String>()
        for (index in pages.indices) {
            val visible = pages.subList(index, minOf(index + 12, pages.size)).map { it.key }.toSet()
            val keep = ReaderWindowPolicy.pageKeys(pages, index, visible)
            retained.retainAll(keep)
            retained.addAll(visible)
            assertTrue(retained.containsAll(visible))
            assertTrue(retained.size <= 16)
        }
        assertTrue(ReaderWindowPolicy.pageKeys(emptyList(), 0).isEmpty())
    }
}
