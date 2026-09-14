package com.rchdliang.copymanga_neo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeBlacklistTest {
    @Test
    fun matchesStablePathEvenWhenDisplayNameChanges() {
        assertTrue(Theme("校园", "xiaoyuan").matches(Theme("校園", "xiaoyuan")))
    }

    @Test
    fun distinctPathsDoNotMatchDespiteSameName() {
        assertFalse(Theme("校园", "a").matches(Theme("校园", "b")))
    }

    @Test
    fun missingOrBlankPathFallsBackToNonBlankName() {
        assertTrue(Theme(" 校园 ", "xiaoyuan").matches(Theme("校园", null)))
        assertTrue(Theme("校园", " ").matches(Theme("校园", "xiaoyuan")))
        assertFalse(Theme("", null).matches(Theme("", null)))
    }

    @Test
    fun hidesAnyMatchingTagAndPreservesOrderAndUnknownTags() {
        val original = listOf(
            comic("keep", listOf(Theme("冒险", "maoxian"))),
            comic("blocked", listOf(Theme("冒险", "maoxian"), Theme("校园", "xiaoyuan"))),
            comic("unknown", null),
            comic("empty", emptyList())
        )
        val filtered = original.withoutBlockedThemes(listOf(Theme("校園", "xiaoyuan")))
        assertEquals(listOf("keep", "unknown", "empty"), filtered.map { it.pathWord })
        assertEquals(4, original.size)
        assertEquals(original, original.withoutBlockedThemes(emptyList()))
    }

    private fun comic(path: String, themes: List<Theme>?) = ComicSummary(
        path, path, null, null, null, null, null, themes, null
    )
}
