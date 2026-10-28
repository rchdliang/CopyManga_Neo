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
        val shelf = PortableShelfReading(documents, sessions, local, scope) { Result.success(emptyList()) }
        val sync = PortableCloudReadingSync(state, manga, progress, local, shelf, scope)
        fun login(owner: String = "user:a") = state.update { it.copy(webSession = PortableSession("token", owner)) }
        fun logout() = state.update { it.copy(webSession = PortableSession()) }
    }
    private fun entry(path: String = "book", read: String = "24", latest: String = "25", id: Long = 1) =
        BrowseHistoryItem(id, "漫画", path, "cover", listOf(Author("作者", "author")), read, "$read 话", latest, "第 $latest 话")

    @Test fun firstLoginImportsEveryPageAndAssumesCaughtUpWithoutClaimingChapterCompletion() = runTest {
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
        assertEquals(ShelfReadingStatus.UPDATED, tracked.getValue("book").status)
        assertEquals(ShelfReadingStatus.CAUGHT_UP, tracked.getValue("second").status)
        assertTrue(tracked.getValue("book").assumedFromCloud)
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
}
