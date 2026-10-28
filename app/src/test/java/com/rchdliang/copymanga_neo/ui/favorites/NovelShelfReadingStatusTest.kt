package com.rchdliang.copymanga_neo.ui.favorites

import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.NovelShelfEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelShelfReadingStatusTest {
    private val entry = NovelShelfEntry(
        1, NovelBook("book", "小说", "", emptyList()), "two", "第二卷"
    )
    private val record = LocalReadingRecord(
        "user:reader", "novel", "book", "one", "第一卷", updatedAt = 1
    )

    private fun status(
        records: List<LocalReadingRecord> = emptyList(),
        shelfEntry: NovelShelfEntry = entry,
        owner: String? = record.owner
    ) = novelShelfReadingStatuses(listOf(shelfEntry), records, owner).getValue("book")

    @Test fun unreadTakesPriorityEvenWhenLatestVolumeIsKnown() {
        assertEquals(NovelShelfReadingStatus("还未读过", true), status())
    }

    @Test fun olderVolumeShowsNewContent() {
        assertEquals(NovelShelfReadingStatus("有新内容：第二卷", true), status(listOf(record)))
    }

    @Test fun startingLatestVolumeShowsLastReadWithoutRequiringCompletion() {
        val latest = record.copy(chapter = "two", name = "第二卷", updatedAt = 2)
        assertEquals(
            NovelShelfReadingStatus("上次读到：第二卷", false),
            status(listOf(latest, record))
        )
    }

    @Test fun rereadingOlderVolumeUsesMostRecentPositionLikeComics() {
        val latest = record.copy(chapter = "two", name = "第二卷", updatedAt = 2)
        assertEquals(
            NovelShelfReadingStatus("有新内容：第二卷", true),
            status(listOf(latest, record.copy(updatedAt = 3)))
        )
    }

    @Test fun missingLatestIdDoesNotInventAnUpdate() {
        assertEquals(
            NovelShelfReadingStatus("上次读到：第一卷", false),
            status(listOf(record), entry.copy(lastChapterId = ""))
        )
    }

    @Test fun otherAccountsKindsBooksAndBlankPositionsDoNotCountAsRead() {
        val unrelated = listOf(
            record.copy(owner = "user:other"), record.copy(kind = "comic"),
            record.copy(path = "other"), record.copy(chapter = " ")
        )
        assertEquals(NovelShelfReadingStatus(), status(unrelated))
        assertEquals(NovelShelfReadingStatus(), status(listOf(record), owner = null))
    }

    @Test fun deletedHistoryReturnsToUnread() {
        assertEquals(NovelShelfReadingStatus("有新内容：第二卷", true), status(listOf(record)))
        assertEquals(NovelShelfReadingStatus(), status(emptyList()))
    }

    @Test fun missingNamesHaveReadableFallbacks() {
        assertEquals("有新内容：新卷", status(listOf(record), entry.copy(lastChapterName = " ")).text)
        assertEquals(
            "上次读到：未知卷",
            status(listOf(record.copy(chapter = "two", name = " "))).text
        )
    }
}
