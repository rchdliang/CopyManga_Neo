package com.rchdliang.copymanga_neo.multiplatform.ui.comments

import androidx.lifecycle.ViewModelStore
import coil3.PlatformContext
import com.rchdliang.copymanga_neo.data.download.portable.*
import com.rchdliang.copymanga_neo.data.export.portable.FileComicExportIo
import com.rchdliang.copymanga_neo.data.image.portable.FileReaderImageIo
import com.rchdliang.copymanga_neo.data.local.portable.platformDocumentStorage
import com.rchdliang.copymanga_neo.data.remote.portable.PortableProxyPolicy
import com.rchdliang.copymanga_neo.data.repository.portable.MemorySecureCredentialStorage
import com.rchdliang.copymanga_neo.data.storage.platformCacheStorage
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import com.rchdliang.copymanga_neo.multiplatform.app.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import java.nio.file.Files
import kotlin.test.*

/** 使用隔离应用图和禁止外网的仓库，验证离线阅读不会悄悄恢复在线请求。 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineCommentsTest {
    private class Fixture(private val scope: TestScope) {
        val root = Files.createTempDirectory("copymanga-offline-comments-")
        var transportCalls = 0
        var reads = 0
        var writes = 0
        var permissions = 0
        var identities = 0
        var downloaded = true
        val local = MangaComment("local", "读者", null, "已保存评论", "2026-11-03", userId = "self")
        var snapshot: DownloadedChapterComments? = DownloadedChapterComments(listOf(local), 10, false, savedAt = 1)
        var response: suspend () -> Result<CommentPage> = { Result.failure(IllegalStateException("offline")) }
        lateinit var graph: PortableAppGraph
        suspend fun start() {
            graph = PortableAppGraph.create(PortableAppServices(
            documents = platformDocumentStorage(root.resolve("settings").toString()),
            cache = platformCacheStorage(root.resolve("cache").toString()),
            secureCredentials = MemorySecureCredentialStorage(), deviceName = "test", deviceModel = "test",
            createHttpClient = { _, _ -> HttpClient(MockEngine { transportCalls++; respondError(io.ktor.http.HttpStatusCode.ServiceUnavailable) }) },
            proxyPolicy = PortableProxyPolicy.EngineDefault,
            downloadServices = { owner, settings, client, _, _ ->
                val monitor = object : DownloadNetworkMonitor {
                    override val connections = MutableStateFlow(DownloadConnection(false, false))
                }
                AppDownloadServices(object : DownloadRuntime { override fun onWorkAvailable() = Unit },
                    DownloadNetworkPolicy(settings, monitor, owner), object : DownloadClientProvider {
                        override fun client(policy: DownloadNetworkState): HttpClient = client()
                    })
            },
            comicExportIo = FileComicExportIo(root.resolve("downloads").toString()),
            readerImageIo = FileReaderImageIo(root.resolve("exports").toString()), imageContext = PlatformContext.INSTANCE
            ), scope.backgroundScope)
        }
        val comments by lazy { object : CommentRepository by graph.repositories.comments {
            override suspend fun hasDownloadedChapter(uuid: String) = Result.success(downloaded)
            override suspend fun getDownloadedChapterComments(uuid: String) = Result.success(snapshot)
            override suspend fun getComments(target: CommentTarget, offset: Int, replyId: String?): Result<CommentPage> {
                reads++; return response()
            }
            override suspend fun postComment(target: CommentTarget, content: String, replyId: String?): Result<Unit> {
                writes++; return Result.success(Unit)
            }
            override suspend fun getReportConfig(target: CommentTarget): Result<CommentReportConfig> {
                writes++; return Result.failure(IllegalStateException("unexpected report"))
            }
        }
        }
        val users by lazy { object : UserRepository by graph.repositories.users {
            override fun isLoggedInSync() = true
            override fun isLoggedIn() = MutableStateFlow(true)
            override suspend fun getMemberInfo(): Result<MemberInfo> {
                identities++; return Result.failure(IllegalStateException("offline"))
            }
        }
        }
        val manga by lazy { object : MangaRepository by graph.repositories.manga {
            override suspend fun getChapterCommentPostingAllowed(pathWord: String, forceRefresh: Boolean): Result<Boolean> {
                permissions++; return Result.success(true)
            }
        }
        }
        val model by lazy { CommentsViewModel(comments, users, manga) }
        val store by lazy { ViewModelStore().apply { put("comments", model) } }
        fun close() { store.clear(); graph.close(); root.toFile().deleteRecursively() }
    }

    @Test fun downloadedOpeningAndReadOnlyActionsNeverFetchNetwork() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(this)
        f.start()
        try {
            val snapshots = listOf(f.snapshot, f.snapshot!!.copy(complete = true), null,
                DownloadedChapterComments(emptyList(), 0, true, closed = true, savedAt = 1))
            snapshots.forEachIndexed { index, snapshot ->
                f.snapshot = snapshot
                f.model.open(CommentTarget(CommentKind.CHAPTER, "chapter-$index"), true, "book"); runCurrent()
                assertTrue(f.model.uiState.value.feed.localOnly)
                assertFalse(f.model.uiState.value.feed.loading)
                assertEquals(snapshot?.comments.orEmpty(), f.model.uiState.value.feed.items)
                assertNull(f.model.uiState.value.feed.error)
                f.model.setDraft("保留离线草稿")
                f.model.post(); f.model.openReport(f.local); f.model.delete(f.local)
                f.model.retryPermission(); f.model.load(refresh = false); runCurrent()
                assertFalse(f.model.canDelete(f.local))
            }
            assertEquals(0, f.reads + f.writes + f.permissions + f.identities + f.transportCalls)
        } finally { f.close(); Dispatchers.resetMain() }
    }

    @Test fun explicitRefreshKeepsSavedCommentsOnFailureAndOnlyUnlocksAfterSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(this)
        f.start()
        try {
            f.model.open(CommentTarget(CommentKind.CHAPTER, "chapter"), true, "book"); runCurrent()
            f.model.setDraft("保留草稿")
            f.model.load(refresh = true, fromPullToRefresh = true); runCurrent()
            assertEquals(1, f.reads)
            assertTrue(f.model.uiState.value.feed.localOnly)
            assertEquals(listOf(f.local), f.model.uiState.value.feed.items)
            assertFalse(f.model.uiState.value.feed.refreshing)
            assertEquals(0, f.permissions + f.identities)
            val gate = CompletableDeferred<Unit>()
            val online = f.local.copy(id = "online", content = "最新评论")
            f.response = { gate.await(); Result.success(CommentPage(listOf(online), 1, null)) }
            f.model.load(refresh = true); runCurrent()
            assertTrue(f.model.uiState.value.feed.localOnly)
            assertEquals(listOf(f.local), f.model.uiState.value.feed.items)
            gate.complete(Unit); runCurrent()
            assertFalse(f.model.uiState.value.feed.localOnly)
            assertEquals(listOf(online), f.model.uiState.value.feed.items)
            assertEquals("保留草稿", f.model.uiState.value.draft)
            assertTrue(f.model.uiState.value.postingAllowed)
            assertEquals(1, f.permissions)
            assertEquals(1, f.identities)
            assertEquals(0, f.transportCalls)
        } finally { f.close(); Dispatchers.resetMain() }
    }
}
