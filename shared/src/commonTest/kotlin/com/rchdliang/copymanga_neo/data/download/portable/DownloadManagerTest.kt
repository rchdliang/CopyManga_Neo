package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import com.rchdliang.copymanga_neo.data.local.portable.PortableAppSettings
import com.rchdliang.copymanga_neo.data.storage.CacheStorage
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import com.rchdliang.copymanga_neo.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadManagerTest {
    private class Documents : DocumentStorage {
        val rows = mutableMapOf<String, String>()
        override suspend fun read(key: String) = rows[key]
        override suspend fun write(key: String, value: String) { rows[key] = value }
        override suspend fun remove(key: String) { rows.remove(key) }
    }
    private class Files : CacheStorage {
        val rows = mutableMapOf<String, ByteArray>()
        override suspend fun read(path: String, limit: Int) = rows[path]?.also { require(it.size <= limit) }
        override suspend fun write(path: String, bytes: ByteArray) { rows[path] = bytes }
        override suspend fun list(prefix: String) = rows.keys.filter { it.startsWith(prefix) }
        override suspend fun remove(path: String) { rows.remove(path) }
        override suspend fun size(path: String) = rows[path]?.size?.toLong() ?: throw PlatformIOException("missing")
        override suspend fun availableBytes() = 1_000_000L
        override suspend fun imageLocation(path: String) = "file:///cache/$path"
    }
    private class Manga : MangaRepository {
        var contentCalls = 0
        var imageUrls = listOf("https://test.invalid/page.jpg")
        var failure: Throwable? = null
        override val cachedComicThemes = MutableStateFlow<Map<String, CachedComicThemes>>(emptyMap())
        override suspend fun getChapterDownloadContent(comicPathWord: String, chapterUuid: String, group: String): Result<ChapterContent> {
            contentCalls++
            return failure?.let { Result.failure(it) } ?: Result.success(ChapterContent(imageUrls, null, null))
        }
        override suspend fun getHomeComics(): Nothing = error("unused")
        override suspend fun getComicDetail(pathWord: String): Nothing = error("unused")
        override suspend fun getComicThemes(pathWord: String): Nothing = error("unused")
        override suspend fun refreshComicThemes(pathWord: String): Nothing = error("unused")
        override suspend fun getChapterCatalog(pathWord: String, forceRefresh: Boolean): Nothing = error("unused")
        override suspend fun getChapters(pathWord: String, group: String, offset: Int, limit: Int): Nothing = error("unused")
        override suspend fun getChapterInfo(comicPathWord: String, chapterUuid: String, group: String): Nothing = error("unused")
        override suspend fun trialChapterContent(comicPathWord: String, chapterUuid: String): Nothing = error("unused")
        override suspend fun getChapterContent(comicPathWord: String, chapterUuid: String, group: String): Nothing = error("unused")
        override suspend fun searchComics(query: String, page: Int, queryType: QueryType?): Nothing = error("unused")
        override suspend fun getRecComics(limit: Int, offset: Int): Nothing = error("unused")
        override suspend fun getThemeComics(theme: String, offset: Int): Nothing = error("unused")
        override suspend fun getFavoriteComics(): Nothing = error("unused")
        override suspend fun getBrowseHistory(offset: Int): Nothing = error("unused")
        override suspend fun clearBrowseHistory(): Nothing = error("unused")
        override suspend fun queryComicStatus(pathWord: String): Nothing = error("unused")
        override suspend fun addFavorite(comicId: String): Nothing = error("unused")
        override suspend fun removeFavorite(collectId: Int): Nothing = error("unused")
        override suspend fun removeFavorites(collectIds: List<Int>): Nothing = error("unused")
    }
    private object Novels : NovelRepository {
        override val cachedVolumes = MutableStateFlow<List<NovelCachedVolume>>(emptyList())
        override suspend fun refreshCachedVolumes() = Result.success(Unit)
        override suspend fun discover(mode: RequestMode, owner: String, offset: Int, order: String, theme: String, author: String): Nothing = error("unused")
        override suspend fun search(mode: RequestMode, owner: String, offset: Int, query: String, type: String): Nothing = error("unused")
        override suspend fun tags(mode: RequestMode, owner: String): Nothing = error("unused")
        override suspend fun detail(mode: RequestMode, owner: String, path: String): Nothing = error("unused")
        override suspend fun volumes(mode: RequestMode, owner: String, path: String): Nothing = error("unused")
        override suspend fun readVolume(mode: RequestMode, owner: String, path: String, id: String, allImages: Boolean, refresh: Boolean, onProgress: (NovelCacheProgress) -> Unit, forDownload: Boolean): Nothing = error("unused")
        override suspend fun removeVolume(mode: RequestMode, owner: String, path: String, id: String): Nothing = error("unused")
        override suspend fun removeBook(mode: RequestMode, owner: String, path: String): Nothing = error("unused")
        override suspend fun cacheUsage(): Nothing = error("unused")
        override suspend fun clearCache(): Nothing = error("unused")
    }
    private object Sessions : ReadingSessionRepository {
        override val cacheReadyFlow = flowOf(true)
        override val sessionChanges = MutableStateFlow(0L)
        override suspend fun awaitReady() = Unit
        override fun getReadingSessionSync() = LocalReadingSession(RequestMode.WEB, "guest", 1)
        override fun isLoggedInSync() = false
    }
    private object NetworkSettings : NetworkSettingsRepository {
        override val settings = MutableStateFlow(com.rchdliang.copymanga_neo.domain.model.NetworkSettings())
        override val officialAppDefaults = OfficialAppSettings()
        override fun generatePseudoId(): Nothing = error("unused")
        override fun generateUmString(): Nothing = error("unused")
        override suspend fun diagnoseOfficialApp(): Nothing = error("unused")
        override suspend fun save(settings: com.rchdliang.copymanga_neo.domain.model.NetworkSettings): Nothing = error("unused")
        override suspend fun reset(): Nothing = error("unused")
        override suspend fun rollback(): Nothing = error("unused")
        override suspend fun discover(): Nothing = error("unused")
        override suspend fun test(settings: com.rchdliang.copymanga_neo.domain.model.NetworkSettings): Nothing = error("unused")
        override suspend fun refreshIfNeeded(): Nothing = error("unused")
    }
    private class Comments(private val records: DownloadRecords) : CommentRepository {
        override suspend fun getDownloadedChapterComments(uuid: String) = Result.success(records.readComments(uuid))
        override suspend fun downloadChapterComments(uuid: String): Result<DownloadedChapterComments> {
            val snapshot = DownloadedChapterComments(emptyList(), 0, true, savedAt = 1)
            records.saveComments(uuid, snapshot)
            return Result.success(snapshot)
        }
        override suspend fun getReportConfig(target: CommentTarget): Nothing = error("unused")
        override suspend fun reportComment(target: CommentTarget, commentId: String, type: Int, reason: String): Nothing = error("unused")
        override suspend fun getReplyPreview(target: CommentTarget, commentId: String): Nothing = error("unused")
        override suspend fun getComments(target: CommentTarget, offset: Int, replyId: String?): Nothing = error("unused")
        override suspend fun postComment(target: CommentTarget, content: String, replyId: String?): Nothing = error("unused")
    }
    private class Images : DownloadImageTransport {
        var calls = 0
        val requestedUrls = mutableListOf<String>()
        var gateAfterCalls = 0
        var gate: CompletableDeferred<Unit>? = null
        var cleanupGate: CompletableDeferred<Unit>? = null
        var cancelled = 0
        var failureUrl: String? = null
        override suspend fun read(url: String, policy: DownloadNetworkState, onBytes: (Int) -> Unit): ByteArray {
            calls++
            requestedUrls += url
            if (url == failureUrl) throw PlatformIOException("image failed")
            try { if (calls > gateAfterCalls) gate?.await() } catch (cancel: CancellationException) { cancelled++; throw cancel }
            finally { cleanupGate?.let { withContext(NonCancellable) { it.await() } } }
            onBytes(3)
            return byteArrayOf(1, 2, 3)
        }
    }
    private class Fixture(scope: CoroutineScope, val documents: Documents = Documents(), val files: Files = Files(),
                          network: NetworkSettingsRepository = NetworkSettings) {
        val records = DownloadRecords(documents, files)
        val queue = DownloadQueueStore(documents)
        val manga = Manga()
        val images = Images()
        val connection = MutableStateFlow(DownloadConnection(true, true, "test"))
        val settings = PortableAppSettings(documents)
        val policy = DownloadNetworkPolicy(settings, object : DownloadNetworkMonitor {
            override val connections = connection
        }, scope)
        var foregroundRequests = 0
        var foregroundGate: CompletableDeferred<Unit>? = null
        val manager = DownloadManager(manga, records, files, images,
            object : DownloadRuntime {
                override fun onWorkAvailable() { foregroundRequests++ }
                override suspend fun awaitReady() { onWorkAvailable(); foregroundGate?.await() }
            },
            queue, policy, network, Comments(records), LocalDownloadAccess(), Novels, Sessions, scope)
    }
    private fun task() = ComicDownloadTask("book", "作品", null, listOf(ChapterDownloadItem(
        ChapterInfo(1, "chapter", 1, 1, null, "第一章", "id", "book", null, "default", 0, null, null, null, null),
        ChapterDownloadStatus.WAITING
    )), "作者", "连载", null, null, taskId = "task")

    @Test fun workerCountIsBoundedAndPauseCancelsEveryInFlightImage() = runTest {
        val f = Fixture(backgroundScope)
        f.manga.imageUrls = (0..9).map { "https://test.invalid/$it.jpg" }
        f.manager.setImageConcurrency(3)
        f.images.gate = CompletableDeferred()
        runCurrent(); f.manager.enqueue(task()); runCurrent()
        assertEquals(3, f.images.calls)
        f.manager.pauseDownload(task().taskKey).join(); runCurrent()
        assertEquals(3, f.images.cancelled)
        assertNull(f.records.getChapter("chapter"))
        f.images.gate = null
        f.manager.resumeDownload(task().taskKey).join(); runCurrent()
        val chapter = assertNotNull(f.records.getChapter("chapter"))
        assertEquals((0..9).map { "${it.toString().padStart(3, '0')}.jpg" }, chapter.imagePaths.map { it.substringAfterLast('/') })
        assertTrue(f.records.hasCompleteFiles(chapter))
    }

    @Test fun failedWorkerCancelsSiblingsBeforeErrorAndNeverCommitsChapter() = runTest {
        val f = Fixture(backgroundScope)
        f.manga.imageUrls = (0..9).map { "https://test.invalid/$it.jpg" }
        f.images.gate = CompletableDeferred()
        f.images.failureUrl = f.manga.imageUrls[2]
        runCurrent(); f.manager.enqueue(task()); runCurrent()
        assertEquals(3, f.images.calls)
        assertEquals(2, f.images.cancelled)
        assertNull(f.records.getChapter("chapter"))
        assertEquals(DownloadTaskStatus.ERROR, f.manager.downloadProgress.value[task().taskKey]?.status)
    }

    @Test fun corruptQueueStopsBackgroundCommandsWithoutReplacingDurableData() = runTest {
        val documents = Documents()
        val original = "{broken queue"
        documents.rows["download-queue"] = original
        val f = Fixture(backgroundScope, documents)
        assertFailsWith<Exception> { f.manager.awaitRestoration() }
        runCurrent()
        f.manager.enqueue(task())
        f.manager.pauseDownload(task().taskKey).join()
        f.manager.resumeDownload(task().taskKey).join()
        f.manager.pauseAll().join()
        runCurrent()
        assertFailsWith<Exception> { f.manager.cancelAll() }
        assertEquals(original, documents.rows["download-queue"])
        assertEquals(0, f.manga.contentCalls)
        assertEquals(0, f.images.calls)
        assertTrue(f.manager.tasks.value.isEmpty())
    }

    @Test fun downloadsPersistOnlyAfterCompletedImagesAndComments() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent(); f.manager.awaitRestoration()
        f.manager.enqueue(task()); runCurrent()
        assertEquals(1, f.manga.contentCalls)
        assertEquals(1, f.images.calls)
        assertTrue(f.records.hasCompleteFiles(assertNotNull(f.records.getChapter("chapter"))))
        assertTrue(f.records.readComments("chapter")?.complete == true)
        assertTrue(f.manager.tasks.value.isEmpty())
        assertTrue(f.queue.load().isEmpty())
    }

    @Test fun manualPauseCancelsInFlightIoAndResumeCompletes() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent()
        f.images.gate = CompletableDeferred()
        f.manager.enqueue(task()); runCurrent()
        f.manager.pauseDownload(task().taskKey); runCurrent()
        assertEquals(1, f.images.cancelled)
        assertNull(f.records.getChapter("chapter"))
        assertEquals(DownloadTaskStatus.PAUSED, f.manager.downloadProgress.value[task().taskKey]?.status)
        assertEquals(ChapterDownloadStatus.WAITING, f.manager.tasks.value.single().items.single().status)
        f.images.gate = null
        f.manager.resumeDownload(task().taskKey); runCurrent()
        assertNotNull(f.records.getChapter("chapter"))
        assertEquals(2, f.manga.contentCalls)
    }

    @Test fun themeLoadingChangesDoNotPauseQueuedNovelButHostChangesStillDo() = runTest {
        val config = MutableStateFlow(com.rchdliang.copymanga_neo.domain.model.NetworkSettings())
        val network = object : NetworkSettingsRepository by NetworkSettings {
            override val settings = config
        }
        val f = Fixture(backgroundScope, network = network)
        f.settings.setWifiOnlyDownloads(true).getOrThrow()
        f.connection.value = DownloadConnection(true, false)
        runCurrent()
        val novel = NovelDownloadTask(RequestMode.WEB, "guest", NovelBook("novel", "Novel", "", emptyList()),
            listOf(NovelDownloadItem(NovelVolume("volume", "Volume", 1, null, null))))
        f.manager.enqueue(novel); runCurrent()
        assertEquals(DownloadTaskStatus.WAITING_FOR_WIFI, f.manager.downloadProgress.value[novel.taskKey]?.status)
        config.value = config.value.copy(themeFetchConcurrency = 5, themeFetchIntervalMillis = 1000)
        runCurrent()
        assertEquals(DownloadTaskStatus.WAITING_FOR_WIFI, f.manager.downloadProgress.value[novel.taskKey]?.status)
        config.value = config.value.copy(apiHost = "changed.example")
        runCurrent()
        assertEquals(DownloadTaskStatus.PAUSED, f.manager.downloadProgress.value[novel.taskKey]?.status)
    }

    @Test fun disallowedNetworkDoesNotConsumeOfficialAuthorization() = runTest {
        val f = Fixture(backgroundScope)
        f.settings.setWifiOnlyDownloads(true).getOrThrow()
        f.connection.value = DownloadConnection(true, false)
        runCurrent()
        f.manager.enqueue(task()); runCurrent()
        assertEquals(0, f.manga.contentCalls)
        assertEquals(DownloadTaskStatus.WAITING_FOR_WIFI, f.manager.downloadProgress.value[task().taskKey]?.status)
        f.connection.value = DownloadConnection(true, true, "test")
        runCurrent()
        assertEquals(1, f.manga.contentCalls)
        assertNotNull(f.records.getChapter("chapter"))
    }

    @Test fun resumeKeepsPublishedPagesAndRetriesOnlyTheInterruptedPage() = runTest {
        val f = Fixture(backgroundScope)
        val urls = listOf("https://test.invalid/first.jpg", "https://test.invalid/second.jpg")
        f.manga.imageUrls = urls
        f.images.gateAfterCalls = 1
        f.images.gate = CompletableDeferred()
        runCurrent(); f.manager.enqueue(task()); runCurrent()
        f.manager.pauseDownload(task().taskKey); runCurrent()
        assertNull(f.records.getChapter("chapter"))
        assertEquals(urls, f.images.requestedUrls)
        f.images.gate = null
        f.manager.resumeDownload(task().taskKey); runCurrent()
        assertEquals(urls + urls[1], f.images.requestedUrls)
        assertTrue(f.records.hasCompleteFiles(assertNotNull(f.records.getChapter("chapter"))))
    }

    @Test fun restartReusesReceiptsFromThePreviousDownloadProcess() = runTest {
        val first = Fixture(backgroundScope)
        val urls = listOf("https://test.invalid/first.jpg", "https://test.invalid/second.jpg")
        first.manga.imageUrls = urls
        first.images.gateAfterCalls = 1
        first.images.gate = CompletableDeferred()
        runCurrent(); first.manager.enqueue(task()); runCurrent()
        first.manager.pauseDownload(task().taskKey); runCurrent()
        val restored = Fixture(backgroundScope, first.documents, first.files)
        restored.manga.imageUrls = urls
        runCurrent(); restored.manager.awaitRestoration()
        restored.manager.resumeDownload(task().taskKey); runCurrent()
        assertEquals(listOf(urls[1]), restored.images.requestedUrls)
        assertTrue(restored.records.hasCompleteFiles(assertNotNull(restored.records.getChapter("chapter"))))
    }

    @Test fun wifiWaitAndManualPauseKeepTheCompletedPageProgress() = runTest {
        val f = Fixture(backgroundScope)
        val urls = listOf("https://test.invalid/first.jpg", "https://test.invalid/second.jpg")
        f.manga.imageUrls = urls
        f.images.gateAfterCalls = 1
        f.images.gate = CompletableDeferred()
        runCurrent(); f.manager.enqueue(task()); runCurrent()
        assertEquals(0.5f, f.manager.downloadProgress.value[task().taskKey]?.overallProgress)
        f.connection.value = DownloadConnection(false, false)
        runCurrent()
        val waiting = assertNotNull(f.manager.downloadProgress.value[task().taskKey])
        assertEquals(DownloadTaskStatus.WAITING_FOR_WIFI, waiting.status)
        assertEquals(0.5f, waiting.overallProgress)
        assertEquals(1, waiting.downloadedImages)
        assertEquals(0L, waiting.speedBytesPerSecond)
        f.manager.pauseDownload(task().taskKey); runCurrent()
        assertEquals(0.5f, f.manager.downloadProgress.value[task().taskKey]?.overallProgress)
        f.connection.value = DownloadConnection(true, true, "new-wifi")
        runCurrent()
        assertEquals(DownloadTaskStatus.PAUSED, f.manager.downloadProgress.value[task().taskKey]?.status)
        f.images.gate = null
        f.manager.resumeDownload(task().taskKey); runCurrent()
        assertEquals(urls + urls[1], f.images.requestedUrls)
    }

    @Test fun receiptRejectsUnknownSourceTruncatedFilesAndUncommittedTransfers() = runTest {
        val files = Files()
        val receipts = DownloadImageReceipts(files)
        val path = "downloads/chapter/000.jpg"
        val url = "https://test.invalid/page.jpg?signature=private"
        files.write(path, byteArrayOf(1, 2, 3))
        assertFalse(receipts.isComplete(path, url))
        receipts.record(path, url)
        assertTrue(receipts.isComplete(path, url))
        assertFalse(files.rows.getValue("$path.complete").decodeToString().contains("private"))
        assertFalse(receipts.isComplete(path, "$url-changed"))
        files.write(path, byteArrayOf(1))
        assertFalse(receipts.isComplete(path, url))
        files.remove(path)
        assertFalse(receipts.isComplete(path, url))
    }

    @Test fun riskFailurePausesWithoutAutomaticRetry() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent()
        f.manga.failure = RiskControlException("captcha")
        f.manager.enqueue(task()); runCurrent()
        assertEquals(1, f.manga.contentCalls)
        assertEquals(0, f.images.calls)
        assertEquals(DownloadTaskStatus.PAUSED_BY_RISK, f.manager.downloadProgress.value[task().taskKey]?.status)
        runCurrent()
        assertEquals(1, f.manga.contentCalls)
    }
    @Test fun restartRestoresPausedAndRequiresExplicitResume() = runTest {
        val documents = Documents()
        DownloadQueueStore(documents).save(task())
        val f = Fixture(backgroundScope, documents)
        runCurrent(); f.manager.awaitRestoration()
        assertEquals(0, f.manga.contentCalls)
        assertEquals(DownloadTaskStatus.PAUSED, f.manager.downloadProgress.value[task().taskKey]?.status)
        f.connection.value = DownloadConnection(false, false)
        runCurrent()
        f.connection.value = DownloadConnection(true, true, "new-network")
        runCurrent()
        assertEquals(0, f.manga.contentCalls)
        f.manager.resumeDownload(task().taskKey)
        runCurrent()
        assertEquals(1, f.manga.contentCalls)
        assertTrue(f.manager.tasks.value.isEmpty())
    }

    @Test fun cancellationWaitsForIoAndPreventsLateMetadataCommit() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        f.images.gate = gate
        f.manager.enqueue(task()); runCurrent()
        val cancellation = backgroundScope.launch { f.manager.cancelDownload(task().taskKey) }
        runCurrent()
        assertTrue(cancellation.isCompleted)
        assertEquals(1, f.images.cancelled)
        gate.complete(Unit)
        runCurrent()
        assertNull(f.records.getChapter("chapter"))
        assertTrue(f.queue.load().isEmpty())
        assertTrue(f.manager.tasks.value.isEmpty())
    }

    @Test fun duplicateQueueClickDoesNotRepeatAuthorization() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        f.images.gate = gate
        f.manager.enqueue(task()); runCurrent()
        f.manager.enqueue(task()); runCurrent()
        assertEquals(1, f.manga.contentCalls)
        assertEquals(1, f.images.calls)
        gate.complete(Unit); runCurrent()
        assertEquals(1, f.manga.contentCalls)
        assertNotNull(f.records.getChapter("chapter"))
    }

    @Test fun retiredClientDrainWaitsForCurrentImageWithoutPausingQueue() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent(); f.images.gate = CompletableDeferred()
        f.manager.enqueue(task()); runCurrent()
        val drained = async { f.manager.awaitActiveImageRequests() }
        runCurrent(); assertFalse(drained.isCompleted)
        f.images.gate!!.complete(Unit); runCurrent()
        drained.await()
        assertEquals(0, f.images.cancelled)
        assertNotNull(f.records.getChapter("chapter"))
    }

    @Test fun retiredClientDrainAlsoWaitsForCancelledRequestCleanup() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent(); f.images.gate = CompletableDeferred(); f.images.cleanupGate = CompletableDeferred()
        f.manager.enqueue(task()); runCurrent()
        f.manager.pauseDownload(task().taskKey); runCurrent()
        val drained = async { f.manager.awaitActiveImageRequests() }
        runCurrent(); assertFalse(drained.isCompleted)
        f.images.cleanupGate!!.complete(Unit); runCurrent()
        drained.await()
        assertEquals(1, f.images.cancelled)
        assertNull(f.records.getChapter("chapter"))
    }


    @Test fun authorizationWaitsForForegroundAndFailurePreservesPausedQueue() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent()
        f.foregroundGate = CompletableDeferred()
        f.manager.enqueue(task()); runCurrent()
        assertEquals(0, f.manga.contentCalls)
        f.foregroundGate!!.completeExceptionally(IllegalStateException("service rejected"))
        runCurrent()
        assertEquals(0, f.images.calls)
        assertEquals(DownloadTaskStatus.PAUSED, f.manager.downloadProgress.value[task().taskKey]?.status)
        assertEquals(1, f.queue.load().size)
        f.foregroundGate = null
        f.manager.resumeDownload(task().taskKey); runCurrent()
        assertNotNull(f.records.getChapter("chapter"))
    }
    @Test fun changingBindingCancelsPreviousImageBeforeResuming() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent(); f.images.gate = CompletableDeferred()
        f.manager.enqueue(task()); runCurrent()
        f.connection.value = DownloadConnection(true, true, "replacement")
        runCurrent()
        assertTrue(f.images.cancelled > 0)
        assertNull(f.records.getChapter("chapter"))
        f.images.gate!!.complete(Unit); runCurrent()
        assertNotNull(f.records.getChapter("chapter"))
    }
}
