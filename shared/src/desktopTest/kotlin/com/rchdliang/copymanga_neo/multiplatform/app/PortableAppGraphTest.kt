package com.rchdliang.copymanga_neo.multiplatform.app

import coil3.PlatformContext
import com.rchdliang.copymanga_neo.data.local.portable.platformDocumentStorage
import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.data.repository.portable.MemorySecureCredentialStorage
import com.rchdliang.copymanga_neo.data.download.portable.*
import com.rchdliang.copymanga_neo.data.export.portable.FileComicExportIo
import com.rchdliang.copymanga_neo.data.image.portable.FileReaderImageIo
import com.rchdliang.copymanga_neo.data.storage.platformCacheStorage
import com.rchdliang.copymanga_neo.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.concurrent.atomic.AtomicInteger
import com.rchdliang.copymanga_neo.data.storage.CacheStorage
import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelStore
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.*
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingSessionRepository
import com.rchdliang.copymanga_neo.multiplatform.ui.downloads.DownloadsViewModel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PortableAppGraphTest {
    @Test fun continuousReaderReleasesOldChaptersAndReloadsInBothDirections() = runTest {
        val root = Files.createTempDirectory("reader-window-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val r = graph.repositories
        val requests = mutableListOf<String>()
        val manga = object : MangaRepository by r.manga {
            override suspend fun getChapterContent(comicPathWord: String, chapterUuid: String, group: String): Result<ChapterContent> {
                requests += chapterUuid
                val number = chapterUuid.toInt()
                return Result.success(ChapterContent(listOf("image-$number"), (number - 1).takeIf { it >= 0 }?.toString(),
                    (number + 1).takeIf { it <= 100 }?.toString(), chapterUuid))
            }
        }
        val model = ReaderViewModel(manga, r.readerPreferences, r.downloads, r.readingProgress, r.readerTools, r.localReading, r.sessions)
        fun state() = assertIs<ReaderUiState.Success>(model.uiState.value)
        suspend fun awaitChapter(id: String) {
            model.uiState.first { it is ReaderUiState.Success && it.chapters.any { chapter -> chapter.uuid == id } }
        }
        try {
            r.readerTools.updateSettings { it.copy(continuousChapters = true) }.getOrThrow()
            model.loadChapter("book", "0", "default")
            awaitChapter("0")
            for (i in 1..4) {
                model.appendNextChapter()
                awaitChapter(i.toString())
            }
            model.appendNextChapter()
            runCurrent()
            assertEquals(5, state().chapters.size)
            assertEquals(1, requests.count { it == "4" })
            assertFalse("5" in requests)
            model.loadChapter("book", "0", "default")
            model.uiState.first { it is ReaderUiState.Success && it.chapters.size == 1 }
            for (i in 1..100) {
                model.appendNextChapter()
                awaitChapter(i.toString())
                assertTrue(state().chapters.size <= 5)
                model.activateChapter(i.toString())
                model.retainWindow(i.toString())
                assertEquals(i.toString(), state().chapterUuid)
                assertTrue(state().chapters.size <= 3)
            }
            for (i in 99 downTo 0) {
                if (state().chapters.none { it.uuid == i.toString() }) {
                    model.prependPreviousChapter()
                    awaitChapter(i.toString())
                }
                model.activateChapter(i.toString())
                model.retainWindow(i.toString())
                assertEquals(i.toString(), state().chapterUuid)
                assertTrue(state().chapters.size <= 3)
            }
            assertTrue(requests.count { it == "1" } >= 2)
        } finally { model.viewModelScope.cancel(); graph.close(); Dispatchers.resetMain(); root.toFile().deleteRecursively() }
    }

    @Test fun canceledAdjacentLoadCannotReinsertChapterAfterExplicitJump() = runTest {
        val root = Files.createTempDirectory("reader-window-race-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val r = graph.repositories
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val manga = object : MangaRepository by r.manga {
            override suspend fun getChapterContent(comicPathWord: String, chapterUuid: String, group: String): Result<ChapterContent> {
                if (chapterUuid == "1") {
                    started.complete(Unit)
                    // 模拟不及时响应取消的底层请求；不能将迟到结果并回新的窗口。
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { finish.await() }
                }
                val i = chapterUuid.toInt()
                return Result.success(ChapterContent(listOf("image-$i"), null, (i + 1).toString()))
            }
        }
        val model = ReaderViewModel(manga, r.readerPreferences, r.downloads, r.readingProgress, r.readerTools, r.localReading, r.sessions)
        try {
            r.readerTools.updateSettings { it.copy(continuousChapters = true) }.getOrThrow()
            model.loadChapter("book", "0", "default")
            model.uiState.first { it is ReaderUiState.Success }
            model.appendNextChapter()
            started.await()
            model.loadChapter("book", "50", "default")
            model.uiState.first { it is ReaderUiState.Success && it.chapterUuid == "50" }
            finish.complete(Unit)
            advanceUntilIdle()
            val state = assertIs<ReaderUiState.Success>(model.uiState.value)
            assertEquals(listOf("50"), state.chapters.map { it.uuid })
            assertEquals("50", state.chapterUuid)
        } finally { finish.complete(Unit); model.viewModelScope.cancel(); graph.close(); Dispatchers.resetMain(); root.toFile().deleteRecursively() }
    }

    @Test fun commentWindowCancelsEvictedModelsAndRestoresOnlyDraftText() = runTest {
        val root = Files.createTempDirectory("reader-comments-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val r = graph.repositories
        var owner = LocalReadingSession(RequestMode.WEB, "first", 1)
        val sessions = object : ReadingSessionRepository by r.sessions {
            override fun getReadingSessionSync() = owner
        }
        val pool = ReaderCommentModels(r.comments, r.users, r.manga, r.downloads, sessions)
        val store = ViewModelStore().also { it.put("pool", pool) }
        try {
            val first = pool.inline("0")
            first.open(CommentTarget(CommentKind.CHAPTER, "0"), false)
            first.setDraft("保留这段草稿")
            runCurrent()
            for (i in 1..100) {
                val ids = (maxOf(0, i - 1)..i).map(Int::toString).toSet()
                pool.chapter(i.toString())
                pool.inline(i.toString())
                pool.retain(ids)
                assertTrue(pool.residentChapterCount <= 2)
            }
            assertFalse(first.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
            val restored = pool.inline("0")
            assertNotSame(first, restored)
            restored.open(CommentTarget(CommentKind.CHAPTER, "0"), false)
            assertEquals("保留这段草稿", restored.uiState.value.draft)
            assertTrue(restored.uiState.value.feed.items.isEmpty())
            pool.retain(emptySet())
            owner = LocalReadingSession(RequestMode.WEB, "second", 2)
            val switched = pool.inline("0")
            switched.open(CommentTarget(CommentKind.CHAPTER, "0"), false)
            assertEquals("", switched.uiState.value.draft)
        } finally { store.clear(); graph.close(); Dispatchers.resetMain(); root.toFile().deleteRecursively() }
    }

    private fun services(root: Path) = PortableAppServices(
        documents = platformDocumentStorage(root.resolve("settings").toString()),
        cache = platformCacheStorage(root.resolve("cache").toString()),
        secureCredentials = MemorySecureCredentialStorage(),
        deviceName = "test", deviceModel = "test",
        createHttpClient = { _, _ -> HttpClient(MockEngine { respondError(io.ktor.http.HttpStatusCode.ServiceUnavailable) }) },
        proxyPolicy = PortableProxyPolicy.EngineDefault,
                downloadServices = { scope, settings, client, _, _ ->
            val monitor = object : DownloadNetworkMonitor {
                override val connections = MutableStateFlow(DownloadConnection(false, false))
            }
            AppDownloadServices(object : DownloadRuntime { override fun onWorkAvailable() = Unit },
                DownloadNetworkPolicy(settings, monitor, scope), object : DownloadClientProvider {
                    override fun client(policy: DownloadNetworkState): HttpClient { check(policy.allowed); return client() }
                })
        },
        comicExportIo = FileComicExportIo(root.resolve("cache/downloads").toString()),
        readerImageIo = FileReaderImageIo(root.resolve("exports").toString()),
        imageContext = PlatformContext.INSTANCE
    )

    @Test fun startupPersistenceAndRestartUseActualPortableRepositories() = runTest {
        val root = Files.createTempDirectory("copymanga-graph-")
        try {
            val first = PortableAppGraph.create(services(root), backgroundScope)
            try {
                val record = LocalReadingRecord("guest", "comic", "book", "chapter", "第一话", updatedAt = 1234,
                    title = "漫画标题", cover = "https://example.test/cover.jpg")
                first.repositories.localReading.record(record)
                first.repositories.localReading.flush().getOrThrow()
                first.repositories.localReading.toggleBookmark(record).getOrThrow()
                first.repositories.networkLog.setEnabled(true)
                assertEquals("漫画标题", first.repositories.localReading.latest("guest", "comic", "book")?.title)
            } finally { first.close() }
            val second = PortableAppGraph.create(services(root), backgroundScope)
            try {
                assertEquals("漫画标题", second.repositories.localReading.latest("guest", "comic", "book")?.title)
                assertEquals(1, second.repositories.localReading.snapshot.value.bookmarks.size)
                assertTrue(second.repositories.network.settings.value.officialApp.pseudoId.isNotBlank())
                assertTrue(second.repositories.downloads.getAllDownloadedComics().first().isEmpty())
            } finally { second.close() }
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun corruptQueuePreservesOfflineReadingAndUnrelatedSettings() = runTest {
        val root = Files.createTempDirectory("copymanga-corrupt-queue-")
        try {
            val services = services(root)
            val records = DownloadRecords(services.documents, services.cache)
            val image = "downloads/book/chapter/page.jpg"
            services.cache.write(image, byteArrayOf(1, 2, 3))
            records.insertComic(DownloadedComicInfo("book", "Offline book", null, "Author", "", null, null, null, 1, 1, 1))
            records.insertChapter(DownloadedChapterRecord("chapter", "book", "Chapter", 1, 1, listOf(image), "downloaded", 1))
            val original = "{broken queue"
            services.documents.write("download-queue", original)
            val graph = PortableAppGraph.create(services, backgroundScope)
            try {
                graph.repositories.startBackground()
                graph.warnings.first { messages -> messages.any { it.contains("下载队列无法恢复") } }
                assertEquals("Offline book", graph.repositories.downloads.getAllDownloadedComics().first().single().name)
                assertNotNull(graph.repositories.downloads.getDownloadedChapter("chapter"))
                assertTrue(graph.repositories.downloads.awaitReady().exceptionOrNull()?.message.orEmpty().contains("修复存储"))
                Dispatchers.setMain(StandardTestDispatcher(testScheduler))
                val model = DownloadsViewModel(graph.repositories.downloads, graph.repositories.novels, graph.repositories.sessions)
                try {
                    model.resumeTask("book").join()
                    assertTrue(model.uiState.value.message.orEmpty().contains("修复存储"))
                    model.clearMessage()
                    model.pauseTask("book").join()
                    assertTrue(model.uiState.value.message.orEmpty().contains("修复存储"))
                } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
                graph.repositories.appSettings.setThemeMode(AppThemeMode.DARK).getOrThrow()
                assertFailsWith<Exception> { graph.repositories.downloads.cancelAll() }
                assertEquals(original, services.documents.read("download-queue"))
                assertContentEquals(byteArrayOf(1, 2, 3), services.cache.read(image, 32))
            } finally { graph.close() }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun corruptDownloadRecordsAndExportStateDoNotBlockUnrelatedFeaturesOrResetData() = runTest {
        for (key in listOf("download-records", "comic-export-task.json")) {
            val root = Files.createTempDirectory("copymanga-corrupt-optional-")
            try {
                val services = services(root)
                val original = "{broken optional document"
                services.documents.write(key, original)
                val graph = PortableAppGraph.create(services, backgroundScope)
                try {
                    graph.repositories.startBackground()
                    graph.warnings.first { it.isNotEmpty() }
                    graph.repositories.export.awaitRestoration()
                    graph.repositories.appSettings.setThemeMode(AppThemeMode.DARK).getOrThrow()
                    assertTrue(graph.repositories.localReading.clearHistory("guest").isSuccess)
                    if (key == "download-records") {
                        val failure = graph.repositories.downloads.awaitReady().exceptionOrNull()
                        assertTrue(failure?.message.orEmpty().contains("修复存储"))
                        assertFailsWith<Exception> { graph.repositories.downloads.getDownloadedChapters("book") }
                    } else {
                        val export = graph.repositories.export
                        assertEquals(ComicExportStatus.INTERRUPTED, export.state.value.status)
                        assertTrue(export.state.value.cleanupWarning.orEmpty().contains("原记录已保留"))
                        assertTrue(export.awaitRestoration().isFailure)
                        val plan = ComicExportPlan(emptyList())
                        assertTrue(export.prepare(ComicExportRequest(listOf("book"))).isFailure)
                        assertTrue(export.saveDraft(plan).isFailure)
                        assertTrue(export.start(plan, ComicExportFormat.ZIP, "unused", false).isFailure)
                        assertTrue(export.dismiss().isFailure)
                        export.cancel()
                    }
                    assertEquals(original, services.documents.read(key))
                } finally { graph.close() }
            } finally { root.toFile().deleteRecursively() }
        }
    }

    @Test fun corruptReadingLibraryDoesNotBlockUnrelatedFeaturesOrOverwriteItsBytes() = runTest {
        val root = Files.createTempDirectory("copymanga-corrupt-library-")
        try {
            val services = services(root)
            val original = "{broken reading document"
            services.documents.write("reading-library.json", original)
            val graph = PortableAppGraph.create(services, backgroundScope)
            try {
                assertNotNull(graph.repositories.localReading.error.value)
                assertTrue(graph.startupWarnings.any { it.contains("本地阅读数据") })
                assertEquals(original, services.documents.read("reading-library.json"))
                assertTrue(graph.repositories.localReading.clearHistory("guest").isFailure)
                assertTrue(graph.repositories.backup.exportSettings().isFailure)
                graph.repositories.appSettings.setThemeMode(AppThemeMode.DARK).getOrThrow()
                assertEquals(original, services.documents.read("reading-library.json"))
                // Simulate restored valid data, then exercise the local library's explicit retry.
                services.documents.write("reading-library.json", "{}")
                graph.repositories.localReading.retryRead().getOrThrow()
                graph.repositories.localReading.record(LocalReadingRecord("guest", "comic", "book", "chapter", "Chapter"))
                graph.repositories.localReading.flush().getOrThrow()
                assertNull(graph.repositories.localReading.error.value)
                assertEquals("book", graph.repositories.localReading.snapshot.value.records.single().path)
            } finally { graph.close() }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun slowOptionalStorageDoesNotBlockCoreAndHeadlessCommandsCanStartRestoration() = runTest {
        val root = Files.createTempDirectory("copymanga-staged-start-")
        val gate = CompletableDeferred<Unit>()
        val queueEntered = CompletableDeferred<Unit>()
        val exportEntered = CompletableDeferred<Unit>()
        val readingLoads = AtomicInteger()
        val cacheScans = AtomicInteger()
        try {
            val original = services(root)
            val delayed = object : DocumentStorage by original.documents {
                override suspend fun read(key: String): String? {
                    if (key == "reading-library.json") readingLoads.incrementAndGet()
                    if (key == "download-queue") { queueEntered.complete(Unit); gate.await() }
                    if (key == "comic-export-task.json") { exportEntered.complete(Unit); gate.await() }
                    return original.documents.read(key)
                }
            }
            val cache = object : CacheStorage by original.cache {
                override suspend fun list(prefix: String): List<String> {
                    cacheScans.incrementAndGet()
                    return original.cache.list(prefix)
                }
            }
            val graph = PortableAppGraph.create(original.copy(documents = delayed, cache = cache), backgroundScope)
            try {
                assertFalse(queueEntered.isCompleted)
                assertFalse(exportEntered.isCompleted)
                assertEquals(1, readingLoads.get())
                assertEquals(0, cacheScans.get())
                assertTrue(graph.repositories.users.isAuthCacheReady().first())
                // 没有 Compose 帧或 startBackground，也能处理来自系统服务的命令。
                val downloads = async { graph.repositories.downloads.awaitReady() }
                val export = async { graph.repositories.export.awaitRestoration() }
                queueEntered.await(); exportEntered.await()
                assertFalse(downloads.isCompleted)
                assertFalse(export.isCompleted)
                graph.repositories.appSettings.setThemeMode(AppThemeMode.DARK).getOrThrow()
                gate.complete(Unit)
                assertTrue(downloads.await().isSuccess)
                assertTrue(export.await().isSuccess)
                listOf(
                    async { graph.repositories.novels.awaitCachedVolumes() },
                    async { graph.repositories.novels.awaitCachedVolumes() }
                ).awaitAll().forEach { assertTrue(it.isSuccess) }
                assertEquals(1, cacheScans.get())
                graph.repositories.novels.refreshCachedVolumes().getOrThrow()
                assertEquals(2, cacheScans.get())
            } finally { gate.complete(Unit); graph.shutdown() }
        } finally { gate.complete(Unit); root.toFile().deleteRecursively() }
    }

}
