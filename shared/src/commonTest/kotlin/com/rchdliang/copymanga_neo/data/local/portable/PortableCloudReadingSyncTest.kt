package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PortableCloudReadingSyncTest {
    private class Documents : DocumentStorage {
        val values = mutableMapOf<String, String>()
        override suspend fun read(key: String) = values[key]
        override suspend fun write(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
    private class Fixture(val scope: CoroutineScope, val documents: Documents = Documents()) {
        val state = PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB)))
        val manga = PortableReadingLifecycleTest.Manga()
        val local = PortableLocalReading(documents, scope)
        val progress = PortableReadingProgress(documents, state, manga, scope)
        val sessions = PortableReadingSession(state)
        var catalog: List<ShelfChapter> = emptyList()
        val shelf = PortableShelfReading(documents, sessions, local, scope, progress) { Result.success(catalog) }
        val sync = PortableCloudReadingSync(state, manga, progress, local, shelf, scope)
        fun login(owner: String = "user:a") = state.update { it.copy(webSession = PortableSession("token", owner)) }
        fun logout() = state.update { it.copy(webSession = PortableSession()) }
    }
    private fun entry(path: String = "book", read: String = "24", latest: String = "25", id: Long = 1) =
        BrowseHistoryItem(id, "漫画", path, "cover", listOf(Author("作者", "author")), read, "$read 话", latest, "第 $latest 话")

    @Test fun firstLoginOnlyDefaultsToCaughtUpWhenCloudChapterIsLatest() = runTest {
        val f = Fixture(backgroundScope)
        f.manga.history = { offset -> Result.success(if (offset == 0) BrowseHistoryPage(listOf(entry()), 20)
            else BrowseHistoryPage(listOf(entry("second", "5", "5", 2)), null)) }
        f.sync.start(); runCurrent()
        assertTrue(f.manga.historyOffsets.isEmpty())
        f.login(); runCurrent()
        assertEquals(listOf(0, 20), f.manga.historyOffsets)
        assertEquals(2, f.local.snapshot.value.records.size)
        val record = f.local.latest("user:a", "comic", "book")!!
        assertEquals("24", record.chapter)
        assertEquals("", record.group) // Resolve the real group on opening, never invent default.
        assertTrue(record.cloudImported)
        assertFalse(record.completed)
        assertFalse(record.atChapterEnd)
        assertEquals("24", f.progress.progress.value.records.getValue("book").chapterUuid)
        assertFalse(f.progress.progress.value.records.getValue("book").pendingSync)
        val tracked = f.shelf.snapshot.value.books(f.sessions.getReadingSessionSync(), "comic")
        assertEquals(ShelfReadingStatus.READING, tracked.getValue("book").status)
        assertEquals(ShelfReadingStatus.CAUGHT_UP, tracked.getValue("second").status)
        assertFalse(tracked.getValue("book").assumedFromCloud)
        assertNull(tracked.getValue("book").caughtUpChapter)
        assertEquals(0, f.manga.calls) // Importing must not report a fake read to the server.
        assertEquals(0, f.manga.clears)
        val backedUp = PortableBackupCodec.reading(PortableBackupCodec.reading(f.local.snapshot.value))
        assertTrue(backedUp.records.all { it.cloudImported })
    }

    @Test fun expiryEmptyResponsesAndReloginPreserveHistoryBookmarksAndLocalCatchUpState() = runTest {
        val f = Fixture(backgroundScope)
        f.login(); runCurrent()
        val reading = LocalReadingRecord("user:a", "comic", "book", "10", "第 10 话", "other", page = 7)
        f.local.record(reading); f.local.flush().getOrThrow()
        f.progress.recordRead("user:a", "book", "10", "第 10 话", "other"); runCurrent()
        f.local.toggleBookmark(reading).getOrThrow()
        f.shelf.record(f.sessions.getReadingSessionSync(), reading); runCurrent()
        val before = f.local.snapshot.value
        f.manga.history = { Result.success(BrowseHistoryPage(listOf(entry(read = "9", latest = "100")), null)) }
        f.sync.start(); runCurrent()
        assertEquals(before, f.local.snapshot.value)
        assertEquals("10", f.progress.progress.value.records.getValue("book").chapterUuid)
        assertEquals(ShelfReadingStatus.READING, f.shelf.snapshot.value.books(f.sessions.getReadingSessionSync(), "comic").getValue("book").status)
        f.logout(); runCurrent()
        assertEquals(before, f.local.snapshot.value)
        f.manga.history = { Result.success(BrowseHistoryPage(emptyList(), null)) }
        f.login(); runCurrent()
        assertEquals(before, f.local.snapshot.value)
        f.progress.acceptRemote("user:a", mapOf("book" to null), f.progress.requestVersion()).getOrThrow()
        runCurrent()
        assertEquals("10", f.progress.progress.value.records.getValue("book").chapterUuid)
        assertEquals(before.bookmarks, f.local.snapshot.value.bookmarks)
        f.manga.history = { Result.failure(Exception("expired session")) }
        f.sync.retry(); runCurrent()
        assertNotNull(f.sync.error.value)
        assertEquals(before, f.local.snapshot.value)
        val restored = PortableLocalReading(f.documents, backgroundScope)
        restored.awaitReady().getOrThrow()
        assertEquals(before, restored.snapshot.value)
    }

    @Test fun lateResponseCannotWriteIntoNewAccountAndPendingLocalPositionWins() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        f.manga.history = {
            val owner = f.state.snapshot.session().owner
            if (owner == "user:a") withContext(NonCancellable) { gate.await() }
            Result.success(BrowseHistoryPage(listOf(entry(owner)), null))
        }
        f.sync.start(); f.login(); runCurrent()
        f.login("user:b"); runCurrent()
        gate.complete(Unit); runCurrent()
        assertTrue(f.local.snapshot.value.records.all { it.owner == "user:b" })
        assertNull(f.local.latest("user:a", "comic", "user:a"))
        assertEquals(1, f.local.snapshot.value.records.size)

        val pending = LocalReadingRecord("user:b", "comic", "pending", "local", "本机", page = 8)
        f.manga.history = { f.local.record(pending); Result.success(BrowseHistoryPage(listOf(entry("pending")), null)) }
        f.sync.retry(); runCurrent()
        assertNull(f.local.latest("user:b", "comic", "pending")) // Still in the debounced write queue.
        f.local.flush().getOrThrow(); runCurrent()
        assertEquals("local", f.local.latest("user:b", "comic", "pending")!!.chapter)
        assertEquals(8, f.local.latest("user:b", "comic", "pending")!!.page)
        assertNull(f.shelf.snapshot.value.books(f.sessions.getReadingSessionSync(), "comic")["pending"])
    }

    @Test fun explicitDeletionIsNotUndoneAndFailedPaginationCanResumeSafely() = runTest {
        val f = Fixture(backgroundScope)
        f.login(); runCurrent()
        val deleted = LocalReadingRecord("user:a", "comic", "removed", "24", "24")
        f.local.record(deleted); f.local.flush().getOrThrow()
        f.local.removeHistory(deleted).getOrThrow()
        var fail = true
        f.manga.history = { offset ->
            if (offset == 0) Result.success(BrowseHistoryPage(listOf(entry("removed"), entry()), 20))
            else if (fail) Result.failure(Exception("offline"))
            else Result.success(BrowseHistoryPage(listOf(entry("second", id = 2)), null))
        }
        f.sync.start(); runCurrent()
        assertNotNull(f.sync.error.value)
        assertNull(f.local.latest("user:a", "comic", "removed"))
        assertNotNull(f.local.latest("user:a", "comic", "book"))
        fail = false; f.sync.retry(); runCurrent()
        assertNull(f.sync.error.value)
        assertEquals(setOf("book", "second"), f.local.snapshot.value.records.map { it.path }.toSet())
        f.local.clearHistory("user:a").getOrThrow(); runCurrent()
        f.sync.retry(); runCurrent()
        assertTrue(f.local.snapshot.value.records.isEmpty())
        assertTrue(f.shelf.snapshot.value.records.isEmpty())
    }

    @Test fun repeatedCloudPullsAdvanceExistingHistoryAndSurviveRestart() = runTest {
        val f = Fixture(backgroundScope)
        var remote = entry(read = "10", latest = "100")
        f.manga.history = { Result.success(BrowseHistoryPage(listOf(remote), null)) }
        f.login(); f.sync.start(); runCurrent()
        assertEquals(ShelfReadingStatus.READING,
            f.shelf.snapshot.value.books(f.sessions.getReadingSessionSync(), "comic").getValue("book").status)
        remote = entry(read = "20", latest = "101")
        f.sync.retry(); runCurrent()
        assertEquals("20", f.local.latest("user:a", "comic", "book")!!.chapter)
        assertEquals("20", f.progress.progress.value.records.getValue("book").chapterUuid)
        val tracked = f.shelf.snapshot.value.books(f.sessions.getReadingSessionSync(), "comic").getValue("book")
        assertEquals("20", tracked.chapter)
        assertNull(tracked.caughtUpChapter)
        assertEquals(ShelfReadingStatus.READING, tracked.status)
        val before = f.local.snapshot.value
        f.sync.retry(); runCurrent()
        assertEquals(before, f.local.snapshot.value)
        val restored = Fixture(backgroundScope, Documents().also { it.values.putAll(f.documents.values) })
        restored.manga.history = { Result.success(BrowseHistoryPage(listOf(entry(read = "21", latest = "101")), null)) }
        restored.login(); restored.sync.start(); runCurrent()
        assertEquals("21", restored.local.latest("user:a", "comic", "book")!!.chapter)
        assertEquals("21", restored.shelf.snapshot.value.books(restored.sessions.getReadingSessionSync(), "comic").getValue("book").chapter)
        assertEquals(0, restored.manga.calls)
    }

    @Test fun remoteAdvanceAndRereadPreserveExactPagesCompletionAndBookmarks() = runTest {
        val f = Fixture(backgroundScope)
        f.login(); runCurrent()
        val precise = LocalReadingRecord("user:a", "comic", "book", "10", "10 话",
            group = "other", title = "漫画", page = 7, completed = true, atChapterEnd = true)
        f.local.record(precise); f.local.flush().getOrThrow()
        f.local.toggleBookmark(precise).getOrThrow()
        val saved = f.local.position("user:a", "comic", "book", "10")!!
        var remote = entry(read = "20", latest = "100")
        f.manga.history = { Result.success(BrowseHistoryPage(listOf(remote), null)) }
        f.sync.start(); runCurrent()
        assertEquals("20", f.local.latest("user:a", "comic", "book")!!.chapter)
        assertEquals(saved, f.local.position("user:a", "comic", "book", "10"))
        remote = entry(read = "10", latest = "100") // 云端回读旧章也属于新的续读位置。
        f.sync.retry(); runCurrent()
        val resumed = f.local.latest("user:a", "comic", "book")!!
        assertEquals("10", resumed.chapter)
        assertEquals(7, resumed.page)
        assertEquals("other", resumed.group)
        assertTrue(resumed.completed)
        assertTrue(resumed.atChapterEnd)
        assertFalse(resumed.cloudImported)
        assertEquals(listOf(precise), f.local.snapshot.value.bookmarks)
        assertEquals(0, f.manga.calls)
    }

    @Test fun flushedReadingDuringCloudRequestCannotBeOverwritten() = runTest {
        val f = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        f.manga.history = { gate.await(); Result.success(BrowseHistoryPage(listOf(entry(read = "10")), null)) }
        f.login(); f.sync.start(); runCurrent()
        val reading = LocalReadingRecord("user:a", "comic", "book", "30", "30", page = 8)
        f.local.record(reading); f.local.flush().getOrThrow()
        f.shelf.record(f.sessions.getReadingSessionSync(), reading); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals("30", f.local.latest("user:a", "comic", "book")!!.chapter)
        assertEquals("30", f.shelf.snapshot.value.books(f.sessions.getReadingSessionSync(), "comic").getValue("book").chapter)
        assertFalse(f.progress.progress.value.records.containsKey("book"))
    }

    @Test fun remoteReadingAcknowledgesThisReleaseButKeepsFutureNotifications() = runTest {
        val f = Fixture(backgroundScope)
        f.login(); runCurrent()
        fun catalog(total: Int) = (1..total).map { ShelfChapter("$it", "$it 话", latest = it == total) }
        f.catalog = catalog(100)
        val reading = LocalReadingRecord("user:a", "comic", "book", "100", "100 话", completed = true)
        f.local.record(reading); f.local.flush().getOrThrow()
        f.shelf.record(f.sessions.getReadingSessionSync(), reading); runCurrent()
        f.catalog = catalog(105)
        f.shelf.refresh(f.sessions.getReadingSessionSync(), "comic", setOf("book")); runCurrent()
        fun tracked() = f.shelf.snapshot.value.books(f.sessions.getReadingSessionSync(), "comic").getValue("book")
        assertEquals(ShelfReadingStatus.UPDATED, tracked().status)
        f.manga.history = { Result.success(BrowseHistoryPage(listOf(entry(read = "101", latest = "105")), null)) }
        f.sync.start(); runCurrent()
        assertEquals("101", tracked().chapter)
        assertEquals("100", tracked().caughtUpChapter)
        assertEquals(ShelfReadingStatus.READING, tracked().status)
        f.catalog = catalog(106)
        f.manga.history = { Result.success(BrowseHistoryPage(listOf(entry(read = "101", latest = "106")), null)) }
        f.sync.retry(); runCurrent()
        assertEquals(ShelfReadingStatus.UPDATED, tracked().status)
        assertEquals("106 话", tracked().latestName)
        // 收藏/详情的单独快照先不能覆盖精确历史；历史合并后再一起更新书架。
        f.progress.acceptRemote("user:a", mapOf("book" to BrowseInfo(null, "book", "102", "102 话")),
            f.progress.requestVersion()).getOrThrow()
        runCurrent()
        assertEquals("101", tracked().chapter)
        f.manga.history = { Result.success(BrowseHistoryPage(listOf(entry(read = "102", latest = "106")), null)) }
        f.sync.retry(); runCurrent()
        assertEquals("102", tracked().chapter)
        assertEquals(ShelfReadingStatus.UPDATED, tracked().status)
        f.progress.acceptRemote("user:a", mapOf("book" to BrowseInfo(null, "book", "106", "106 话")),
            f.progress.requestVersion()).getOrThrow()
        runCurrent()
        assertEquals("102", tracked().chapter)
        f.manga.history = { Result.success(BrowseHistoryPage(listOf(entry(read = "106", latest = "106")), null)) }
        f.sync.retry(); runCurrent()
        assertEquals("106", tracked().chapter)
        assertEquals(ShelfReadingStatus.CAUGHT_UP, tracked().status)
        assertEquals("106", tracked().caughtUpChapter)
    }

    @Test fun missingLatestChapterNeverClaimsCaughtUp() = runTest {
        val f = Fixture(backgroundScope)
        f.manga.history = { Result.success(BrowseHistoryPage(
            listOf(entry().copy(latestChapterId = null, latestChapterName = null)), null)) }
        f.login(); f.sync.start(); runCurrent()
        val tracked = f.shelf.snapshot.value.books(f.sessions.getReadingSessionSync(), "comic").getValue("book")
        assertEquals("", tracked.latestChapterId)
        assertEquals(ShelfReadingStatus.READING, tracked.status)
        assertNull(tracked.caughtUpChapter)
    }

    @Test fun startupRepairsStaleShelfFromOnlyTheCompletedLatestChapter() = runTest {
        for (staleChapter in listOf("299", "300", "")) {
            val owner = "user:a"
            val key = ShelfReadingKey(owner, RequestMode.WEB, "comic", "book")
            val local = LocalReadingRecord(owner, "comic", "book", "300", "第300话",
                page = 13, completed = true, atChapterEnd = true, updatedAt = 100)
            val old = ShelfReadingRecord(key, chapter = staleChapter, chapterName = "第299话",
                catalog = listOf("299", "300"), latestChapterId = "300")
            val documents = Documents().apply {
                values["reading-library.json"] = storageJson.encodeToString(LocalReadingSnapshot.serializer(),
                    LocalReadingSnapshot(records = listOf(local)))
                values["reading-progress.json"] = storageJson.encodeToString(ProgressDocument.serializer(),
                    ProgressDocument(owners = mapOf(owner to mapOf("book" to
                        ReadingProgress("300", "第300话", null, 101, false)))))
                values["shelf-reading.json"] = DocumentEnvelope.encode("copymanga.document/shelf-reading.json",
                    PortableShelfReading.SCHEMA_VERSION, storageJson.encodeToJsonElement(ShelfReadingSnapshot.serializer(),
                        ShelfReadingSnapshot(if (staleChapter.isEmpty()) emptyMap() else mapOf(key.id to old))))
            }
            val f = Fixture(backgroundScope, documents)
            f.catalog = listOf(ShelfChapter("299", "第299话"), ShelfChapter("300", "第300话", latest = true))
            f.login(); runCurrent()
            val tracked = f.shelf.snapshot.value.books(f.sessions.getReadingSessionSync(), "comic").getValue("book")
            assertEquals("300", tracked.chapter)
            assertEquals("第300话", tracked.chapterName)
            assertTrue(tracked.completed)
            assertEquals(ShelfReadingStatus.CAUGHT_UP, tracked.status)
            assertEquals(listOf(local), f.local.snapshot.value.records)
            assertEquals(0, f.manga.calls)
        }
    }

    @Test fun unrelatedProgressSnapshotCannotReplay299OverJustCompleted300() = runTest {
        val f = Fixture(backgroundScope)
        f.login(); runCurrent()
        f.catalog = listOf(ShelfChapter("299", "第299话"), ShelfChapter("300", "第300话", latest = true))
        f.progress.acceptRemote("user:a", mapOf("book" to BrowseInfo(null, "book", "299", "第299话")),
            f.progress.requestVersion()).getOrThrow()
        runCurrent()
        // 章节上报与书架落盘使用不同协程；模拟本机完成先被处理的时序。
        val reading = LocalReadingRecord("user:a", "comic", "book", "300", "第300话", page = 13, completed = true)
        f.local.record(reading)
        f.shelf.record(f.sessions.getReadingSessionSync(), reading)
        runCurrent()
        fun tracked() = f.shelf.snapshot.value.books(f.sessions.getReadingSessionSync(), "comic").getValue("book")
        assertEquals(ShelfReadingStatus.CAUGHT_UP, tracked().status)
        f.progress.acceptRemote("user:a", mapOf("other" to BrowseInfo(null, "other", "1", "1")),
            f.progress.requestVersion()).getOrThrow()
        runCurrent()
        assertEquals("300", tracked().chapter)
        assertEquals(ShelfReadingStatus.CAUGHT_UP, tracked().status)
        f.local.flush().getOrThrow()
        f.progress.acceptRemote("user:a", mapOf("book" to BrowseInfo(null, "book", "300", "第300话")),
            f.progress.requestVersion()).getOrThrow()
        runCurrent()
        assertEquals("300", tracked().chapter)
        assertEquals(ShelfReadingStatus.CAUGHT_UP, tracked().status)
    }

    @Test fun matchingCloudLatestDefaultsShelfCompletionWithoutInventingPrecisePosition() = runTest {
        val f = Fixture(backgroundScope)
        f.login(); runCurrent()
        f.catalog = listOf(ShelfChapter("300", "第300话", latest = true))
        f.manga.history = { Result.success(BrowseHistoryPage(listOf(entry(read = "300", latest = "300")), null)) }
        f.sync.start(); runCurrent()
        fun tracked() = f.shelf.snapshot.value.books(f.sessions.getReadingSessionSync(), "comic").getValue("book")
        assertEquals(ShelfReadingStatus.CAUGHT_UP, tracked().status)
        assertTrue(tracked().completed)
        assertFalse(f.local.latest("user:a", "comic", "book")!!.completed)
        assertFalse(f.local.latest("user:a", "comic", "book")!!.atChapterEnd)
        f.local.record(LocalReadingRecord("user:a", "comic", "book", "300", "第300话", page = 13, completed = true))
        f.local.flush().getOrThrow(); runCurrent()
        assertTrue(tracked().completed)
        assertEquals(ShelfReadingStatus.CAUGHT_UP, tracked().status)
    }

    @Test fun cloudLatestDefaultSurvivesRestartAndReceivesFutureUpdates() = runTest {
        val f = Fixture(backgroundScope)
        // 目录暂时不可用也能按云端明确的最新章显示，且保留下一次核对资格。
        f.manga.history = { Result.success(BrowseHistoryPage(listOf(entry(read = "300", latest = "300")), null)) }
        f.login(); f.sync.start(); runCurrent()
        val current = f.shelf.snapshot.value.books(f.sessions.getReadingSessionSync(), "comic").getValue("book")
        assertEquals(ShelfReadingStatus.CAUGHT_UP, current.status)
        assertEquals("300", current.caughtUpChapter)
        assertTrue(current.cloudCatchUpNeedsCatalog)
        assertEquals(0, f.manga.calls)
        val restored = Fixture(backgroundScope, Documents().also { it.values.putAll(f.documents.values) })
        restored.catalog = listOf(ShelfChapter("299", "299"), ShelfChapter("300", "300"),
            ShelfChapter("301", "301", latest = true))
        restored.manga.history = { Result.success(BrowseHistoryPage(listOf(entry(read = "300", latest = "301")), null)) }
        restored.login(); restored.sync.start(); runCurrent()
        val updated = restored.shelf.snapshot.value.books(restored.sessions.getReadingSessionSync(), "comic").getValue("book")
        assertEquals(ShelfReadingStatus.UPDATED, updated.status)
        assertEquals("300", updated.caughtUpChapter)
        assertFalse(updated.cloudCatchUpNeedsCatalog)
        assertEquals(ShelfReadingStatus.UPDATED, updated.read("299", "299", false, 2).status)
        assertEquals(ShelfReadingStatus.READING, updated.read("301", "301", false, 3).status)
    }

    @Test fun localUnfinishedLatestChapterKeepsItsPreciseProgress() = runTest {
        val f = Fixture(backgroundScope)
        f.login(); runCurrent()
        val record = LocalReadingRecord("user:a", "comic", "book", "300", "300", page = 4)
        f.local.record(record); f.local.flush().getOrThrow()
        f.shelf.record(f.sessions.getReadingSessionSync(), record); runCurrent()
        f.catalog = listOf(ShelfChapter("300", "300", latest = true))
        f.manga.history = { Result.success(BrowseHistoryPage(listOf(entry(read = "300", latest = "300")), null)) }
        f.sync.start(); runCurrent()
        val tracked = f.shelf.snapshot.value.books(f.sessions.getReadingSessionSync(), "comic").getValue("book")
        assertEquals(ShelfReadingStatus.READING, tracked.status)
        assertFalse(tracked.completed)
        assertNull(tracked.caughtUpChapter)
        assertEquals(4, f.local.latest("user:a", "comic", "book")!!.page)
    }
}
