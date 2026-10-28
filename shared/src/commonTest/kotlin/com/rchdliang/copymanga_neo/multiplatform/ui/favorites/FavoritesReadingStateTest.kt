package com.rchdliang.copymanga_neo.multiplatform.ui.favorites

import com.rchdliang.copymanga_neo.domain.model.*
import kotlin.test.*

class FavoritesReadingStateTest {
    @Test fun badgesCountOnlyUnstartedUpdatesOfShelfWorks() {
        val chapters = listOf(ShelfChapter("one", "第一章"), ShelfChapter("two", "第二章"))
        val comic = FavoriteComic("id", true, "Comic", "book", null, null, null, 0, null, null,
            "two", "第二章", BrowseInfo(null, "book", "one", "第一章"))
        val novel = NovelShelfEntry(1, NovelBook("book", "Novel", "", emptyList()), "two", "第二卷")
        val tracked = ShelfReadingRecord(ShelfReadingKey("owner", RequestMode.WEB, "comic", "book"))
            .read("one", "第一章", true, 1).observeCatalog(chapters.take(1)).observeCatalog(chapters)
        val state = FavoritesUiState(isLoggedIn = true, favorites = listOf(FavoriteItem(1, null, null, null, comic)),
            novels = listOf(novel), comicReadingStates = mapOf("book" to tracked),
            novelReadingStates = mapOf("book" to tracked.copy(key = tracked.key.copy(kind = "novel"))))
        assertEquals(2, state.updatedCount)
        val started = state.copy(comicReadingStates = mapOf("book" to tracked.read("two", "第二章", false, 2)))
        assertEquals(0, started.updatedComicCount)
        assertEquals(1, started.updatedCount)
        assertEquals(0, started.copy(novels = emptyList()).updatedCount)
        assertEquals(0, state.copy(isLoggedIn = false).updatedCount)
        assertEquals(0, state.copy(comicReadingStates = emptyMap(), novelReadingStates = emptyMap()).updatedCount)
    }
    @Test fun backlogAndAcknowledgedUpdatesUseTheSameEmphasisAsUnread() {
        val unread = favoritesReadingStatus(null, "", "", "300")
        val backlog = favoritesReadingStatus(null, "299", "第299话", "300")
        assertTrue(unread.emphasized)
        assertEquals(unread.emphasized, backlog.emphasized)
        assertEquals("继续阅读：第299话", backlog.text)
        val chapters = (298..300).map { ShelfChapter("$it", "第${it}话") }
        val caughtUp = ShelfReadingRecord(ShelfReadingKey("owner", RequestMode.WEB, "comic", "book"))
            .read("298", "第298话", true, 1).observeCatalog(chapters.take(1))
        val acknowledged = caughtUp.observeCatalog(chapters).read("299", "第299话", false, 2)
        val presentation = favoritesReadingStatus(acknowledged, "299", "第299话", "300")
        assertEquals("继续阅读：第299话", presentation.text)
        assertEquals(unread.emphasized, presentation.emphasized)
        assertEquals(ShelfReadingStatus.READING, acknowledged.status)
    }

    @Test fun completedLatestStaysMutedAndLatestWithoutCompletionIsNotInvented() {
        val completed = ShelfReadingRecord(ShelfReadingKey("owner", RequestMode.WEB, "comic", "book"))
            .read("300", "第300话", true, 1).observeCatalog(listOf(ShelfChapter("300", "第300话")))
        val presentation = favoritesReadingStatus(completed, "300", "第300话", "300")
        assertEquals("已读至最新", presentation.text)
        assertFalse(presentation.emphasized)
        val imported = favoritesReadingStatus(null, "300", "第300话", "300")
        assertEquals("继续阅读：第300话", imported.text)
        assertFalse(imported.emphasized)
        assertFalse(favoritesReadingStatus(null, "300", "第300话", "").emphasized)
    }
}
