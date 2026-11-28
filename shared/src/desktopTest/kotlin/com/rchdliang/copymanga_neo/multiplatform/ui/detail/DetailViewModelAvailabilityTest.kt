package com.rchdliang.copymanga_neo.multiplatform.ui.detail

import androidx.lifecycle.ViewModelStore
import coil3.PlatformContext
import com.rchdliang.copymanga_neo.data.download.portable.*
import com.rchdliang.copymanga_neo.data.export.portable.FileComicExportIo
import com.rchdliang.copymanga_neo.data.image.portable.FileReaderImageIo
import com.rchdliang.copymanga_neo.data.local.portable.platformDocumentStorage
import com.rchdliang.copymanga_neo.data.remote.portable.ComicUnavailableException
import com.rchdliang.copymanga_neo.data.remote.portable.PortableProxyPolicy
import com.rchdliang.copymanga_neo.data.repository.portable.MemorySecureCredentialStorage
import com.rchdliang.copymanga_neo.data.storage.platformCacheStorage
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import com.rchdliang.copymanga_neo.multiplatform.app.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import java.nio.file.Files
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelAvailabilityTest {
    @Test fun unavailableDetailsSkipTheCatalogAndOnlyOfferDownloadedChapters() = runTest {
        val root = Files.createTempDirectory("comic-detail-availability-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(PortableAppServices(
            documents = platformDocumentStorage(root.resolve("settings").toString()),
            cache = platformCacheStorage(root.resolve("cache").toString()),
            secureCredentials = MemorySecureCredentialStorage(), deviceName = "test", deviceModel = "test",
            createHttpClient = { _, _ -> HttpClient(MockEngine { error("Unexpected network request") }) },
            proxyPolicy = PortableProxyPolicy.EngineDefault,
            downloadServices = { scope, settings, _, _, _ ->
                val monitor = object : DownloadNetworkMonitor {
                    override val connections = MutableStateFlow(DownloadConnection(false, false))
                }
                AppDownloadServices(object : DownloadRuntime { override fun onWorkAvailable() = Unit },
                    DownloadNetworkPolicy(settings, monitor, scope), object : DownloadClientProvider {
                        override fun client(policy: DownloadNetworkState): HttpClient = error("Unexpected download")
                    })
            },
            comicExportIo = FileComicExportIo(root.resolve("downloads").toString()),
            readerImageIo = FileReaderImageIo(root.resolve("exports").toString()), imageContext = PlatformContext.INSTANCE,
        ), backgroundScope)
        val r = graph.repositories
        val unavailable = "該作品已下架或已合并入其他同系列作品。"
        val calls = mutableListOf<String>()
        val display = DisplayValue("", 0)
        val manga = object : MangaRepository by r.manga {
            override suspend fun getComicDetail(pathWord: String): Result<ComicDetail> {
                calls += "detail/$pathWord"
                if (pathWord != "catalog-risk") return Result.failure(ComicUnavailableException(210, unavailable))
                val comic = ComicInfo(pathWord, false, false, 0, pathWord, null, pathWord, false, false,
                    display, display, display, null, null, null, display, display, null, null, null, null, null,
                    null, null, null, null)
                return Result.success(ComicDetail(comic, emptyMap(), false, false, false, false, false))
            }
            override suspend fun getChapterCatalog(pathWord: String, forceRefresh: Boolean): Result<ChapterCatalog> {
                calls += "catalog/$pathWord"
                return Result.failure(RiskControlException("所有分组均为 0 章"))
            }
        }
        val users = object : UserRepository by r.users {
            override fun isAuthCacheReady() = flowOf(true)
            override fun isLoggedIn() = flowOf(false)
        }
        val downloads = object : DownloadRepository by r.downloads {
            override suspend fun getDownloadedComic(pathWord: String) = if (pathWord == "offline")
                DownloadedComicInfo("offline", "本地漫画", null, "", "", null, null, null, 1, 1, 1) else null
            override suspend fun getDownloadedChapters(comicPathWord: String) =
                listOf(DownloadedChapterInfo("downloaded", comicPathWord, "已下载章节", 1, 1, listOf("local.png"), "default"))
            override suspend fun getDownloadedChapterUuids(comicPathWord: String) = if (comicPathWord == "offline") setOf("downloaded") else emptySet()
        }
        val model = DetailViewModel(manga, users, downloads, r.blacklist, r.readingProgress, r.localReading)
        val store = ViewModelStore().apply { put("detail", model) }
        try {
            model.loadDetail("removed")
            runCurrent()
            assertEquals(unavailable, model.uiState.value.error)
            assertNull(model.uiState.value.riskControlReason)
            assertNull(model.uiState.value.comicDetail)
            model.refreshDetail()
            runCurrent()
            assertEquals(unavailable, model.uiState.value.error)

            model.loadDetail("offline")
            runCurrent()
            val offline = model.uiState.value
            assertTrue(offline.hasLocalCache && offline.isChapterCatalogReady)
            assertEquals(listOf("downloaded"), offline.chapters.map { it.uuid })
            assertEquals(setOf("downloaded"), offline.downloadedChapterUuids)
            assertTrue(offline.toastMessage.orEmpty().contains(unavailable))
            assertNull(offline.riskControlReason)
            assertNull(offline.error)

            model.loadDetail("catalog-risk")
            runCurrent()
            assertEquals("所有分组均为 0 章", model.uiState.value.riskControlReason)
            assertNull(model.uiState.value.error)
            assertEquals(listOf("detail/removed", "detail/removed", "detail/offline",
                "detail/catalog-risk", "catalog/catalog-risk"), calls)
        } finally {
            store.clear(); graph.close(); Dispatchers.resetMain(); root.toFile().deleteRecursively()
        }
    }
}
