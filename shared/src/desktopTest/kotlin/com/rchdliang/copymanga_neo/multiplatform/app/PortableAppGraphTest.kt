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
    @Test fun activeHomeCanRequestOffMainBeforeUiCollectionButInactiveHomeWaitsForStart() = runTest {
        val root = Files.createTempDirectory("copymanga-home-start-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val calls = AtomicInteger()
        val requested = kotlinx.coroutines.channels.Channel<Long>(2)
        val uiThread = Thread.currentThread().threadId()
        val manga = object : MangaRepository by graph.repositories.manga {
            override suspend fun getHomeComics(): Result<HomeComicsData> {
                calls.incrementAndGet(); requested.send(Thread.currentThread().threadId())
                return Result.failure(IllegalStateException("offline"))
            }
        }
        val inactive = com.rchdliang.copymanga_neo.multiplatform.ui.home.HomeViewModel(
            manga, graph.repositories.discovery, graph.repositories.risk, graph.repositories.appSettings, autoStart = false)
        var active: com.rchdliang.copymanga_neo.multiplatform.ui.home.HomeViewModel? = null
        try {
            runCurrent(); assertEquals(0, calls.get())
            inactive.start(); inactive.start(); runCurrent()
            assertNotEquals(uiThread, requested.receive())
            assertEquals(1, calls.get())
            active = com.rchdliang.copymanga_neo.multiplatform.ui.home.HomeViewModel(
                manga, graph.repositories.discovery, graph.repositories.risk, graph.repositories.appSettings, autoStart = true)
            runCurrent()
            assertNotEquals(uiThread, requested.receive())
            assertEquals(2, calls.get())
        } finally {
            inactive.viewModelScope.cancel(); active?.viewModelScope?.cancel(); requested.close()
            graph.shutdown(); Dispatchers.resetMain(); root.toFile().deleteRecursively()
        }
    }

    @Test fun sessionKeepsCachedIdentityButDefersNetworkAndStartsOnlyOnceOffMain() = runTest {
        val root = Files.createTempDirectory("copymanga-session-start-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val calls = AtomicInteger()
        val requested = CompletableDeferred<Long>()
        val uiThread = Thread.currentThread().threadId()
        val users = object : com.rchdliang.copymanga_neo.domain.repository.UserRepository by graph.repositories.users {
            override fun isLoggedInSync() = true
            override fun getAvatar() = kotlinx.coroutines.flow.flowOf("cached-avatar")
            override suspend fun getMemberInfo(): Result<MemberInfo> {
                calls.incrementAndGet()
                requested.complete(Thread.currentThread().threadId())
                return Result.failure(IllegalStateException("offline"))
            }
        }
        val model = MainSessionModel(users)
        try {
            runCurrent()
            assertTrue(model.isLoggedIn.value)
            assertEquals("cached-avatar", model.avatar.value)
            assertEquals(0, calls.get())
            model.start(); model.start(); runCurrent()
            assertNotEquals(uiThread, requested.await())
            assertEquals(1, calls.get())
        } finally {
            model.viewModelScope.cancel(); graph.shutdown(); Dispatchers.resetMain()
            root.toFile().deleteRecursively()
        }
    }

    @Test fun expiryBeforeDeferredSessionStartCannotLaunchDuplicateAutoLogins() = runTest {
        val root = Files.createTempDirectory("copymanga-session-expiry-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val loggedIn = MutableStateFlow(true)
        val calls = AtomicInteger()
        val requested = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val users = object : com.rchdliang.copymanga_neo.domain.repository.UserRepository by graph.repositories.users {
            override fun isLoggedInSync() = loggedIn.value
            override fun getAutoLoginSync() = true
            override fun getSavedUsernameSync() = "test-user"
            override fun getSavedPasswordSync() = "test-password"
            override suspend fun login(username: String, rawPassword: String, expectedGeneration: Long?): Result<LoginInfo> {
                calls.incrementAndGet(); requested.complete(Unit); release.await()
                return Result.failure(IllegalStateException("offline"))
            }
        }
        val model = MainSessionModel(users)
        try {
            loggedIn.value = false
            model.start(); runCurrent(); requested.await()
            assertEquals(1, calls.get())
        } finally {
            release.complete(Unit); model.viewModelScope.cancel(); graph.shutdown(); Dispatchers.resetMain()
            root.toFile().deleteRecursively()
        }
    }

    @Test fun deferredFavoritesPreserveCachedLoginAndDoNotFetchBeforeStart() = runTest {
        val root = Files.createTempDirectory("copymanga-favorites-start-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val calls = AtomicInteger()
        val requested = CompletableDeferred<Unit>()
        val novelOffsets = mutableListOf<Int>()
        graph.repositories.appSettings.setFavoritesSort(FavoritesSort.UPDATED).getOrThrow()
        val novelSorts = mutableListOf<FavoritesSort>()
        val comicSorts = java.util.Collections.synchronizedList(mutableListOf<FavoritesSort>())
        var failSecondPage = true
        val oldPageWaiting = CompletableDeferred<Unit>()
        val releaseOldPage = CompletableDeferred<Unit>()
        var delayOldPage = false
        val readingSession = graph.repositories.sessions.getReadingSessionSync()!!
        val localRecords = MutableStateFlow(LocalReadingSnapshot(records = listOf(
            LocalReadingRecord(readingSession.owner!!, "novel", "novel-1", "old", "Old", group = readingSession.mode.name, updatedAt = 1),
            LocalReadingRecord(readingSession.owner!!, "novel", "novel-2", "old", "Old", group = readingSession.mode.name, updatedAt = 1),
            LocalReadingRecord("different-owner", "novel", "novel-1", "latest", "Latest", group = readingSession.mode.name, updatedAt = 2),
            LocalReadingRecord(readingSession.owner!!, "novel", "novel-1", "latest", "Latest", group = RequestMode.WEB.name, updatedAt = 3)
        )))
        val localReading = object : com.rchdliang.copymanga_neo.domain.repository.LocalReadingRepository by graph.repositories.localReading {
            override val snapshot = localRecords
        }
        val novelShelf = object : com.rchdliang.copymanga_neo.domain.repository.NovelShelfRepository by graph.repositories.novelShelf {
            override suspend fun list(offset: Int, sort: FavoritesSort): Result<NovelShelfPage> {
                novelOffsets += offset
                novelSorts += sort
                if (delayOldPage && sort == FavoritesSort.UPDATED && offset == 1) {
                    oldPageWaiting.complete(Unit)
                    // 模拟底层请求忽略取消，仍然返回旧排序的分页。
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { releaseOldPage.await() }
                }
                if (offset == 1 && failSecondPage) return Result.failure(Exception("offline"))
                // The shelf API uses last_chapter_id for the latest novel volume too.
                val json = kotlinx.serialization.json.Json.parseToJsonElement("""
                    {"offset":$offset,"total":3,"limit":1,"list":[{"uuid":${offset + 1},"last_browse":null,
                    "book":{"path_word":"novel-${offset + 1}","name":"Novel","last_chapter_id":"latest","last_chapter_name":"Latest"}}]}
                """) as kotlinx.serialization.json.JsonObject
                return Result.success(com.rchdliang.copymanga_neo.data.novel.portable.PortableNovelJson.shelfPage(json, offset))
            }
        }
        val users = object : com.rchdliang.copymanga_neo.domain.repository.UserRepository by graph.repositories.users {
            override fun isLoggedInSync() = true
        }
        val manga = object : MangaRepository by graph.repositories.manga {
            override suspend fun getFavoriteComics(sort: FavoritesSort): Result<List<FavoriteItem>> {
                comicSorts += sort
                calls.incrementAndGet(); requested.complete(Unit)
                return Result.success(listOf(FavoriteItem(1, null, null, null, FavoriteComic(
                    "comic", true, "Comic", "comic", null, null, null, 0, null, null,
                    "latest", "Latest", BrowseInfo("comic", "comic", "old", "Old")
                ))))
            }
        }
        val model = com.rchdliang.copymanga_neo.multiplatform.ui.favorites.FavoritesViewModel(
            manga, users, graph.repositories.readingProgress, novelShelf,
            graph.repositories.appSettings, localReading, graph.repositories.sessions, graph.repositories.shelfReading, autoStart = false)
        try {
            runCurrent()
            assertTrue(model.uiState.value.isLoggedIn)
            assertTrue(model.uiState.value.isLoading)
            assertEquals(0, calls.get())
            assertTrue(novelOffsets.isEmpty())
            model.start(); model.start(); runCurrent(); requested.await()
            model.uiState.first { !it.isLoading }
            assertEquals(1, calls.get())
            runCurrent()
            assertEquals(listOf(0, 1), novelOffsets)
            assertEquals(0, model.uiState.value.updatedNovelCount)
            assertEquals(0, model.uiState.value.updatedComicCount)
            assertEquals(0, model.uiState.value.updatedCount)
            assertNotNull(model.uiState.value.novelLoadMoreError)
            failSecondPage = false
            model.retryMoreNovels(); runCurrent()
            assertEquals(listOf(0, 1, 1, 2), novelOffsets)
            assertEquals(3, model.uiState.value.novels.size)
            assertEquals(0, model.uiState.value.updatedNovelCount) // Legacy positions do not prove a past catch-up.
            assertEquals("Latest", model.uiState.value.novelReadingProgress["novel-1"]?.name)
            assertEquals("old", model.uiState.value.novelReadingProgress["novel-2"]?.chapter)
            assertFalse(model.uiState.value.novelReadingProgress.containsKey("novel-3"))
            assertEquals(0, model.uiState.value.updatedCount)
            assertFalse(model.uiState.value.novelsHaveMore)
            assertEquals(com.rchdliang.copymanga_neo.multiplatform.ui.favorites.FavoritesKind.COMIC, model.uiState.value.selectedKind)
            localRecords.value = localRecords.value.copy(records = localRecords.value.records +
                LocalReadingRecord(readingSession.owner!!, "novel", "novel-1", "latest", "Latest", group = readingSession.mode.name, updatedAt = 4))
            runCurrent()
            assertEquals(0, model.uiState.value.updatedNovelCount)
            assertTrue(model.uiState.value.novelReadingStates.isEmpty())
            assertEquals("Latest", model.uiState.value.novelReadingProgress["novel-1"]?.name)
            model.selectKind(com.rchdliang.copymanga_neo.multiplatform.ui.favorites.FavoritesKind.NOVEL)
            runCurrent()
            assertTrue(graph.repositories.appSettings.getFavoritesNovelSelected().getOrThrow())
            assertEquals(listOf(FavoritesSort.UPDATED), comicSorts.toList())
            assertTrue(novelSorts.all { it == FavoritesSort.UPDATED })
            model.enterSelectionMode(1)
            model.selectSort(FavoritesSort.READ)
            runCurrent()
            model.uiState.first { !it.isLoading && it.favorites.isNotEmpty() }
            assertEquals(FavoritesSort.READ, model.uiState.value.sort)
            assertFalse(model.uiState.value.isSelectionMode)
            assertTrue(model.uiState.value.selectedIds.isEmpty())
            assertEquals(listOf(0, 1, 1, 2, 0, 1, 2), novelOffsets)
            assertEquals(List(3) { FavoritesSort.READ }, novelSorts.takeLast(3))
            assertEquals(listOf(FavoritesSort.UPDATED, FavoritesSort.READ), comicSorts.toList())
            assertEquals(FavoritesSort.READ, graph.repositories.appSettings.getFavoritesSort().getOrThrow())
            delayOldPage = true
            model.selectSort(FavoritesSort.UPDATED)
            runCurrent()
            oldPageWaiting.await()
            model.selectSort(FavoritesSort.ADDED)
            runCurrent()
            model.uiState.first { !it.isLoading && it.favorites.isNotEmpty() }
            val requestsBeforeOldResponse = novelOffsets.size
            releaseOldPage.complete(Unit)
            runCurrent()
            assertEquals(requestsBeforeOldResponse, novelOffsets.size)
            assertEquals(List(3) { FavoritesSort.ADDED }, novelSorts.takeLast(3))
            assertEquals(FavoritesSort.ADDED, model.uiState.value.sort)
            assertEquals(3, model.uiState.value.novels.size)
            assertFalse(model.uiState.value.novelsHaveMore)
        } finally {
            releaseOldPage.complete(Unit)
            model.viewModelScope.cancel(); graph.shutdown(); Dispatchers.resetMain()
            root.toFile().deleteRecursively()
        }
    }

    private fun deleteNovelPreviewTemp(root: Path) {
        val target = root.toAbsolutePath().normalize()
        val temporary = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()
        check(target.startsWith(temporary) && target != temporary && target.fileName.toString().startsWith("novel-preview-"))
        target.toFile().deleteRecursively()
    }
    @Test fun novelPreviewLoadsOnDemandCachesAndRetries() = runTest {
        val root = Files.createTempDirectory("novel-preview-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val seed = NovelBook("novel", "Novel", "", emptyList())
        var calls = 0
        var fail = false
        val repo = object : com.rchdliang.copymanga_neo.domain.repository.NovelRepository by graph.repositories.novels {
            override suspend fun detail(mode: RequestMode, owner: String, path: String): Result<NovelBook> {
                calls++
                return if (fail) Result.failure(Exception("offline"))
                else Result.success(seed.copy(path = path, brief = "Preview", themes = listOf(NovelTag("Novel tag", "tag"))))
            }
        }
        val vm = com.rchdliang.copymanga_neo.multiplatform.ui.components.NovelPreviewViewModel(
            repo, graph.repositories.sessions, graph.repositories.network)
        try {
            runCurrent()
            assertEquals(0, calls)
            vm.show(seed); runCurrent()
            assertEquals("Preview", vm.state.value.book?.brief)
            vm.show(null); vm.show(seed); runCurrent()
            assertEquals(1, calls)
            vm.show(seed.copy(path = "ready", brief = "Already loaded")); runCurrent()
            assertEquals(1, calls)
            fail = true
            vm.show(seed.copy(path = "failed")); runCurrent()
            assertEquals("简介加载失败，请重试", vm.state.value.error)
            fail = false
            vm.retry(); runCurrent()
            assertNull(vm.state.value.error)
            assertEquals("Preview", vm.state.value.book?.brief)
            assertEquals(3, calls)
        } finally { vm.viewModelScope.cancel(); graph.close(); Dispatchers.resetMain(); deleteNovelPreviewTemp(root) }
    }

    @Test fun novelPreviewRejectsLateResultAfterSessionAndModeChange() = runTest {
        val root = Files.createTempDirectory("novel-preview-session-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val seed = NovelBook("novel", "Novel", "", emptyList())
        val revision = MutableStateFlow(0L)
        var current = LocalReadingSession(RequestMode.WEB, "owner-a", 0)
        val networkState = MutableStateFlow(graph.repositories.network.settings.value)
        val sessions = object : ReadingSessionRepository by graph.repositories.sessions {
            override val sessionChanges = revision
            override fun getReadingSessionSync() = current
        }
        val network = object : com.rchdliang.copymanga_neo.domain.repository.NetworkSettingsRepository by graph.repositories.network {
            override val settings = networkState
        }
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<Pair<RequestMode, String>>()
        val repo = object : com.rchdliang.copymanga_neo.domain.repository.NovelRepository by graph.repositories.novels {
            override suspend fun detail(mode: RequestMode, owner: String, path: String): Result<NovelBook> {
                calls += mode to owner
                if (owner == "owner-a") kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { release.await() }
                return Result.success(seed.copy(brief = owner))
            }
        }
        val vm = com.rchdliang.copymanga_neo.multiplatform.ui.components.NovelPreviewViewModel(repo, sessions, network)
        try {
            runCurrent()
            vm.show(seed); runCurrent()
            current = LocalReadingSession(RequestMode.OFFICIAL_APP, "owner-b", 1)
            networkState.value = networkState.value.copy(requestMode = RequestMode.OFFICIAL_APP)
            revision.value++
            runCurrent()
            assertEquals(seed, vm.state.value.book)
            assertTrue(vm.state.value.epoch > 0)
            vm.show(seed); runCurrent()
            release.complete(Unit); runCurrent()
            assertEquals("owner-b", vm.state.value.book?.brief)
            assertEquals(listOf(RequestMode.WEB to "owner-a", RequestMode.OFFICIAL_APP to "owner-b"), calls)
            current = LocalReadingSession(RequestMode.WEB, "owner-c", 2)
            revision.value++; runCurrent()
            vm.show(seed); runCurrent()
            assertEquals("owner-b", vm.state.value.book?.brief) // Public metadata is reused; no old request is replayed.
            assertEquals(2, calls.size)
        } finally { release.complete(Unit); vm.viewModelScope.cancel(); graph.close(); Dispatchers.resetMain(); deleteNovelPreviewTemp(root) }
    }

    @Test fun novelPreviewDismissalCancelsAndDoesNotCacheLateResult() = runTest {
        val root = Files.createTempDirectory("novel-preview-dismiss-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val seed = NovelBook("novel", "Novel", "", emptyList())
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val repo = object : com.rchdliang.copymanga_neo.domain.repository.NovelRepository by graph.repositories.novels {
            override suspend fun detail(mode: RequestMode, owner: String, path: String): Result<NovelBook> {
                val number = ++calls
                if (number == 1) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { release.await() }
                return Result.success(seed.copy(brief = "Preview $number"))
            }
        }
        val vm = com.rchdliang.copymanga_neo.multiplatform.ui.components.NovelPreviewViewModel(
            repo, graph.repositories.sessions, graph.repositories.network)
        try {
            runCurrent()
            vm.show(seed); runCurrent()
            vm.show(null)
            release.complete(Unit); runCurrent()
            assertFalse(vm.state.value.loading)
            assertEquals("", vm.state.value.book?.brief)
            vm.show(seed); runCurrent()
            assertEquals(2, calls)
            assertEquals("Preview 2", vm.state.value.book?.brief)
        } finally { release.complete(Unit); vm.viewModelScope.cancel(); graph.close(); Dispatchers.resetMain(); deleteNovelPreviewTemp(root) }
    }
    @Test fun homePaginationDoesNotWaitForMetadata() = runTest {
        val root = Files.createTempDirectory("home-filter-progress-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val r = graph.repositories
        val allowed = listOf(Theme("Allowed", "allowed"))
        fun comic(id: String, themes: List<Theme>?) = ComicSummary(id, id, null, null, null, null, null, themes, null)
        val manga = object : MangaRepository by r.manga {
            override val cachedComicThemes = MutableStateFlow<Map<String, CachedComicThemes>>(emptyMap())
            override suspend fun getHomeComics() = Result.success(HomeComicsData(
                recComics = listOf(comic("ready", allowed)), rankComics = listOf(comic("slow", null)), recNextOffset = 1))
            override suspend fun refreshComicThemes(pathWord: String): Result<List<Theme>> = kotlinx.coroutines.awaitCancellation()
        }
        var requests = 0
        val discovery = object : com.rchdliang.copymanga_neo.domain.repository.ComicDiscoveryRepository by r.discovery {
            override suspend fun getRecommendations(offset: Int, limit: Int): Result<ComicPage> {
                requests++
                return Result.success(ComicPage(listOf(comic("more", allowed)), null))
            }
        }
        val model = com.rchdliang.copymanga_neo.multiplatform.ui.home.HomeViewModel(manga, discovery, r.risk, r.appSettings)
        try {
            val ready = model.uiState.first { com.rchdliang.copymanga_neo.multiplatform.ui.home.HomeShelf.RECOMMENDED in it.readyShelves }
            assertTrue(ready.filterReady)
            model.loadMore(com.rchdliang.copymanga_neo.multiplatform.ui.home.HomeShelf.RECOMMENDED, automatic = true)
            model.uiState.first { it.recComicList.size == 2 }
            assertEquals(1, requests)
        } finally { model.viewModelScope.cancel(); graph.close(); Dispatchers.resetMain(); root.toFile().deleteRecursively() }
    }
    @Test fun cloudHistoryReaderResolvesTheChapterGroupBeforeLoadingContent() = runTest {
        val root = Files.createTempDirectory("reader-cloud-group-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val r = graph.repositories
        val requests = mutableListOf<String>()
        val manga = object : MangaRepository by r.manga {
            override suspend fun getChapterInfo(comicPathWord: String, chapterUuid: String, group: String): Result<ChapterInfo> {
                requests += "resolve:$chapterUuid"
                return Result.success(ChapterInfo(0, chapterUuid, 1, 0, null, "24话", "book", comicPathWord,
                    null, "other_honyakuchimu", 0, null, null, null, null))
            }
            override suspend fun getChapterContent(comicPathWord: String, chapterUuid: String, group: String): Result<ChapterContent> {
                requests += "content:$group"
                return Result.success(ChapterContent(listOf("image"), null, null, "24话"))
            }
        }
        val model = ReaderViewModel(manga, r.readerPreferences, r.downloads, r.readingProgress, r.readerTools, r.localReading, r.sessions, r.shelfReading)
        try {
            model.loadChapter("book", "chapter24", "")
            val result = model.uiState.first { it is ReaderUiState.Success || it is ReaderUiState.Error }
            val ready = assertIs<ReaderUiState.Success>(result)
            assertEquals("other_honyakuchimu", ready.chapters.single().group)
            assertEquals(listOf("resolve:chapter24", "content:other_honyakuchimu"), requests)
        } finally { model.viewModelScope.cancel(); graph.close(); Dispatchers.resetMain(); root.toFile().deleteRecursively() }
    }

    @Test fun shelfCatalogUsesGlobalLatestChapterEvenWhenAnotherGroupAppearsLast() = runTest {
        val root = Files.createTempDirectory("shelf-global-latest-")
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        fun chapter(id: String, group: String) = ChapterInfo(0, id, 1, 0, null, id, "book", "book",
            null, group, 0, null, null, null, null)
        val groups = listOf(ChapterGroup("default", 1, "默认"), ChapterGroup("other", 1, "其它汉化版"))
        val chapters = linkedMapOf("default" to listOf(chapter("25", "default")), "other" to listOf(chapter("24", "other")))
        val display = DisplayValue("", 0)
        val comic = ComicInfo("book", false, false, 0, "漫画", null, "book", false, false,
            display, display, display, null, null, null, display, display, null, null, null, null, null,
            null, LastChapter("25", "第25话"), null, null)
        var fallback = false
        val pagedGroups = mutableListOf<String>()
        val manga = object : MangaRepository by graph.repositories.manga {
            override suspend fun getChapterCatalog(pathWord: String, forceRefresh: Boolean): Result<ChapterCatalog> {
                assertTrue(forceRefresh)
                return if (fallback) Result.failure(Exception("catalog unavailable")) else Result.success(ChapterCatalog(groups, chapters))
            }
            override suspend fun getComicDetail(pathWord: String) = Result.success(
                ComicDetail(comic, groups.associateBy { it.pathWord }, false, false, true, false, false))
            override suspend fun getChapters(pathWord: String, group: String, offset: Int, limit: Int): Result<ChapterList> {
                pagedGroups += group
                return Result.success(ChapterList(chapters.getValue(group), 1))
            }
        }
        try {
            val key = ShelfReadingKey("owner", RequestMode.WEB, "comic", "book", "other")
            suspend fun catalog() = com.rchdliang.copymanga_neo.data.local.portable.loadShelfReadingCatalog(
                key, manga, graph.repositories.novels).getOrThrow()
            val complete = catalog()
            assertEquals("25", complete.single { it.latest }.id)
            assertEquals("24", complete.last().id)
            assertEquals(setOf("default", "other"), complete.map { it.group }.toSet())
            fallback = true
            assertEquals(complete, catalog())
            assertEquals(listOf("default", "other"), pagedGroups)
        } finally { graph.close(); root.toFile().deleteRecursively() }
    }

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
        val model = ReaderViewModel(manga, r.readerPreferences, r.downloads, r.readingProgress, r.readerTools, r.localReading, r.sessions, r.shelfReading)
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
        val model = ReaderViewModel(manga, r.readerPreferences, r.downloads, r.readingProgress, r.readerTools, r.localReading, r.sessions, r.shelfReading)
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

    @Test fun downloadListFailureCanBeRetriedWithoutDiscardingReadableRecords() = runTest {
        val root = Files.createTempDirectory("copymanga-download-retry-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        var fail = true
        var subscriptions = 0
        val book = DownloadedComicInfo("book", "Offline book", null, "Author", "", null, null, null, 1, 1, 1)
        val repository = object : com.rchdliang.copymanga_neo.domain.repository.DownloadRepository by graph.repositories.downloads {
            override suspend fun awaitReady() = Result.success(Unit)
            override fun getAllDownloadedComics() = kotlinx.coroutines.flow.flow {
                subscriptions++
                if (fail) error("private storage path")
                emit(listOf(book))
            }
        }
        val model = DownloadsViewModel(repository, graph.repositories.novels, graph.repositories.sessions)
        try {
            model.uiState.first { !it.isLoading }
            assertNotNull(model.uiState.value.loadError)
            assertFalse(model.uiState.value.isLoading)
            fail = false; model.reload(); model.uiState.first { !it.isLoading }
            assertNull(model.uiState.value.loadError)
            assertEquals(1, model.uiState.value.downloadedBooks.size)
            fail = true; model.reload(); model.uiState.first { !it.isLoading }
            assertNotNull(model.uiState.value.loadError)
            assertEquals(1, model.uiState.value.downloadedBooks.size)
            assertEquals(3, subscriptions)
        } finally { model.viewModelScope.cancel(); graph.close(); Dispatchers.resetMain(); root.toFile().deleteRecursively() }
    }


    @Test fun failedAdjacentDoesNotRetryWhenOtherEdgeIsTrimmed() = runTest {
        val root = Files.createTempDirectory("reader-failed-edge-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val r = graph.repositories
        val requests = mutableListOf<String>()
        var fail = true
        val manga = object : MangaRepository by r.manga {
            override suspend fun getChapterContent(comicPathWord: String, chapterUuid: String, group: String): Result<ChapterContent> {
                requests += chapterUuid
                if (chapterUuid == "4" && fail) return Result.failure(IllegalStateException("offline"))
                val n = chapterUuid.toInt()
                return Result.success(ChapterContent(listOf("image-$n"), (n - 1).toString(), (n + 1).toString()))
            }
        }
        val model = ReaderViewModel(manga, r.readerPreferences, r.downloads, r.readingProgress, r.readerTools, r.localReading, r.sessions, r.shelfReading)
        suspend fun contains(id: String) { model.uiState.first { it is ReaderUiState.Success && it.chapters.any { c -> c.uuid == id } } }
        try {
            r.readerTools.updateSettings { it.copy(continuousChapters = true) }.getOrThrow()
            model.loadChapter("book", "1"); contains("1")
            model.appendNextChapter(); contains("2")
            model.appendNextChapter(); contains("3")
            model.appendNextChapter()
            model.toastMessage.first { it != null }
            model.activateChapter("3"); model.retainWindow("3")
            repeat(20) { model.appendNextChapter(); runCurrent() }
            assertEquals(1, requests.count { it == "4" })
            assertEquals(listOf("2", "3"), assertIs<ReaderUiState.Success>(model.uiState.value).chapters.map { it.uuid })
            fail = false
            model.appendNextChapter(retry = true); contains("4")
            assertEquals(2, requests.count { it == "4" })
        } finally { model.viewModelScope.cancel(); graph.close(); Dispatchers.resetMain(); root.toFile().deleteRecursively() }
    }

    @Test fun throttledAdjacentPausesBothAutomaticEdgesAndKeepsCurrentChapterRetryable() = runTest {
        val root = Files.createTempDirectory("reader-throttled-edge-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val r = graph.repositories
        val requests = mutableListOf<String>()
        var throttled = true
        val manga = object : MangaRepository by r.manga {
            override suspend fun getChapterContent(comicPathWord: String, chapterUuid: String, group: String): Result<ChapterContent> {
                requests += chapterUuid
                if (chapterUuid == "3" && throttled) return Result.failure(
                    com.rchdliang.copymanga_neo.domain.repository.RiskControlException("Request was throttled"))
                val n = chapterUuid.toInt()
                return Result.success(ChapterContent(listOf("image-$n"), (n - 1).toString(), (n + 1).toString()))
            }
        }
        val model = ReaderViewModel(manga, r.readerPreferences, r.downloads, r.readingProgress, r.readerTools, r.localReading, r.sessions, r.shelfReading)
        try {
            r.readerTools.updateSettings { it.copy(continuousChapters = true) }.getOrThrow()
            model.loadChapter("book", "2")
            model.uiState.first { it is ReaderUiState.Success }
            model.appendNextChapter()
            model.toastMessage.first { it?.contains("210") == true }
            repeat(20) { model.appendNextChapter(); model.prependPreviousChapter(); model.retainWindow("2"); runCurrent() }
            assertEquals(listOf("2", "3"), requests)
            val current = assertIs<ReaderUiState.Success>(model.uiState.value)
            assertEquals("2", current.chapterUuid)
            assertEquals("3", current.content.nextUuid)
            assertEquals("1", current.content.prevUuid)
            assertTrue(model.toastMessage.value!!.contains("暂停自动加载"))
            // 模拟冷却后由用户手动重试；不修改真实网络风控设置。
            throttled = false
            model.appendNextChapter(retry = true)
            model.uiState.first { it is ReaderUiState.Success && it.chapters.any { c -> c.uuid == "3" } }
            assertEquals(2, requests.count { it == "3" })
        } finally { model.viewModelScope.cancel(); graph.close(); Dispatchers.resetMain(); root.toFile().deleteRecursively() }
    }

    @Test fun bothAdjacentRequestsDeduplicateAndLateResponsesCannotChangeExplicitJump() = runTest {
        val root = Files.createTempDirectory("reader-bidirectional-race-")
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val graph = PortableAppGraph.create(services(root), backgroundScope)
        val r = graph.repositories
        val requests = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val bothStarted = CompletableDeferred<Unit>()
        val manga = object : MangaRepository by r.manga {
            override suspend fun getChapterContent(comicPathWord: String, chapterUuid: String, group: String): Result<ChapterContent> {
                requests += chapterUuid
                if (chapterUuid in setOf("1", "3")) {
                    if (requests.containsAll(listOf("1", "3"))) bothStarted.complete(Unit)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { release.await() }
                }
                val n = chapterUuid.toInt()
                return Result.success(ChapterContent(listOf("image-$n"), (n - 1).toString(), (n + 1).toString()))
            }
        }
        val model = ReaderViewModel(manga, r.readerPreferences, r.downloads, r.readingProgress, r.readerTools, r.localReading, r.sessions, r.shelfReading)
        try {
            r.readerTools.updateSettings { it.copy(continuousChapters = true) }.getOrThrow()
            model.loadChapter("book", "2"); model.uiState.first { it is ReaderUiState.Success }
            repeat(20) { model.appendNextChapter(); model.prependPreviousChapter() }
            bothStarted.await()
            assertEquals(1, requests.count { it == "1" }); assertEquals(1, requests.count { it == "3" })
            model.loadChapter("book", "7")
            model.uiState.first { it is ReaderUiState.Success && it.chapterUuid == "7" }
            release.complete(Unit); advanceUntilIdle()
            assertEquals(listOf("7"), assertIs<ReaderUiState.Success>(model.uiState.value).chapters.map { it.uuid })
        } finally { release.complete(Unit); model.viewModelScope.cancel(); graph.close(); Dispatchers.resetMain(); root.toFile().deleteRecursively() }
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

    @Test fun deferredPreferencesDoNotBlockHomeAndFirstConsumersWaitForSavedValues() = runTest {
        val root = Files.createTempDirectory("copymanga-deferred-preferences-")
        val gate = CompletableDeferred<Unit>()
        val keys = listOf("comic-reader-settings.json", "reader-tools.json", "novel-reader-settings.json", "novel-annotations.json", "network-log-settings.json")
        val entered = keys.associateWith { CompletableDeferred<Unit>() }
        val loads = keys.associateWith { AtomicInteger() }
        try {
            val original = services(root)
            com.rchdliang.copymanga_neo.data.local.portable.PortableReaderPreferences(original.documents)
                .updateGlobal { it.copy(pageLayout = "DOUBLE") }.getOrThrow()
            com.rchdliang.copymanga_neo.data.local.portable.PortableReaderTools(original.documents)
                .updateSettings { it.copy(continuousChapters = true) }.getOrThrow()
            com.rchdliang.copymanga_neo.data.local.portable.PortableNovelPreferences(original.documents)
                .update(NovelReaderPreferences(fontSize = 24f)).getOrThrow()
            val annotation = NovelAnnotation("id", "owner", "WEB", "book", "volume", "卷",
                "文本", NovelTextRange(0, 0, 0, 2), createdAt = 1)
            com.rchdliang.copymanga_neo.data.local.portable.PortableNovelAnnotations(original.documents)
                .save(annotation).getOrThrow()
            val delayed = object : DocumentStorage by original.documents {
                override suspend fun read(key: String): String? {
                    entered[key]?.let { signal ->
                        loads.getValue(key).incrementAndGet()
                        signal.complete(Unit)
                        gate.await()
                    }
                    return original.documents.read(key)
                }
            }
            val graph = PortableAppGraph.create(original.copy(documents = delayed), backgroundScope)
            try {
                assertTrue(entered.values.none { it.isCompleted })
                // 不经过首页预热也必须恢复已保存值，不能先发默认值；并发预热不能重复读取。
                val comic = async { graph.repositories.readerPreferences.globalPreferences.first() }
                val tools = async { graph.repositories.readerTools.awaitReady() }
                val novel = async { graph.repositories.novelPreferences.settings.first() }
                val notes = async { graph.repositories.annotations.annotations.first() }
                keys.take(4).forEach { entered.getValue(it).await() }
                assertFalse(comic.isCompleted); assertFalse(tools.isCompleted)
                assertFalse(novel.isCompleted); assertFalse(notes.isCompleted)
                graph.repositories.startBackground()
                gate.complete(Unit)
                assertEquals("DOUBLE", comic.await().pageLayout)
                assertTrue(tools.await().isSuccess)
                assertTrue(graph.repositories.readerTools.settings.value.continuousChapters)
                assertEquals(24f, novel.await().fontSize)
                assertEquals(listOf(annotation), notes.await())
                entered.getValue("network-log-settings.json").await()
                keys.take(4).forEach { assertEquals(1, loads.getValue(it).get(), it) }
            } finally { gate.complete(Unit); graph.shutdown() }
        } finally { gate.complete(Unit); root.toFile().deleteRecursively() }
    }

    @Test fun savedThemeIsPublishedBeforeOtherCoreStorageFinishes() = runTest {
        val root = Files.createTempDirectory("copymanga-startup-theme-")
        val gate = CompletableDeferred<Unit>()
        try {
            val original = services(root)
            com.rchdliang.copymanga_neo.data.local.portable.PortableAppSettings(original.documents)
                .setThemeMode(AppThemeMode.DARK).getOrThrow()
            val theme = CompletableDeferred<AppSettings>()
            val delayed = object : DocumentStorage by original.documents {
                override suspend fun read(key: String): String? {
                    if (key == "reading-library.json") gate.await()
                    return original.documents.read(key)
                }
            }
            val creating = async {
                PortableAppGraph.create(original.copy(documents = delayed, onAppSettingsReady = { theme.complete(it) }), backgroundScope)
            }
            try {
                assertEquals(AppThemeMode.DARK, theme.await().themeMode)
                assertFalse(creating.isCompleted)
            } finally { gate.complete(Unit); creating.await().shutdown() }
        } finally { gate.complete(Unit); root.toFile().deleteRecursively() }
    }

    @Test fun corruptDeferredAnnotationsWarnWithoutBlockingHomeOrOverwritingData() = runTest {
        val root = Files.createTempDirectory("copymanga-deferred-corrupt-")
        try {
            val original = services(root)
            original.documents.write("novel-annotations.json", "{broken")
            val graph = PortableAppGraph.create(original, backgroundScope)
            try {
                graph.repositories.startBackground()
                graph.warnings.first { it.any { warning -> warning.contains("小说标注") } }
                assertFailsWith<Exception> { graph.repositories.annotations.annotations.first() }
                assertEquals("{broken", original.documents.read("novel-annotations.json"))
                assertTrue(graph.repositories.appSettings.setThemeMode(AppThemeMode.DARK).isSuccess)
            } finally { graph.shutdown() }
        } finally { root.toFile().deleteRecursively() }
    }

}
