package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.ImageLoader
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
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import com.rchdliang.copymanga_neo.multiplatform.app.*
import com.rchdliang.copymanga_neo.multiplatform.ui.comments.CommentsViewModel
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalUiPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Files
import kotlin.test.*

/** 完整 ReaderScreen + 评论组件 + ReaderViewModel；仅假仓库和临时数据，禁止外网。 */
@OptIn(ExperimentalTestApi::class)
class ReaderCommentBoundaryUiTest {
    @Test fun throttledInlineCommentsDoNotReloadWhenReturningToTheSpread() =
        runSkikoComposeUiTest(size = Size(1100f, 734f)) {
            val fixture = Fixture("RIGHT_TO_LEFT", missingNext = true, failComments = true)
            try {
                mount(fixture)
                waitUntil(timeoutMillis = 10_000) { fixture.success() != null }
                swipeForward("RIGHT_TO_LEFT")
                waitUntil(timeoutMillis = 5_000) {
                    fixture.comments.inline("2").uiState.value.feed.error != null
                }
                repeat(3) {
                    onNodeWithTag("reader").performTouchInput {
                        swipe(Offset(800f, 350f), Offset(300f, 350f), 300)
                    }
                    waitForIdle()
                    onNodeWithText("14–15/15").assertIsDisplayed()
                    swipeForward("RIGHT_TO_LEFT")
                    onNodeWithText("Request was throttled", substring = true).assertIsDisplayed()
                }
                assertEquals(1, fixture.commentCalls.count { it == "2" })
                assertEquals(0, fixture.unexpectedHttpCalls)
            } finally { runOnUiThread { fixture.close() } }
        }

    @Test fun missingNextMetadataKeepsCommentsAtTheBoundaryWithoutInventingRequests() =
        runSkikoComposeUiTest(size = Size(1100f, 734f)) {
            val fixture = Fixture("RIGHT_TO_LEFT", missingNext = true)
            try {
                mount(fixture)
                waitUntil(timeoutMillis = 10_000) { fixture.success() != null }
                swipeForward("RIGHT_TO_LEFT")
                waitUntil(timeoutMillis = 5_000) { fixture.comments.inline("2").uiState.value.target?.id == "2" }
                swipeForward("RIGHT_TO_LEFT")
                assertEquals("2", fixture.success()?.chapterUuid)
                assertNull(fixture.success()?.content?.nextUuid)
                assertEquals(0, fixture.chapterCalls.count { it == "3" })
                assertEquals(1, fixture.commentCalls.count { it == "2" })
                assertEquals(0, fixture.unexpectedHttpCalls)
            } finally { runOnUiThread { fixture.close() } }
        }

    @Test fun decodedWidePageBeforeCommentsStillPrefetchesAndCrosses() =
        runSkikoComposeUiTest(size = Size(1100f, 734f)) {
            val fixture = Fixture("RIGHT_TO_LEFT", actualImages = true)
            try {
                mount(fixture, initialPage = 5)
                waitUntil(timeoutMillis = 10_000) { fixture.success() != null }
                waitForIdle()
                onNodeWithText("6–7/15").assertIsDisplayed()
                for (label in listOf("8–9/15", "10/15", "11–12/15", "13–14/15", "15/15")) {
                    swipeForward("RIGHT_TO_LEFT")
                    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
                    onNodeWithText(label).assertIsDisplayed()
                }
                waitUntil(timeoutMillis = 5_000) { fixture.success()?.chapters?.any { it.uuid == "3" } == true }
                swipeForward("RIGHT_TO_LEFT")
                waitUntil(timeoutMillis = 5_000) { fixture.comments.inline("2").uiState.value.target?.id == "2" }
                swipeForward("RIGHT_TO_LEFT")
                waitUntil(timeoutMillis = 5_000) { fixture.success()?.chapterUuid == "3" }
                onNodeWithText("1/15").assertIsDisplayed()
                assertEquals(1, fixture.chapterCalls.count { it == "3" })
                assertEquals(0, fixture.unexpectedHttpCalls)
            } finally { runOnUiThread { fixture.close() } }
        }

    @Test fun rtlForwardSwipeCrossesRealInlineCommentsWithOneChapterRequest() =
        commentsThenNext("RIGHT_TO_LEFT")

    @Test fun ltrForwardSwipeCrossesRealInlineCommentsWithOneChapterRequest() =
        commentsThenNext("LEFT_TO_RIGHT")

