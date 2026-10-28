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
}
