package com.rchdliang.copymanga_neo.domain.model

import org.junit.Assert.*
import org.junit.Test

class ComicCardMetadataTest {
    private val blocked = listOf(Theme("恐怖", "horror"))
    private fun metadata(themes: List<Theme>?) =
        ComicCardMetadata("sample", themes, "简介", 10, "2026-10-19", 1000)

    @Test
    fun cacheExpiresAtTwentyFiveHours() {
        val value = metadata(emptyList())
        assertTrue(value.isFresh(1000 + ComicCardMetadata.CACHE_MILLIS - 1))
        assertFalse(value.isFresh(1000 + ComicCardMetadata.CACHE_MILLIS))
        assertFalse(value.isFresh(999))
    }

    @Test
    fun unknownDetailsNeverRevealBlacklistedCover() {
        assertEquals(ComicCoverVisibility.PENDING, comicCoverVisibility(false, emptyList(), null))
        assertEquals(ComicCoverVisibility.PENDING, comicCoverVisibility(true, blocked, null))
        assertEquals(
            ComicCoverVisibility.PENDING,
            comicCoverVisibility(true, blocked, metadata(null))
        )
    }

    @Test
    fun confirmedEmptyThemesAreUsableWithoutAnotherRequest() {
        assertEquals(
            ComicCoverVisibility.VISIBLE,
            comicCoverVisibility(true, blocked, metadata(emptyList()))
        )
        assertEquals(ComicCoverVisibility.VISIBLE, comicCoverVisibility(true, emptyList(), null))
    }

    @Test
    fun expiredMetadataStillProtectsCoverAndKeepsStaticFields() {
        val value = metadata(listOf(Theme("不同译名", "horror")))
        assertFalse(value.isFresh(1000 + ComicCardMetadata.CACHE_MILLIS))
        assertEquals(ComicCoverVisibility.BLURRED, comicCoverVisibility(true, blocked, value))
        assertEquals("简介", value.brief)
    }
}