    private fun commentsThenNext(direction: String) = runSkikoComposeUiTest(size = Size(1100f, 734f)) {
        val fixture = Fixture(direction)
        try {
            mount(fixture)
            waitUntil(timeoutMillis = 10_000) { fixture.success()?.chapters?.any { it.uuid == "3" } == true }
            waitForIdle()
            assertEquals("2", fixture.success()?.chapterUuid)
            swipeForward(direction)
            waitUntil(timeoutMillis = 5_000) { fixture.comments.inline("2").uiState.value.target?.id == "2" }
            waitForIdle()
            onAllNodesWithText("Chapter 2").onFirst().assertIsDisplayed()
            swipeForward(direction)
            waitUntil(timeoutMillis = 5_000) { fixture.success()?.chapterUuid == "3" }
            waitForIdle()
            onNodeWithText("1/15").assertIsDisplayed()
            assertEquals(1, fixture.chapterCalls.count { it == "3" })
            assertEquals(1, fixture.commentCalls.count { it == "2" })
            assertEquals(0, fixture.unexpectedHttpCalls)
        } finally { runOnUiThread { fixture.close() } }
    }

    @Test fun throttledPrefetchDoesNotRepeatOnCommentSwipesAndExplicitRecoveryCanCross() =
        runSkikoComposeUiTest(size = Size(1100f, 734f)) {
            val fixture = Fixture("RIGHT_TO_LEFT", failNext = true)
            try {
                mount(fixture)
                waitUntil(timeoutMillis = 10_000) { fixture.chapterCalls.count { it == "3" } == 1 }
                waitForIdle()
                swipeForward("RIGHT_TO_LEFT")
                waitUntil(timeoutMillis = 5_000) { fixture.comments.inline("2").uiState.value.target?.id == "2" }
                repeat(3) { swipeForward("RIGHT_TO_LEFT") }
                assertEquals("2", fixture.success()?.chapterUuid)
                assertEquals("3", fixture.success()?.content?.nextUuid)
                assertEquals(1, fixture.chapterCalls.count { it == "3" })
                assertEquals(1, fixture.commentCalls.count { it == "2" })
                // 假仓库模拟冷却结束；只接受明确手动重试，不由滑动解除限流保护。
                runOnUiThread { fixture.failNext = false; fixture.reader.appendNextChapter(retry = true) }
                waitUntil(timeoutMillis = 5_000) { fixture.success()?.chapters?.any { it.uuid == "3" } == true }
                waitForIdle()
                swipeForward("RIGHT_TO_LEFT")
                waitUntil(timeoutMillis = 5_000) { fixture.success()?.chapterUuid == "3" }
                onNodeWithText("1/15").assertIsDisplayed()
                assertEquals(2, fixture.chapterCalls.count { it == "3" })
                assertEquals(0, fixture.unexpectedHttpCalls)
            } finally { runOnUiThread { fixture.close() } }
        }

