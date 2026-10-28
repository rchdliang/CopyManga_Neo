package com.rchdliang.copymanga_neo.domain.model

import kotlin.test.*

class ShelfReadingTest {
    private val key = ShelfReadingKey("owner", RequestMode.WEB, "comic", "book", "default")
    private fun chapters(count: Int) = (1..count).map { ShelfChapter("c$it", "第 $it 章") }
    private fun caughtUp(count: Int) = ShelfReadingRecord(key)
        .read("c$count", "第 $count 章", true, 1).observeCatalog(chapters(count))

    @Test fun backlogNeverBecomesAnUpdateUntilCaughtUp() {
        var state = ShelfReadingRecord(key).read("c10", "第 10 章", true, 1).observeCatalog(chapters(100))
        assertEquals(ShelfReadingStatus.READING, state.status)
        state = state.observeCatalog(chapters(101))
        assertEquals(ShelfReadingStatus.READING, state.status)
        state = state.read("c101", "第 101 章", false, 2).observeCatalog(chapters(101))
        assertEquals(ShelfReadingStatus.READING, state.status)
        state = state.read("c101", "第 101 章", true, 3).observeCatalog(chapters(101))
        assertEquals(ShelfReadingStatus.CAUGHT_UP, state.status)
        assertEquals(ShelfReadingStatus.UPDATED, state.observeCatalog(chapters(102)).status)
    }

    @Test fun beginningAnyNewChapterDisarmsUntilNextCatchUp() {
        var state = caughtUp(100).observeCatalog(chapters(102))
        assertEquals(ShelfReadingStatus.UPDATED, state.status)
        state = state.read("c101", "第 101 章", false, 2)
        assertEquals(ShelfReadingStatus.READING, state.status)
        assertNull(state.caughtUpChapter)
        state = state.observeCatalog(chapters(103))
        assertEquals(ShelfReadingStatus.READING, state.status)
        state = state.read("c103", "第 103 章", true, 3).observeCatalog(chapters(103))
        assertEquals(ShelfReadingStatus.CAUGHT_UP, state.status)
        assertEquals(ShelfReadingStatus.UPDATED, state.observeCatalog(chapters(104)).status)
    }

    @Test fun rereadingOldChaptersDoesNotAcknowledgeNewContent() {
        val state = caughtUp(100).read("c10", "第 10 章", false, 2).observeCatalog(chapters(101))
        assertEquals(ShelfReadingStatus.UPDATED, state.status)
        assertEquals("c100", state.caughtUpChapter)
    }

    @Test fun offlineReadingIsResolvedBeforePublishingUpdates() {
        val state = caughtUp(100).read("c101", "第 101 章", false, 2)
            .read("c10", "第 10 章", false, 3).observeCatalog(chapters(102))
        assertEquals(ShelfReadingStatus.READING, state.status)
        assertNull(state.caughtUpChapter)
        // An offline completion alone cannot create an assumed catch-up baseline.
        val offline = ShelfReadingRecord(key).read("c100", "末章", true, 1)
        assertEquals(ShelfReadingStatus.READING, offline.status)
        assertEquals(ShelfReadingStatus.READING, offline.observeCatalog(chapters(101)).status)
    }

    @Test fun reorderedDeletedOrPartialCatalogDoesNotInventUpdates() {
        assertEquals(ShelfReadingStatus.READING, caughtUp(100).observeCatalog(chapters(99)).status)
        assertEquals(ShelfReadingStatus.READING, caughtUp(100).observeCatalog(chapters(100).reversed()).status)
        assertEquals(ShelfReadingStatus.CAUGHT_UP, caughtUp(100).observeCatalog(emptyList()).status)
        assertEquals(ShelfReadingStatus.CAUGHT_UP, caughtUp(100).observeCatalog(chapters(100) + chapters(1)).status)
    }

    @Test fun modesShareReadingButAccountsKindsAndSelectedGroupsAreIndependent() {
        val a = caughtUp(100).observeCatalog(chapters(101))
        val b = ShelfReadingRecord(key.copy(group = "other")).read("other1", "其他汉化组", false, 2)
        val novel = a.copy(key = key.copy(kind = "novel", group = ""))
        val snapshot = ShelfReadingSnapshot(listOf(a, b, novel).associateBy { it.key.id })
        val session = LocalReadingSession(RequestMode.WEB, "owner", 1)
        assertEquals(ShelfReadingStatus.READING, snapshot.books(session, "comic").getValue("book").status)
        assertEquals(ShelfReadingStatus.UPDATED, snapshot.books(session, "novel").getValue("book").status)
        assertTrue(snapshot.books(session.copy(owner = "another"), "comic").isEmpty())
        assertEquals(ShelfReadingStatus.UPDATED, snapshot.books(session.copy(mode = RequestMode.OFFICIAL_APP), "novel").getValue("book").status)
        assertEquals(key.id, key.copy(mode = RequestMode.OFFICIAL_APP).id)
    }
}
