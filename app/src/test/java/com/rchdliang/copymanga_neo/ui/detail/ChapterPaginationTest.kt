package com.rchdliang.copymanga_neo.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterPaginationTest {

    @Test
    fun emptyPageAdvancesOffsetToTotal() {
        assertEquals(250, advanceChapterOffset(currentOffset = 200, total = 250, receivedCount = 0))
    }

    @Test
    fun nonEmptyPageAdvancesOffsetByReceivedCount() {
        assertEquals(
            220,
            advanceChapterOffset(currentOffset = 200, total = 250, receivedCount = 20)
        )
    }
}