    private fun ComposeUiTest.mount(fixture: Fixture, initialPage: Int = 14) {
        setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides fixture.owner,
                LocalUiPlatform provides fixture.uiPlatform,
                // 仅图像使用确定性预览；评论面板和所有翻页/预取逻辑仍为生产组件。
                LocalInspectionMode provides !fixture.actualImages
            ) {
                CopyManga_NeoTheme {
                    Box(Modifier.fillMaxSize().testTag("reader")) {
                        ReaderScreen("2", "fixture", "Chapter 2", onNavigateBack = {},
                            viewModel = fixture.reader, services = fixture.services, initialPage = initialPage)
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.swipeForward(direction: String) {
        // 与平板已执行手势相同的相对起终点和时长，排除靠更猛的手势掩盖问题。
        onNodeWithTag("reader").performTouchInput {
            val start = if (direction == "RIGHT_TO_LEFT") 900f / 3048 else 2200f / 3048
            val end = if (direction == "RIGHT_TO_LEFT") 2200f / 3048 else 900f / 3048
            swipe(Offset(width * start, height * .5f), Offset(width * end, height * .5f), 450)
        }
        waitForIdle()
    }

    private class Fixture(direction: String, var failNext: Boolean = false, val actualImages: Boolean = false,
                          val missingNext: Boolean = false, val failComments: Boolean = false) {
        private val root = Files.createTempDirectory("reader-comment-boundary-")
        private val images = (0..14).map { page ->
            if (!actualImages) "fixture-$page" else {
                val wide = page == 9
                val bitmap = java.awt.image.BufferedImage(if (wide) 360 else 180, if (wide) 180 else 320,
                    java.awt.image.BufferedImage.TYPE_INT_RGB)
                bitmap.createGraphics().apply {
                    color = java.awt.Color(50 + page * 10, 120, 160)
                    fillRect(0, 0, bitmap.width, bitmap.height); dispose()
                }
                root.resolve("page-$page.png").toFile().also {
                    javax.imageio.ImageIO.write(bitmap, "png", it)
                }.path
            }
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
        val uiPlatform = object : UiPlatform {
            override val backDispatcher = UiBackDispatcher()
            override suspend fun shareText(title: String, text: String) = Result.success(Unit)
            override fun formatDate(epochMilliseconds: Long) = "2026-10-24"
        }
        var unexpectedHttpCalls = 0
        val chapterCalls = mutableListOf<String>()
        val commentCalls = mutableListOf<String>()
        private val graph = runBlocking { PortableAppGraph.create(PortableAppServices(
            documents = platformDocumentStorage(root.resolve("settings").toString()),
            cache = platformCacheStorage(root.resolve("cache").toString()),
            secureCredentials = MemorySecureCredentialStorage(), deviceName = "test", deviceModel = "test",
            createHttpClient = { _, _ -> HttpClient(MockEngine {
                unexpectedHttpCalls++
                error("Unexpected HTTP request in offline reader fixture")
            }) },
            proxyPolicy = PortableProxyPolicy.EngineDefault,
            downloadServices = { scope, settings, client, _, _ ->
                val monitor = object : DownloadNetworkMonitor {
                    override val connections = MutableStateFlow(DownloadConnection(false, false))
                }
                AppDownloadServices(object : DownloadRuntime { override fun onWorkAvailable() = Unit },
                    DownloadNetworkPolicy(settings, monitor, scope), object : DownloadClientProvider {
                        override fun client(policy: DownloadNetworkState): HttpClient = error("No downloads in fixture")
                    })
            },
            comicExportIo = FileComicExportIo(root.resolve("downloads").toString()),
            readerImageIo = FileReaderImageIo(root.resolve("exports").toString()),
            imageContext = PlatformContext.INSTANCE
        ), scope) }
        private val r = graph.repositories
        private val manga = object : MangaRepository by r.manga {
            override suspend fun getChapterContent(comicPathWord: String, chapterUuid: String, group: String): Result<ChapterContent> {
                chapterCalls += chapterUuid
                if (chapterUuid == "3" && failNext) return Result.failure(RiskControlException("Request was throttled"))
                val n = chapterUuid.toInt()
                return Result.success(ChapterContent(images,
                    if (n > 1) (n - 1).toString() else null,
                    if (n < 3 && !(missingNext && n == 2)) (n + 1).toString() else null, "Chapter $n"))
            }
            override suspend fun getComicDetail(pathWord: String): Result<ComicDetail> =
                Result.failure(IllegalStateException("Offline fixture: no posting permission"))
        }
        private val repository = object : CommentRepository by r.comments {
            override suspend fun getComments(target: CommentTarget, offset: Int, replyId: String?): Result<CommentPage> {
                commentCalls += target.id
                if (failComments) return Result.failure(RiskControlException("Request was throttled"))
                return Result.success(CommentPage(emptyList(), 0, null))
            }
            override suspend fun getDownloadedChapterComments(uuid: String) =
                Result.success<DownloadedChapterComments?>(null)
        }
        val reader = ReaderViewModel(manga, r.readerPreferences, r.downloads, r.readingProgress,
            r.readerTools, r.localReading, r.sessions, r.shelfReading)
        val comments = ReaderCommentModels(repository, r.users, manga, r.downloads, r.sessions)
        private val actions = ReaderImageActionsViewModel(r.images, SavedStateHandle())
        private val modal = CommentsViewModel(repository, r.users, manga, r.downloads)
        private val models = ViewModelStore().apply {
            put("reader", reader); put("comments", comments); put("actions", actions); put("modal", modal)
        }
        private val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        val services = ReaderUiServices(object : ReaderPlatform {
            @Composable override fun ImmersiveEffect(orientation: ScreenOrientationSetting?) = Unit
            @Composable override fun KeepScreenOnEffect(enabled: Boolean) = Unit
            override suspend fun chooseImageDestination(image: ReaderExportImage) = Result.success<String?>(null)
            override suspend fun shareImage(image: ReaderExportImage) = Result.success(Unit)
        }, { loader }, actions, comments, { modal })
        init {
            runBlocking {
                r.readerTools.updateSettings { it.copy(continuousChapters = true, inlineComments = true, preloadPages = 2) }.getOrThrow()
                r.readerPreferences.updateComic("fixture") { it.copy(pageLayout = "AUTO", readingDirection = direction) }.getOrThrow()
            }
        }
        fun success() = reader.uiState.value as? ReaderUiState.Success
        fun close() {
            models.clear(); owner.destroy(); loader.shutdown(); graph.close(); scope.cancel()
            val path = root.toAbsolutePath().normalize()
            val temp = java.nio.file.Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()
            check(path.startsWith(temp) && path.fileName.toString().startsWith("reader-comment-boundary-"))
            path.toFile().deleteRecursively()
        }
    }
}
