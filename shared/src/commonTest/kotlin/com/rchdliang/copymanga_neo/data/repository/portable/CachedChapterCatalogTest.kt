package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.domain.model.ChapterCatalog
import com.rchdliang.copymanga_neo.domain.model.ChapterGroup
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import kotlin.test.*

class CachedChapterCatalogTest {
    private fun chapter(id: String, index: Int = 0, rich: Boolean = false) = ChapterInfo(
        index, id, 2, if (rich) (index + 1) * 10 else index,
        if (rich) 39 else null, id, "comic-id", "book", "group-id", "default", 1,
        if (rich) "success" else null, if (rich) "2026-10-28" else null,
        if (index == 0) null else "a", if (index == 0) "b" else null,
    )

    private fun catalog(vararg chapters: ChapterInfo) = ChapterCatalog(
        listOf(ChapterGroup("default", chapters.size, "默认")), mapOf("default" to chapters.toList()),
    )

    private fun rows(entry: CachedChapterCatalog) = assertNotNull(entry.result.getOrNull()).chaptersByGroup.getValue("default")

    @Test fun webOnlyCatalogIsSharedButMustBeEnrichedForApp() {
        val web = CachedChapterCatalog.success(catalog(chapter("a")), false, 300, null)
        assertTrue(web.canReuse(10, false))
        assertFalse(web.canReuse(10, true))
        val api = CachedChapterCatalog.success(catalog(chapter("a", rich = true)), true, 400, web)
        assertTrue(api.canReuse(10, true))
        assertTrue(api.canReuse(10, false))
    }

    @Test fun repeatedWebRefreshesKeepApiFieldsButAcceptFreshValues() {
        val full = catalog(chapter("a", rich = true), chapter("b", 1, true))
        val api = CachedChapterCatalog.success(full, true, 300, null)
        val fresh = catalog(chapter("a").copy(name = "新名称", type = 2),
            chapter("b", 1).copy(size = 42, news = "", datetimeCreated = "2026-10-29"))
        val web = CachedChapterCatalog.success(fresh, false, 400, api)
        val again = CachedChapterCatalog.success(fresh, false, 500, web)
        assertEquals(listOf(39, 42), rows(again).map { it.size })
        assertEquals(listOf(10, 20), rows(again).map { it.ordered })
        assertEquals("success", rows(again)[0].news)
        assertEquals("2026-10-28", rows(again)[0].datetimeCreated)
        assertEquals("新名称", rows(again)[0].name)
        assertEquals(2, rows(again)[0].type)
        assertEquals("", rows(again)[1].news)
        assertEquals("2026-10-29", rows(again)[1].datetimeCreated)
        assertTrue(again.canReuse(250, true))
    }

    @Test fun sparseRefreshDoesNotExtendApiFreshnessOrResurrectClearedFields() {
        val api = CachedChapterCatalog.success(catalog(chapter("a", rich = true)), true, 300, null)
        val web = CachedChapterCatalog.success(catalog(chapter("a")), false, 500, api)
        assertTrue(web.canReuse(350, false))
        assertFalse(web.canReuse(350, true))
        assertFalse(web.canReuse(500, false))
        val cleared = CachedChapterCatalog.success(catalog(chapter("a").copy(ordered = 10)), true, 600, web)
        assertNull(rows(cleared).single().size)
        assertNull(rows(cleared).single().news)
        assertTrue(cleared.canReuse(550, true)) // API 合法的 null 不应导致无限补全。
        assertNull(rows(CachedChapterCatalog.success(catalog(chapter("a")), false, 700, cleared)).single().size)
    }

    @Test fun changedMembershipKeepsNewNavigationAndDoesNotRestoreDeletedChapter() {
        val api = CachedChapterCatalog.success(catalog(chapter("a", rich = true), chapter("b", 1, true)), true, 300, null)
        val fresh = catalog(chapter("b").copy(prevChapter = null, nextChapter = "c"),
            chapter("c", 1).copy(prevChapter = "b", nextChapter = null))
        val web = CachedChapterCatalog.success(fresh, false, 400, api)
        assertEquals(listOf("b", "c"), rows(web).map { it.uuid })
        assertEquals(listOf(0, 1), rows(web).map { it.ordered })
        assertEquals(listOf(39, null), rows(web).map { it.size })
        assertNull(rows(web)[0].prevChapter)
        assertEquals("c", rows(web)[0].nextChapter)
        assertEquals("b", rows(web)[1].prevChapter)
        assertFalse(web.canReuse(10, true))
    }

    @Test fun reorderedCatalogDoesNotReuseOldServerSortWeights() {
        val api = CachedChapterCatalog.success(catalog(chapter("a", rich = true), chapter("b", 1, true)), true, 300, null)
        val fresh = catalog(chapter("b").copy(nextChapter = "a"), chapter("a", 1).copy(prevChapter = "b"))
        val web = CachedChapterCatalog.success(fresh, false, 400, api)
        assertEquals(listOf("b", "a"), rows(web).map { it.uuid })
        assertEquals(listOf(0, 1), rows(web).map { it.ordered })
        assertEquals(listOf(39, 39), rows(web).map { it.size })
        assertFalse(web.canReuse(10, true))
    }

    @Test fun differentComicOrGroupCannotBorrowMetadataForTheSameUuid() {
        val api = CachedChapterCatalog.success(catalog(chapter("a", rich = true)), true, 300, null)
        for (fresh in listOf(chapter("a").copy(comicPathWord = "another"),
            chapter("a").copy(comicId = "another-id"), chapter("a").copy(groupId = "another-group"))) {
            val web = CachedChapterCatalog.success(catalog(fresh), false, 400, api)
            assertNull(rows(web).single().size)
            assertFalse(web.canReuse(10, true))
        }
        val moved = ChapterCatalog(listOf(ChapterGroup("other", 1, "其他")),
            mapOf("other" to listOf(chapter("a").copy(groupPathWord = "other"))))
        val web = CachedChapterCatalog.success(moved, false, 400, api)
        assertNull(assertNotNull(web.result.getOrNull()).chaptersByGroup.getValue("other").single().size)
        assertFalse(web.canReuse(10, true))
    }
}
