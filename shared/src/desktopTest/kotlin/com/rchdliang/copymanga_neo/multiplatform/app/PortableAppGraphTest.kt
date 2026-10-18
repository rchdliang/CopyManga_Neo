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
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.multiplatform.ui.downloads.DownloadsViewModel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PortableAppGraphTest {
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
                assertTrue(graph.startupWarnings.any { it.contains("下载队列无法恢复") })
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
                    assertTrue(graph.startupWarnings.any { it.contains(if (key == "download-records") "下载记录" else "导出任务") })
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

}
