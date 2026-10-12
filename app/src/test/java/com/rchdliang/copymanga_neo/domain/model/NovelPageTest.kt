package com.rchdliang.copymanga_neo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelPageTest {
    private fun books(count: Int) = List(count) { NovelBook("book-$it", "标题", "", emptyList()) }

    @Test
    fun offsetsUseServerOffsetAndReturnedCountBeforeUiDeduplication() {
        val duplicates = books(2) + books(1)
        val page = NovelPage(duplicates, total = 10, offset = 3, limit = 3)
        assertEquals(6, page.nextOffset)
        assertTrue(page.hasMore)
        assertEquals(2, duplicates.distinctBy { it.path }.size)
    }

    @Test
    fun emptyPageStopsEvenIfServerTotalIsStale() {
        assertFalse(NovelPage(emptyList(), total = 100, offset = 20, limit = 20).hasMore)
    }

    @Test
    fun terminalShortPageStopsAtServerTotalForAllOfficialLimits() {
        for (limit in listOf(18, 20, 21)) {
            val first = NovelPage(books(limit), total = limit + 2, offset = 0, limit = limit)
            assertEquals(limit, first.nextOffset)
            assertTrue(first.hasMore)
            val last =
                NovelPage(books(2), total = limit + 2, offset = first.nextOffset, limit = limit)
            assertEquals(limit + 2, last.nextOffset)
            assertFalse(last.hasMore)
        }
    }
}
