package com.rchdliang.copymanga_neo.multiplatform.ui.detail

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.reading.ReadingPositionPolicy
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.*

class StartReadingPolicyTest {
    private fun chapter(id: String, index: Int, next: String? = null, group: String = "default") =
        ChapterInfo(index, id, 0, index, 10, id, "comic", "book", null, group, 0, null, null, null, next)
    private val a = chapter("a", 0, "b")
    private val b = chapter("b", 1, "c")
    private val c = chapter("c", 2)
    private val position = LocalReadingRecord("owner", "comic", "book", "a", "a",
        page = 9, completed = true, atChapterEnd = true)
    private fun state(chapters: List<ChapterInfo> = listOf(a, b, c)) = DetailUiState(
        chapters = chapters.toImmutableList(), browse = BrowseInfo(null, "book", "a", "a"),
        browseGroup = "default", resumePosition = position, isChapterCatalogReady = true,
        isProgressLoading = false)

    @Test fun stoppedAtEndStartsExactlyNextChapterOnFirstPage() {
        val target = assertNotNull(resolveStartChapter(state()))
        assertEquals("b", target.uuid)
        assertEquals(0, target.initialPage)
    }
    @Test fun historicalCompletionDoesNotSkipCurrentRereadPosition() {
        val target = assertNotNull(resolveStartChapter(state().copy(resumePosition = position.copy(page = 3, atChapterEnd = false))))
        assertEquals("a", target.uuid)
        assertEquals(-1, target.initialPage)
    }
    @Test fun oldOrCloudOnlyProgressDoesNotInferChapterEnd() {
        assertEquals("a", resolveStartChapter(state().copy(resumePosition = null))?.uuid)
        assertEquals("a", resolveStartChapter(state().copy(resumePosition = position.copy(atChapterEnd = false)))?.uuid)
    }
    @Test fun noNextChapterPreservesCurrentPosition() {
        assertEquals("a", resolveStartChapter(state(listOf(a.copy(nextChapter = null))))?.uuid)
    }
    @Test fun offlineMissingChapterNeverSkipsForwardToLaterDownload() {
        assertEquals("a", resolveStartChapter(state(listOf(a, c)).copy(hasLocalCache = true))?.uuid)
        assertEquals("a", resolveStartChapter(state(listOf(a.copy(nextChapter = null), c)).copy(hasLocalCache = true))?.uuid)
        assertEquals("b", resolveStartChapter(state(listOf(a, b)).copy(hasLocalCache = true))?.uuid)
    }
    @Test fun sortingAndSelectedGroupCannotChangeContinuationOrder() {
        assertEquals("b", resolveStartChapter(state(listOf(c, b, a)).copy(sortAscending = false))?.uuid)
        val other = chapter("other", 0, group = "other")
        val state = state(listOf(other)).copy(selectedGroup = "other",
            chapterCatalog = ChapterCatalog(emptyList(), mapOf("default" to listOf(a, b, c), "other" to listOf(other))))
        assertEquals("b", resolveStartChapter(state)?.uuid)
        assertEquals("default", resolveStartChapter(state)?.groupPathWord)
    }
    @Test fun mismatchedPrecisePositionCannotAdvanceRemoteChapter() {
        assertEquals("a", resolveStartChapter(state().copy(resumePosition = position.copy(chapter = "other")))?.uuid)
    }
    @Test fun completedRemoteCatalogCanResolveNewlyPublishedNextChapter() {
        assertEquals("b", resolveStartChapter(state(listOf(a.copy(nextChapter = null), b)))?.uuid)
        assertEquals("a", resolveStartChapter(state(listOf(a.copy(nextChapter = null), b)).copy(isChapterCatalogReady = false))?.uuid)
    }
    @Test fun longImageMustReachBottomAndSettleButMayShareViewportWithPriorPage() {
        assertFalse(ReadingPositionPolicy.completed(9, 10, true, false, continuous = true))
        assertFalse(ReadingPositionPolicy.completed(9, 10, false, true, continuous = true))
        assertTrue(ReadingPositionPolicy.completed(8, 10, true, true, continuous = true))
        assertFalse(ReadingPositionPolicy.completed(8, 10, true, true))
        assertTrue(ReadingPositionPolicy.completed(9, 10, true, true))
    }
}
