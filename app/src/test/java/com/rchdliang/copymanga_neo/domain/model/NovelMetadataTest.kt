package com.rchdliang.copymanga_neo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NovelMetadataTest {
    private val detail = NovelBook(
        "spice", "狼与香辛料", "https://example.com/cover.jpg",
        listOf(NovelTag("支仓冻砂", "author")), brief = "简介"
    )

    @Test
    fun sparseVolumeKeepsKnownMetadata() {
        val volume = NovelBook("spice", "", "", emptyList())
        assertEquals(detail, volume.withFallback(detail))
        assertEquals(detail, volume.copy(name = "  ").withFallback(detail))
    }

    @Test
    fun differentRoutesNeverShareMetadata() {
        val other = NovelBook("other", "", "", emptyList())
        assertEquals(other, other.withFallback(detail))
    }

    @Test
    fun freshValuesTakePrecedenceWithoutLosingAuthors() {
        val fresh = NovelBook("spice", "新标题", "https://example.com/new.jpg", emptyList())
        val merged = fresh.withFallback(detail)
        assertEquals("新标题", merged.name)
        assertEquals(fresh.cover, merged.cover)
        assertEquals(detail.authors, merged.authors)
    }

    @Test
    fun missingTitlesStayBlankAfterFailedDetailLookup() {
        val volume = NovelBook("spice", "", "", emptyList())
        val saved = NovelBook("spice", "  ", "", emptyList())
        val merged = volume.withFallback(null).withFallback(saved)
        assertEquals("", merged.name)
        assertEquals("", safeBookTitle(merged.path, merged.name, saved.name))
        assertEquals("作品信息暂缺", displayBookTitle(merged.path, merged.name, saved.name))
    }

    @Test
    fun realSavedTitleSurvivesSparseVolumeAndFailedDetailLookup() {
        val volume = NovelBook("spice", "", "", emptyList())
        val merged = volume.withFallback(detail)
        assertEquals(detail.name, safeBookTitle(merged.path, merged.name, "spice"))
        assertEquals(detail.name, displayBookTitle(merged.path, merged.name))
    }

    @Test
    fun blankTitleShowsMissingStateWithoutPersistingPlaceholder() {
        assertEquals("", safeBookTitle("spice", null, "  "))
        assertEquals("作品信息暂缺", displayBookTitle("spice", null, " "))
        assertEquals("狼与香辛料", safeBookTitle("spice", "", " 狼与香辛料 "))
    }
}
