package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.domain.model.ComicDownloadTask
import com.rchdliang.copymanga_neo.domain.model.LocalReadingSession
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.NovelDownloadItem
import com.rchdliang.copymanga_neo.domain.model.NovelDownloadTask
import com.rchdliang.copymanga_neo.domain.model.NovelTag
import com.rchdliang.copymanga_neo.domain.model.NovelVolume
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.model.matches
import com.rchdliang.copymanga_neo.domain.model.mergeRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class DownloadQueueStoreTest {
    private val book = NovelBook(
        "shared",
        "作品",
        "https://example.test/cover",
        listOf(NovelTag("作者", "author")),
        themes = listOf(NovelTag("冒险", "adventure")),
        brief = "简介",
        uuid = "book-id",
        popular = 123
    )

    private fun novel(owner: String = "user:a", mode: RequestMode = RequestMode.WEB) =
        NovelDownloadTask(
            mode, owner, book, listOf(NovelDownloadItem(NovelVolume("1", "第一卷", 1, null, "2"))),
            taskId = "novel-task"
        )

    private fun comic() = ComicDownloadTask(
        "shared", "同名路径漫画", null, emptyList(), "", "", null, null,
        taskId = "comic-task"
    )

    @Test
    fun samePathAcrossContentAndAccountsUsesIndependentPersistentKeys() = runTest {
        val dao = MemoryQueue()
        val store = DownloadQueueStore(dao)
        listOf(
            comic(),
            novel(),
            novel("user:b"),
            novel(mode = RequestMode.OFFICIAL_APP)
        ).forEach { store.save(it) }
        assertEquals(4, store.load().size)
        assertEquals(4, store.load().map { it.taskKey }.toSet().size)
        store.remove(novel().taskKey)
        assertEquals(
            setOf(
                comic().taskKey,
                novel("user:b").taskKey,
                novel(mode = RequestMode.OFFICIAL_APP).taskKey
            ),
            store.load().map { it.taskKey }.toSet()
        )
    }

    @Test
    fun restoredNovelRetainsMetadataAndIntentButRequiresCacheVerification() {
        val task = novel().copy(
            volumes = listOf(
                NovelDownloadItem(
                    NovelVolume("1", "第一卷", 1, null, "2"),
                    ChapterDownloadStatus.DOWNLOADED,
                    1f
                ),
                NovelDownloadItem(
                    NovelVolume("2", "第二卷", 2, "1", null),
                    ChapterDownloadStatus.DOWNLOADING,
                    0.5f
                )
            )
        )
        val restored =
            DownloadQueueCodec.decode(DownloadQueueCodec.encode(task)) as NovelDownloadTask
        assertEquals(task.book, restored.book)
        assertEquals(task.mode, restored.mode)
        assertEquals(task.owner, restored.owner)
        assertEquals(task.taskId, restored.taskId)
        assertEquals(task.volumes.map { it.volume }, restored.volumes.map { it.volume })
        assertTrue(restored.volumes.all { it.status == ChapterDownloadStatus.WAITING && it.progress == 0f })
    }

    @Test
    fun appendKeepsCompletedVolumesAndRetriesOnlyRequestedFailures() {
        val v1 = NovelVolume("1", "第一卷", 1, null, "2")
        val v2 = NovelVolume("2", "第二卷", 2, "1", "3")
        val v3 = NovelVolume("3", "第三卷", 3, "2", null)
        val current = novel().copy(
            volumes = listOf(
                NovelDownloadItem(v1, ChapterDownloadStatus.DOWNLOADED, 1f),
                NovelDownloadItem(v2, ChapterDownloadStatus.ERROR, errorMessage = "网络断开")
            )
        )
        val merged = current.mergeRequest(
            novel().copy(
                volumes = listOf(
                    NovelDownloadItem(v1),
                    NovelDownloadItem(v2),
                    NovelDownloadItem(v3),
                    NovelDownloadItem(v3)
                )
            )
        ) as NovelDownloadTask
        assertEquals(listOf("1", "2", "3"), merged.volumes.map { it.id })
        assertEquals(ChapterDownloadStatus.DOWNLOADED, merged.volumes[0].status)
        assertEquals(ChapterDownloadStatus.WAITING, merged.volumes[1].status)
        assertNull(merged.volumes[1].errorMessage)
        assertEquals(current.taskId, merged.taskId)
    }

    @Test
    fun taskCannotResumeUnderAnotherAccountOrMode() {
        assertTrue(novel().matches(LocalReadingSession(RequestMode.WEB, "user:a", 9)))
        assertFalse(novel().matches(LocalReadingSession(RequestMode.WEB, "user:b", 9)))
        assertFalse(novel().matches(LocalReadingSession(RequestMode.OFFICIAL_APP, "user:a", 9)))
        assertFalse(novel().matches(null))
    }

    @Test
    fun mergingDifferentOwnersIsRejected() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { novel().mergeRequest(novel("user:b")) }
    }

    private class MemoryQueue : DocumentStorage {
        val rows = linkedMapOf<String, String>()
        override suspend fun read(key: String) = rows[key]
        override suspend fun write(key: String, value: String) { rows[key] = value }
        override suspend fun remove(key: String) { rows.remove(key) }
    }
}
