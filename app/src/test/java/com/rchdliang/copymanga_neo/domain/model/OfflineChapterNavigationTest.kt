package com.rchdliang.copymanga_neo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineChapterNavigationTest {
    private fun chapter(uuid: String, index: Int, prev: String? = null, next: String? = null) =
        DownloadedChapterInfo(
            uuid,
            "comic",
            uuid,
            index,
            1,
            listOf("local/$uuid.jpg"),
            "default",
            prev,
            next
        )

    @Test
    fun partialDownloadDoesNotSkipMissingMiddleChapter() {
        val one = chapter("one", 1, next = "two")
        val three = chapter("three", 3, prev = "two")
        assertNull(one.offlineContent(listOf(one, three)).nextUuid)
        assertNull(three.offlineContent(listOf(one, three)).prevUuid)
    }

    @Test
    fun knownLinksIgnoreDownloadOrderAndOtherGroups() {
        val one = chapter("one", 1, next = "two")
        val two = chapter("two", 2, prev = "one")
        assertEquals("two", one.offlineContent(listOf(two, one)).nextUuid)
        assertEquals("one", two.offlineContent(listOf(two, one)).prevUuid)
        assertNull(one.offlineContent(listOf(one, two.copy(groupPathWord = "extra"))).nextUuid)
        assertNull(one.offlineContent(listOf(one, two.copy(comicPathWord = "other"))).nextUuid)
    }

    @Test
    fun refreshedMetadataCanAddOrRemoveTheSuccessor() {
        val one = chapter("one", 1)
        val two = chapter("two", 2)
        assertNull(one.offlineContent(listOf(one, two)).nextUuid)
        assertEquals("two", one.copy(nextChapter = "two").offlineContent(listOf(one, two)).nextUuid)
        assertEquals(one.imagePaths, one.offlineContent(listOf(one)).imageUrls)
    }
}
