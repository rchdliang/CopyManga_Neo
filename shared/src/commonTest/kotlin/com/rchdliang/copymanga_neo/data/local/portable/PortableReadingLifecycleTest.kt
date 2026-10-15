package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.Serializable
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PortableReadingLifecycleTest {
    private class Documents : DocumentStorage {
        val values = mutableMapOf<String, String>()
        var readGate: CompletableDeferred<Unit>? = null
        var writeFailure = false
        override suspend fun read(key: String): String? { readGate?.await(); return values[key] }
        override suspend fun write(key: String, value: String) {
            check(!writeFailure) { "disk full" }; values[key] = value
        }
        override suspend fun remove(key: String) { values.remove(key) }
    }
    @Serializable private data class ProgressData(
        val owners: Map<String, Map<String, ReadingProgress>> = emptyMap(),
        val cleared: Map<String, Long> = emptyMap()
    )
    private fun Documents.progress(data: ProgressData) {
        values["reading-progress.json"] = storageJson.encodeToString(ProgressData.serializer(), data)
    }
    private fun state(owner: String = "user:a") = PortableNetworkState(NetworkSnapshot(webSession = PortableSession("token", owner)))
    private class Manga : MangaRepository {
        var calls = 0
        var clears = 0
        var submit: suspend () -> Result<TrialContent> = { Result.failure(IllegalStateException("offline")) }
        var confirm: suspend () -> Result<ComicStatus> = { Result.success(ComicStatus(BrowseInfo(null, "book", "chapter", "chapter"), null)) }
        var clear: suspend () -> Result<Unit> = { Result.success(Unit) }
        override val cachedComicThemes = MutableStateFlow<Map<String, CachedComicThemes>>(emptyMap())
        override suspend fun trialChapterContent(comicPathWord: String, chapterUuid: String): Result<TrialContent> { calls++; return submit() }
        override suspend fun queryComicStatus(pathWord: String) = confirm()
        override suspend fun clearBrowseHistory(): Result<Unit> { clears++; return clear() }
        override suspend fun getHomeComics(): Nothing = error("unused")
        override suspend fun getComicDetail(pathWord: String): Nothing = error("unused")
        override suspend fun getComicThemes(pathWord: String): Nothing = error("unused")
        override suspend fun refreshComicThemes(pathWord: String): Nothing = error("unused")
        override suspend fun getChapterCatalog(pathWord: String, forceRefresh: Boolean): Nothing = error("unused")
        override suspend fun getChapters(pathWord: String, group: String, offset: Int, limit: Int): Nothing = error("unused")
        override suspend fun getChapterInfo(comicPathWord: String, chapterUuid: String, group: String): Nothing = error("unused")
        override suspend fun getChapterContent(comicPathWord: String, chapterUuid: String, group: String): Nothing = error("unused")
        override suspend fun searchComics(query: String, page: Int, queryType: QueryType?): Nothing = error("unused")
        override suspend fun getRecComics(limit: Int, offset: Int): Nothing = error("unused")
        override suspend fun getThemeComics(theme: String, offset: Int): Nothing = error("unused")
        override suspend fun getFavoriteComics(): Nothing = error("unused")
        override suspend fun getBrowseHistory(offset: Int): Nothing = error("unused")
        override suspend fun addFavorite(comicId: String): Nothing = error("unused")
        override suspend fun removeFavorite(collectId: Int): Nothing = error("unused")
        override suspend fun removeFavorites(collectIds: List<Int>): Nothing = error("unused")
    }
    @Test fun persistedPendingSyncWaitsForInitialLoad() = runTest {
        val docs = Documents()
        docs.progress(ProgressData(mapOf("user:a" to mapOf("book" to ReadingProgress("chapter", "chapter", "default", 1, true)))))
        docs.readGate = CompletableDeferred()
        val manga = Manga()
        val repo = PortableReadingProgress(docs, state(), manga, backgroundScope)
        runCurrent(); assertEquals(0, manga.calls)
        docs.readGate!!.complete(Unit); runCurrent()
        assertEquals(1, manga.calls)
        assertTrue(repo.awaitReady("user:a").records["book"]!!.pendingSync)
    }
    @Test fun thrownSyncFailureDoesNotKillExplicitRetryCollector() = runTest {
        val manga = Manga().apply { submit = { error("transport threw") } }
        val repo = PortableReadingProgress(Documents(), state(), manga, backgroundScope)
        runCurrent(); repo.recordRead("user:a", "book", "chapter", "chapter", "default"); runCurrent()
        val calls = manga.calls
        repo.retryPending(); runCurrent()
        assertEquals(calls + 1, manga.calls)
        assertTrue(repo.progress.value.records["book"]!!.pendingSync)
    }
    @Test fun corruptLoadFinishesWithErrorAndNeverClearsRemote() = runTest {
        val docs = Documents().apply { values["reading-progress.json"] = "{broken" }
        val manga = Manga()
        val repo = PortableReadingProgress(docs, state(), manga, backgroundScope)
        runCurrent()
        assertNotNull(repo.awaitReady("user:a").error)
        assertTrue(repo.clearHistory().isFailure)
        assertEquals(0, manga.clears)
        assertEquals("{broken", docs.values["reading-progress.json"])
    }
    @Test fun retryVersionDoesNotBecomeReadyUntilReloadFinishes() = runTest {
        val docs = Documents()
        val repo = PortableReadingProgress(docs, state(), Manga(), backgroundScope)
        runCurrent()
        docs.readGate = CompletableDeferred()
        val version = repo.retryLoading(); runCurrent()
        val waiting = async { repo.awaitReady("user:a", version) }
        runCurrent(); assertFalse(waiting.isCompleted)
        docs.readGate!!.complete(Unit); runCurrent()
        assertEquals(version, waiting.await().loadVersion)
    }
    @Test fun sameOwnerAfterAccountRoundTripRejectsOldRemoteResponse() = runTest {
        val state = state()
        val repo = PortableReadingProgress(Documents(), state, Manga(), backgroundScope)
        runCurrent(); val version = repo.requestVersion()
        state.update { it.copy(webSession = PortableSession("other", "user:b")) }
        state.update { it.copy(webSession = PortableSession("token", "user:a")) }
        repo.acceptRemote("user:a", mapOf("book" to BrowseInfo(null, "book", "stale", "stale")), version).getOrThrow()
        runCurrent(); assertTrue(repo.progress.value.records.isEmpty())
    }
    @Test fun persistedFutureTombstoneDoesNotRejectNewReading() = runTest {
        val future = kotlin.time.Clock.System.now().toEpochMilliseconds() + 1_000_000
        val docs = Documents().apply { progress(ProgressData(cleared = mapOf("guest" to future))) }
        val repo = PortableReadingProgress(docs, state("guest"), Manga(), backgroundScope)
        runCurrent(); repo.recordRead("guest", "book", "new", "new", "default"); runCurrent()
        assertTrue(repo.progress.value.records.getValue("book").version > future)
    }
    @Test fun localReadingInitializesWithoutExternalRetryCall() = runTest {
        val record = LocalReadingRecord("guest", "comic", "book", "chapter", "chapter")
        val docs = Documents().apply {
            values["reading-library.json"] = storageJson.encodeToString(LocalReadingSnapshot.serializer(), LocalReadingSnapshot(records = listOf(record)))
        }
        val repo = PortableLocalReading(docs, backgroundScope); runCurrent()
        assertEquals(record, repo.position("guest", "comic", "book", "chapter"))
    }
    @Test fun mergeAdvancesVersionBeyondImportedDeletion() = runTest {
        val repo = PortableLocalReading(Documents(), backgroundScope); runCurrent()
        val future = kotlin.time.Clock.System.now().toEpochMilliseconds() + 1_000_000
        repo.merge(LocalReadingSnapshot(clearedOwners = mapOf("guest" to future))).getOrThrow()
        repo.record(LocalReadingRecord("guest", "comic", "book", "chapter", "chapter"))
        repo.flush().getOrThrow()
        assertTrue(repo.snapshot.value.records.single().updatedAt > future)
    }
    @Test fun deleteFencesQueuedPositionsButAllowsLaterReading() = runTest {
        val repo = PortableLocalReading(Documents(), backgroundScope); runCurrent()
        val r = LocalReadingRecord("guest", "comic", "book", "chapter", "chapter")
        repo.record(r); repo.clearHistory("guest").getOrThrow(); repo.flush().getOrThrow()
        assertTrue(repo.snapshot.value.records.isEmpty())
        repo.record(r.copy(page = 5)); repo.flush().getOrThrow()
        assertEquals(5, repo.snapshot.value.records.single().page)
    }
    @Test fun failedDeleteRetainsPendingCompletedReading() = runTest {
        val docs = Documents()
        val repo = PortableLocalReading(docs, backgroundScope); runCurrent()
        val r = LocalReadingRecord("guest", "comic", "book", "chapter", "chapter", completed = true)
        repo.record(r); docs.writeFailure = true
        assertTrue(repo.clearHistory("guest").isFailure)
        docs.writeFailure = false; repo.record(r.copy(completed = false)); repo.flush().getOrThrow()
        assertTrue(repo.snapshot.value.records.single().completed)
    }
    @Test fun newStartupReadSurvivesFuturePersistedTombstone() = runTest {
        val future = kotlin.time.Clock.System.now().toEpochMilliseconds() + 1_000_000
        val docs = Documents().apply {
            values["reading-library.json"] = storageJson.encodeToString(LocalReadingSnapshot.serializer(), LocalReadingSnapshot(clearedOwners = mapOf("guest" to future)))
        }
        val repo = PortableLocalReading(docs, backgroundScope)
        repo.record(LocalReadingRecord("guest", "comic", "book", "chapter", "chapter"))
        repo.flush().getOrThrow()
        assertTrue(repo.snapshot.value.records.single().updatedAt > future)
    }
    @Test fun semanticallyCorruptLibraryIsPreservedAndBlocksMutationsUntilRetry() = runTest {
        val record = LocalReadingRecord("guest", "comic", "book", "chapter", "chapter", updatedAt = 1)
        val corrupt = listOf(
            LocalReadingSnapshot(clearedOwners = mapOf("guest" to Long.MAX_VALUE)),
            LocalReadingSnapshot(records = listOf(record, record)),
            LocalReadingSnapshot(records = listOf(record.copy(kind = "unknown"))),
            LocalReadingSnapshot(records = listOf(record.copy(page = -1)))
        )
        for (snapshot in corrupt) {
            val docs = Documents()
            val original = storageJson.encodeToString(LocalReadingSnapshot.serializer(), snapshot)
            docs.values["reading-library.json"] = original
            val repo = PortableLocalReading(docs, backgroundScope)
            runCurrent()
            assertNotNull(repo.error.value)
            assertTrue(repo.snapshot.value.records.isEmpty())
            repo.record(record)
            assertTrue(repo.flush().isFailure)
            assertTrue(repo.clearHistory("guest").isFailure)
            assertEquals(original, docs.values["reading-library.json"])
            docs.values["reading-library.json"] = storageJson.encodeToString(LocalReadingSnapshot.serializer(), LocalReadingSnapshot())
            repo.retryRead().getOrThrow()
            repo.flush().getOrThrow()
            assertNull(repo.error.value)
            assertEquals("book", repo.snapshot.value.records.single().path)
        }
    }

    @Test fun oversizedLibraryWriteRetainsPriorDiskAndMemoryState() = runTest {
        val docs = Documents()
        val repo = PortableLocalReading(docs, backgroundScope); runCurrent()
        val existing = LocalReadingRecord("guest", "comic", "existing", "chapter", "chapter", updatedAt = 1)
        repo.merge(LocalReadingSnapshot(records = listOf(existing))).getOrThrow()
        val original = docs.values["reading-library.json"]
        val oversized = LocalReadingSnapshot(records = List(1100) { index ->
            existing.copy(path = "book-$index", title = "a".repeat(8192))
        })
        assertTrue(repo.merge(oversized).isFailure)
        assertEquals(original, docs.values["reading-library.json"])
        assertEquals(listOf(existing), repo.snapshot.value.records)
        assertNotNull(repo.error.value)
    }

    @Test fun oversizedLibraryReadIsRejectedWithoutReplacingItsBytes() = runTest {
        val docs = Documents()
        val original = storageJson.encodeToString(LocalReadingSnapshot.serializer(),
            LocalReadingSnapshot(deleted = mapOf("a".repeat(PortableBackupCodec.MAX_READING_BYTES) to 1L)))
        docs.values["reading-library.json"] = original
        val repo = PortableLocalReading(docs, backgroundScope); runCurrent()
        assertNotNull(repo.error.value)
        assertTrue(repo.clearHistory("guest").isFailure)
        assertEquals(original, docs.values["reading-library.json"])
    }

}
