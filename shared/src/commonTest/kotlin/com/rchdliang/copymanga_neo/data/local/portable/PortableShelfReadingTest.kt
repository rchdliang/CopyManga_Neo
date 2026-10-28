package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.ReadingSessionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PortableShelfReadingTest {
    @Test fun updatesFollowTheWholeComicAcrossTranslationGroups() = runTest {
        val docs = Documents()
        val sessions = Sessions()
        val local = PortableLocalReading(docs, backgroundScope)
        var catalog = listOf(ShelfChapter("c23", "23", "default"), ShelfChapter("c24", "24话", "other_honyakuchimu", true))
        val tracking = PortableShelfReading(docs, sessions, local, backgroundScope) { Result.success(catalog) }
        val session = sessions.current
        tracking.record(session, position("comic", 24, true).copy(group = "other_honyakuchimu")); runCurrent()
        assertEquals(ShelfReadingStatus.CAUGHT_UP, tracking.state(session, "comic").status)
        catalog = catalog.map { it.copy(latest = false) } + ShelfChapter("c25", "第25话", "default", true)
        tracking.refresh(session, "comic", setOf("book")); runCurrent()
        assertEquals(ShelfReadingStatus.UPDATED, tracking.state(session, "comic").status)
        assertEquals("第25话", tracking.state(session, "comic").latestName)
        tracking.record(session, position("comic", 25).copy(group = "default")); runCurrent()
        assertEquals(ShelfReadingStatus.READING, tracking.state(session, "comic").status)
        assertEquals(1, tracking.snapshot.value.records.size)
        tracking.record(session, position("comic", 25, true).copy(group = "default")); runCurrent()
        catalog = catalog.map { it.copy(latest = false) } + ShelfChapter("c26", "26", "other_honyakuchimu", true)
        tracking.refresh(session, "comic", setOf("book")); runCurrent()
        tracking.record(session, position("comic", 24).copy(group = "other_honyakuchimu")); runCurrent()
        assertEquals(ShelfReadingStatus.UPDATED, tracking.state(session, "comic").status) // Rereading a known old chapter is not acknowledgement.
    }
    private class Documents : DocumentStorage {
        val values = mutableMapOf<String, String>()
        override suspend fun read(key: String) = values[key]
        override suspend fun write(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
    private class Sessions : ReadingSessionRepository {
        var current = LocalReadingSession(RequestMode.WEB, "owner", 1)
        override val sessionChanges = MutableStateFlow(1L)
        override val cacheReadyFlow = MutableStateFlow(true)
        override suspend fun awaitReady() = Unit
        override fun getReadingSessionSync() = current
        override fun isLoggedInSync() = true
    }
    private fun chapters(count: Int) = (1..count).map { ShelfChapter("c$it", "第 $it 章") }
    private fun position(kind: String, chapter: Int, completed: Boolean = false) =
        LocalReadingRecord("owner", kind, "book", "c$chapter", "第 $chapter 章", completed = completed)
    private fun PortableShelfReading.state(session: LocalReadingSession, kind: String) =
        snapshot.value.books(session, kind).getValue("book")

    @Test fun mangaAndNovelPersistAcknowledgementAndNotifyOnTheNextRelease() = runTest {
        for (kind in listOf("comic", "novel")) {
            val docs = Documents()
            val sessions = Sessions()
            val session = sessions.current
            val local = PortableLocalReading(docs, backgroundScope)
            var total = 100
            var requests = 0
            fun repo() = PortableShelfReading(docs, sessions, local, backgroundScope) {
                requests++; Result.success(chapters(total))
            }
            val tracking = repo()
            tracking.record(session, position(kind, 10, true)); runCurrent()
            assertEquals(ShelfReadingStatus.READING, tracking.state(session, kind).status)
            val initialRequests = requests
            total = 101
            tracking.refresh(session, kind, setOf("book")); runCurrent()
            assertEquals(initialRequests, requests) // No per-book catalogue scans for old backlog.
            assertEquals(ShelfReadingStatus.READING, tracking.state(session, kind).status)
            tracking.record(session, position(kind, 101)); runCurrent()
            assertEquals(ShelfReadingStatus.READING, tracking.state(session, kind).status)
            tracking.record(session, position(kind, 101, true)); runCurrent()
            assertEquals(ShelfReadingStatus.CAUGHT_UP, tracking.state(session, kind).status)
            total = 103
            tracking.refresh(session, kind, setOf("book")); runCurrent()
            assertEquals(ShelfReadingStatus.UPDATED, tracking.state(session, kind).status)
            tracking.record(session, position(kind, 102)); runCurrent()
            assertEquals(ShelfReadingStatus.READING, tracking.state(session, kind).status)
            val restored = repo(); runCurrent()
            assertEquals(ShelfReadingStatus.READING, restored.state(session, kind).status)
            total = 104
            restored.refresh(session, kind, setOf("book")); runCurrent()
            assertEquals(ShelfReadingStatus.UPDATED, restored.state(session, kind).status)
            restored.record(session, position(kind, 104, true)); runCurrent()
            assertEquals(ShelfReadingStatus.CAUGHT_UP, restored.state(session, kind).status)
            total = 105
            restored.refresh(session, kind, setOf("book")); runCurrent()
            assertEquals(ShelfReadingStatus.UPDATED, restored.state(session, kind).status)
        }
    }

    @Test fun responseAfterNewReadingCannotRecreateBadgeAndOldSessionsAreRejected() = runTest {
        val docs = Documents()
        val sessions = Sessions()
        val session = sessions.current
        val local = PortableLocalReading(docs, backgroundScope)
        var total = 100
        var gate: CompletableDeferred<Unit>? = null
        val tracking = PortableShelfReading(docs, sessions, local, backgroundScope) {
            gate?.await(); Result.success(chapters(total))
        }
        tracking.record(session, position("comic", 100, true)); runCurrent()
        gate = CompletableDeferred()
        total = 102
        tracking.refresh(session, "comic", setOf("book")); runCurrent()
        tracking.record(session, position("comic", 101)); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(ShelfReadingStatus.READING, tracking.state(session, "comic").status)
        assertEquals("c100", tracking.state(session, "comic").caughtUpChapter)

        gate = CompletableDeferred()
        tracking.record(session, position("comic", 102, true)); runCurrent()
        sessions.current = session.copy(mode = RequestMode.OFFICIAL_APP, generation = 2)
        gate.complete(Unit); runCurrent()
        assertEquals("c100", tracking.state(session, "comic").caughtUpChapter)
        assertEquals(ShelfReadingStatus.READING, tracking.state(sessions.current, "comic").status)
        tracking.record(session, position("comic", 102, true)); runCurrent()
        assertEquals(ShelfReadingStatus.READING, tracking.state(sessions.current, "comic").status)
        assertTrue(tracking.snapshot.value.books(sessions.current.copy(owner = "other-account"), "comic").isEmpty())
    }

    @Test fun offlineCompletionWaitsForFullOnlineCatalogueAndHistoryDeletionClearsBaseline() = runTest {
        val docs = Documents()
        val sessions = Sessions()
        val session = sessions.current
        val local = PortableLocalReading(docs, backgroundScope)
        var offline = true
        val tracking = PortableShelfReading(docs, sessions, local, backgroundScope) {
            if (offline) Result.failure(Exception("offline")) else Result.success(chapters(100))
        }
        tracking.record(session, position("comic", 100, true)); runCurrent()
        assertEquals(ShelfReadingStatus.READING, tracking.state(session, "comic").status)
        offline = false
        tracking.refresh(session, "comic", setOf("book")); runCurrent()
        assertEquals(ShelfReadingStatus.CAUGHT_UP, tracking.state(session, "comic").status)
        local.clearHistory("owner").getOrThrow(); runCurrent()
        assertTrue(tracking.snapshot.value.records.isEmpty())
        val restored = PortableShelfReading(docs, sessions, local, backgroundScope) { Result.success(chapters(101)) }
        restored.refresh(session, "comic", setOf("book")); runCurrent()
        assertTrue(restored.snapshot.value.records.isEmpty())
    }

    @Test fun legacyReadingHistoryDoesNotInventCatchUpEvidence() = runTest {
        val docs = Documents()
        val sessions = Sessions()
        val local = PortableLocalReading(docs, backgroundScope)
        local.record(position("comic", 100, true)); local.flush().getOrThrow()
        var requests = 0
        val tracking = PortableShelfReading(docs, sessions, local, backgroundScope) {
            requests++; Result.success(chapters(101))
        }
        tracking.refresh(sessions.current, "comic", setOf("book")); runCurrent()
        assertTrue(tracking.snapshot.value.records.isEmpty())
        assertEquals(0, requests)
    }

    @Test fun queuedReadingPreservesOrderAndCannotResurrectDeletedHistory() = runTest {
        val docs = Documents()
        val sessions = Sessions()
        val local = PortableLocalReading(docs, backgroundScope)
        val tracking = PortableShelfReading(docs, sessions, local, backgroundScope) { Result.success(chapters(102)) }
        runCurrent()
        tracking.record(sessions.current, position("comic", 100, true))
        tracking.record(sessions.current, position("comic", 101))
        runCurrent()
        assertEquals("c101", tracking.state(sessions.current, "comic").chapter)
        assertEquals(ShelfReadingStatus.READING, tracking.state(sessions.current, "comic").status)
        tracking.record(sessions.current, position("comic", 102, true))
        local.clearHistory("owner").getOrThrow()
        runCurrent()
        assertTrue(tracking.snapshot.value.records.isEmpty())
        // A genuine read after clearing may establish new evidence.
        tracking.record(sessions.current, position("comic", 102, true)); runCurrent()
        assertEquals(ShelfReadingStatus.CAUGHT_UP, tracking.state(sessions.current, "comic").status)
    }

    @Test fun modeSwitchKeepsCatchUpAndAcknowledgementButUsesCurrentProtocol() = runTest {
        for (kind in listOf("comic", "novel")) {
            val docs = Documents()
            val sessions = Sessions()
            val web = sessions.current
            val app = web.copy(mode = RequestMode.OFFICIAL_APP, generation = 2)
            val local = PortableLocalReading(docs, backgroundScope)
            var total = 100
            var requestedMode: RequestMode? = null
            val tracking = PortableShelfReading(docs, sessions, local, backgroundScope) { key ->
                requestedMode = key.mode
                Result.success(chapters(total))
            }
            tracking.record(web, position(kind, 100, true)); runCurrent()
            sessions.current = app
            assertEquals(ShelfReadingStatus.CAUGHT_UP, tracking.state(app, kind).status)
            total = 102
            tracking.refresh(app, kind, setOf("book")); runCurrent()
            assertEquals(RequestMode.OFFICIAL_APP, requestedMode)
            assertEquals(ShelfReadingStatus.UPDATED, tracking.state(app, kind).status)
            tracking.record(app, position(kind, 101)); runCurrent()
            sessions.current = web.copy(generation = 3)
            assertEquals(ShelfReadingStatus.READING, tracking.state(sessions.current, kind).status)
            assertEquals(1, tracking.snapshot.value.records.size)
            val restored = PortableShelfReading(docs, sessions, local, backgroundScope) { Result.success(chapters(103)) }
            restored.refresh(sessions.current, kind, setOf("book")); runCurrent()
            assertEquals(ShelfReadingStatus.UPDATED, restored.state(sessions.current, kind).status)
        }
    }

    @Test fun migratesModeScopedRecordsUsingLatestReadingWithoutResurrectingBadge() = runTest {
        val docs = Documents()
        val sessions = Sessions()
        val local = PortableLocalReading(docs, backgroundScope)
        val key = ShelfReadingKey("owner", RequestMode.WEB, "novel", "book")
        val waiting = ShelfReadingRecord(key).read("c100", "第 100 章", true, 1)
            .observeCatalog(chapters(100)).observeCatalog(chapters(102))
        val started = waiting.copy(key = key.copy(mode = RequestMode.OFFICIAL_APP)).read("c101", "第 101 章", false, 2)
        fun legacyId(key: ShelfReadingKey) = com.rchdliang.copymanga_neo.domain.reading.ReadingMergePolicy.key(
            key.owner, key.mode.name, key.kind, key.path, key.group)
        val old = ShelfReadingSnapshot(mapOf(legacyId(waiting.key) to waiting, legacyId(started.key) to started))
        docs.values["shelf-reading.json"] = DocumentEnvelope.encode("copymanga.document/shelf-reading.json", 1,
            storageJson.encodeToJsonElement(ShelfReadingSnapshot.serializer(), old))
        val tracking = PortableShelfReading(docs, sessions, local, backgroundScope) { Result.success(chapters(102)) }
        tracking.refresh(sessions.current, "novel", setOf("book")); runCurrent()
        assertEquals(1, tracking.snapshot.value.records.size)
        assertEquals(ShelfReadingStatus.READING, tracking.state(sessions.current, "novel").status)
        assertEquals("c101", tracking.state(sessions.current, "novel").chapter)
        assertTrue(docs.values.containsKey("shelf-reading.json.before-v3"))
        // 迁移后的新版本仍可通过个人数据备份导出。
        assertTrue(PortableUserDataBackup(docs).export().contains("shelf-reading.json"))
    }

    @Test fun migrationRemovesAssumedCatchUpButKeepsRealEvidence() = runTest {
        val docs = Documents()
        val sessions = Sessions()
        val key = ShelfReadingKey("owner", RequestMode.WEB, "comic", "imported")
        val assumed = ShelfReadingRecord(key, chapter = "c10", caughtUpChapter = "c10",
            catalog = listOf("c10", "c100"), latestChapterId = "c100",
            knownWhenCaughtUp = setOf("c10"), assumedFromCloud = true)
        val real = ShelfReadingRecord(key.copy(path = "real"))
            .read("c100", "100", true, 1).observeCatalog(chapters(100))
        val previous = ShelfReadingSnapshot(listOf(assumed, real).associateBy { it.key.id })
        docs.values["shelf-reading.json"] = DocumentEnvelope.encode("copymanga.document/shelf-reading.json", 2,
            storageJson.encodeToJsonElement(ShelfReadingSnapshot.serializer(), previous))
        val local = PortableLocalReading(docs, backgroundScope)
        val shelf = PortableShelfReading(docs, sessions, local, backgroundScope) { Result.success(chapters(101)) }
        runCurrent()
        val books = shelf.snapshot.value.books(sessions.current, "comic")
        assertNull(books.getValue("imported").caughtUpChapter)
        assertFalse(books.getValue("imported").assumedFromCloud)
        assertEquals(ShelfReadingStatus.READING, books.getValue("imported").status)
        assertEquals("c100", books.getValue("real").caughtUpChapter)
        assertEquals(ShelfReadingStatus.CAUGHT_UP, books.getValue("real").status)
        assertTrue(docs.values.containsKey("shelf-reading.json.before-v3"))
        assertTrue(PortableUserDataBackup(docs).export().contains("shelf-reading.json"))
    }
}
