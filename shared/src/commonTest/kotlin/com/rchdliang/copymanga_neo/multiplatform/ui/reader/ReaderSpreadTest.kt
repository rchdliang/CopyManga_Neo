package com.rchdliang.copymanga_neo.multiplatform.ui.reader
import kotlin.test.*

class ReaderSpreadTest {
    private fun pages(count: Int, chapter: String = "chapter") = (0 until count).map {
        ReaderPageEntry("$chapter:image:$it", chapter, "Chapter", "image-$it", it, count)
    }
    private fun groups(count: Int, breaks: List<Int> = emptyList(), wide: Set<Int> = emptySet()) =
        readerSpreads(pages(count), true, wide.map { "chapter:image:$it" }.toSet(), mapOf("chapter" to breaks))
            .map { it.indices }

    @Test fun coverAndOddTailKeepEveryOriginalExactlyOnce() {
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4)), groups(5))
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)), groups(6))
        assertEquals(emptyList<List<Int>>(), groups(0))
        assertEquals(listOf(listOf(0)), groups(1))
    }
    @Test fun rtlChangesVisualOrderWithoutChangingOriginalIdentity() {
        val plan = readerSpreads(pages(3), true)
        assertEquals(listOf(1, 2), plan[1].visualIndices(false))
        assertEquals(listOf(2, 1), plan[1].visualIndices(true))
        assertEquals(1, plan.spreadIndex(2))
        assertEquals(2, plan.rawIndex(1, 2))
    }
    @Test fun wideImagesAreNeverPairedOnEitherSide() {
        assertEquals(listOf(listOf(0), listOf(1), listOf(2), listOf(3, 4), listOf(5)), groups(6, wide = setOf(2)))
        assertEquals(listOf(listOf(0), listOf(1), listOf(2, 3)), groups(4, wide = setOf(1)))
        assertTrue(readerWideImage(1600f, 1000f))
        assertFalse(readerWideImage(800f, 1200f))
        assertFalse(readerWideImage(Float.NaN, 100f))
    }
    @Test fun multipleLocalRestartsHandleInsertionsAndCanBeRemovedIndependently() {
        assertEquals(listOf(listOf(0), listOf(1), listOf(2, 3), listOf(4), listOf(5, 6), listOf(7)), groups(8, listOf(2, 5)))
        assertEquals(listOf(listOf(0), listOf(1), listOf(2, 3), listOf(4, 5), listOf(6, 7)), groups(8, listOf(2)))
        assertEquals(groups(8), groups(8, emptyList()))
        assertEquals(listOf(listOf(0, 1), listOf(2)), groups(3, listOf(0)))
    }
    @Test fun restartCannotForceAWideImageIntoAPair() {
        assertEquals(listOf(listOf(0), listOf(1), listOf(2), listOf(3, 4)), groups(5, listOf(2), setOf(2)))
    }
    @Test fun chapterBoundariesAndCommentsAreAlwaysSeparate() {
        val entries = pages(2, "a") + ReaderPageEntry("a:comments", "a", "A", null, 1, 2) + pages(3, "b")
        val plan = readerSpreads(entries, true)
        assertEquals(listOf(listOf(0), listOf(1), listOf(2), listOf(3), listOf(4, 5)), plan.map { it.indices })
    }
    @Test fun allJumpHistoryAndBookmarkPagesRoundTripAcrossModesAndReflow() {
        val entries = pages(13)
        val plans = listOf(readerSpreads(entries, false), readerSpreads(entries, true),
            readerSpreads(entries, true, setOf(entries[4].key), mapOf("chapter" to listOf(2, 7))))
        for (original in entries.indices) for (plan in plans) {
            val restored = resolveReaderPageIndex(entries, entries[original].key, "chapter", original, 0)
            assertEquals(original, plan.rawIndex(plan.spreadIndex(restored), restored))
        }
        for (plan in plans) assertEquals(entries.indices.toList(), plan.flatMap { it.indices })
    }
    @Test fun modeChoiceSurvivesNarrowPortraitAndAutoThreshold() {
        assertFalse(readerUsesSpreads("SINGLE", 1400f, 900f))
        assertTrue(readerUsesSpreads("DOUBLE", 700f, 400f))
        assertFalse(readerUsesSpreads("DOUBLE", 599f, 400f))
        assertFalse(readerUsesSpreads("DOUBLE", 900f, 1200f))
        assertFalse(readerUsesSpreads("AUTO", 839f, 500f))
        assertTrue(readerUsesSpreads("AUTO", 1200f, 800f))
    }
    @Test fun groupingInvariantForEveryRestartAndWidePosition() {
        val entries = pages(20)
        for (restart in 0..19) for (wide in 0..19) {
            val plan = readerSpreads(entries, true, setOf(entries[wide].key), mapOf("chapter" to listOf(restart)))
            assertEquals(entries.indices.toList(), plan.flatMap { it.indices })
            assertTrue(plan.all { it.indices.size in 1..2 })
            assertEquals(1, plan[plan.spreadIndex(wide)].indices.size)
            assertEquals(restart, plan[plan.spreadIndex(restart)].indices.first())
        }
    }
}
