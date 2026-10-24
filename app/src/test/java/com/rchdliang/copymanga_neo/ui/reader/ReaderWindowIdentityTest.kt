package com.rchdliang.copymanga_neo.ui.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderWindowIdentityTest {
    private fun pages(chapter: String) = (0..4).map { i ->
        ReaderPageEntry("$chapter:image:$i", chapter, "Chapter $chapter", "fixture-$i", i, 5)
    }

    @Test fun stableSpreadKeySurvivesPrependAndKeepsActualRawAnchor() {
        val chapter = pages("2")
        val key = readerSpreads(chapter, true)[1].key(chapter)
        val withPrevious = pages("1") + chapter
        val groups = readerSpreads(withPrevious, true)
        assertEquals(7, groups.rawIndexForKey(withPrevious, key, 7))
        assertEquals(6, groups.rawIndexForKey(withPrevious, key, 0))
        val trimmed = withPrevious.drop(5)
        assertEquals(2, readerSpreads(trimmed, true).rawIndexForKey(trimmed, key, 2))
    }

    @Test fun changedLayoutDoesNotTreatAnOldGroupIndexAsTheSamePage() {
        val pages = pages("2")
        val key = readerSpreads(pages, true)[1].key(pages)
        assertNull(readerSpreads(pages, false).rawIndexForKey(pages, key, 2))
    }
}
