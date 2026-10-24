package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.multiplatform.app.RouteLifecycleOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** 真实阅读器组合与布局，假章节/图片；严禁网络复现限流。 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class ReaderChapterTransitionUiTest {
    @OptIn(ExperimentalTestApi::class)
    @Test fun appendedChapterDuringAutoSpreadAnimationDoesNotJumpBack() = runSkikoComposeUiTest(size = Size(1100f, 700f)) {
        val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
        fun chapter(n: Int) = ReaderSessionChapter("$n", "default", ChapterContent(
            (0..4).map { "fixture-$n-$it" }, null, if (n == 2) "3" else null, "Chapter $n"))
        var chapters by mutableStateOf(listOf(chapter(2)))
        val pending = mutableStateOf<Int?>(2)
        val reports = mutableListOf<Pair<String, Int>>()
        var appendRequested = false
        var requests = 0
        try {
            setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalInspectionMode provides true) {
                CopyManga_NeoTheme {
                    ReaderSuccessContent(images = chapters.first().content.imageUrls, chapterName = "Fixture",
                        readingDirection = "RIGHT_TO_LEFT", pageScale = "FIT_SCREEN", screenOrientation = "SYSTEM_DEFAULT",
                        onPrevChapter = {}, onNextChapter = {}, hasPrevChapter = false, hasNextChapter = true,
                        onRefresh = {}, onReadingDirectionChange = {}, onPageScaleChange = {}, onScreenOrientationChange = {},
                        pendingInitialPage = pending, sessionChapters = chapters, activeChapterUuid = "2",
                        tools = ReaderToolsSettings(pageLayout = "AUTO", continuousChapters = true, inlineComments = false, preloadPages = 3),
                        modifier = Modifier.testTag("reader"),
                        onPosition = { id, page, _, _ -> reports += id to page },
                        onAppendNext = { requests++; appendRequested = true })
                }
            } }
            waitForIdle();assertEquals("2" to 2, reports.last())
            mainClock.autoAdvance = false
            onNodeWithTag("reader").performTouchInput { swipe(Offset(300f, 350f), Offset(950f, 350f), durationMillis = 200) }
            assertTrue(appendRequested, "Expected one pending prefetch before the user turns the page")
            mainClock.advanceTimeBy(16)
            runOnUiThread { chapters = chapters + chapter(3) }
            mainClock.advanceTimeBy(600);waitForIdle()
            assertEquals("2" to 3, reports.last(), "Appending must preserve the user's in-flight forward page, not restore its old anchor: $reports")
            assertEquals(1, requests)
        } finally { owner.destroy() }
    }


    @OptIn(ExperimentalTestApi::class)
    @Test fun autoLandscapeCrossesChaptersInBothDirectionsWithoutRequestLoop() = runSkikoComposeUiTest(size = Size(1100f, 700f)) {
        val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
        fun chapter(n: Int) = ReaderSessionChapter("$n", "default", ChapterContent(
            (0..4).map { "fixture-$n-$it" }, (n - 1).takeIf { it > 0 }?.toString(),
            (n + 1).takeIf { it < 8 }?.toString(), "Chapter $n"))
        var chapters by mutableStateOf(listOf(chapter(2)))
        var active by mutableStateOf("2")
        val pending = mutableStateOf<Int?>(4)
        val reports = mutableListOf<Pair<String, Int>>()
        val requests = mutableListOf<String>()
        var attemptedPrevious: String? = null
        var attemptedNext: String? = null
        fun adjacent(previous: Boolean) {
            if (chapters.size >= ReaderWindowPolicy.MAX_RESIDENT_CHAPTERS) return
            val boundary = if (previous) chapters.first() else chapters.last()
            val id = (if (previous) boundary.content.prevUuid else boundary.content.nextUuid) ?: return
            if (chapters.any { it.uuid == id } || id == if (previous) attemptedPrevious else attemptedNext) return
            if (previous) attemptedPrevious = id else attemptedNext = id
            requests += id
            check(requests.size < 30) { "Cross-chapter request loop: $requests; positions=$reports" }
            chapters = if (previous) listOf(chapter(id.toInt())) + chapters else chapters + chapter(id.toInt())
        }
        try {
            setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalInspectionMode provides true) {
                CopyManga_NeoTheme {
                    ReaderSuccessContent(images = chapters.first().content.imageUrls, chapterName = "Fixture",
                        readingDirection = "RIGHT_TO_LEFT", pageScale = "FIT_SCREEN", screenOrientation = "SYSTEM_DEFAULT",
                        onPrevChapter = {}, onNextChapter = {}, hasPrevChapter = true, hasNextChapter = true,
                        onRefresh = {}, onReadingDirectionChange = {}, onPageScaleChange = {}, onScreenOrientationChange = {},
                        pendingInitialPage = pending, sessionChapters = chapters, activeChapterUuid = active,
                        tools = ReaderToolsSettings(pageLayout = "AUTO", continuousChapters = true, inlineComments = false, preloadPages = 2),
                        modifier = Modifier.testTag("reader"),
                        onPosition = { id, page, _, _ -> reports += id to page },
                        onActivateChapter = { active = it },
                        onWindowSettled = { id ->
                            if (id == active) {
                                val kept = ReaderWindowPolicy.around(chapters, id) { it.uuid }
                                if (kept != chapters) {
                                    if (kept.first().uuid != chapters.first().uuid) attemptedPrevious = null
                                    if (kept.last().uuid != chapters.last().uuid) attemptedNext = null
                                    chapters = kept
                                }
                            }
                        },
                        onAppendNext = { adjacent(false) }, onPrependPrevious = { adjacent(true) })
                }
            } }
            waitForIdle()
            assertEquals("2" to 4, reports.last())
            val forward = listOf("3" to 0, "3" to 1, "3" to 3, "4" to 0, "4" to 1, "4" to 3, "5" to 0)
            fun assertVisiblePage(expected: Pair<String, Int>) {
                onNodeWithText("Chapter "+expected.first).assertIsDisplayed()
                onNodeWithText(when(expected.second) { 0 -> "1/5"; 1 -> "2–3/5"; else -> "4–5/5" }).assertIsDisplayed()
            }
            for (expected in forward) {
                onNodeWithTag("reader").performTouchInput { swipe(Offset(300f, 350f), Offset(950f, 350f), 200) }
                waitForIdle()
                assertEquals(expected, reports.last(), "Forward position drift: $reports; requests=$requests")
                assertVisiblePage(expected)
                if (expected in listOf("3" to 1, "4" to 0)) capture("chapter-${expected.first}-page-${expected.second+1}")
            }
            assertTrue(requests.groupingBy { it }.eachCount().values.all { it == 1 }, "Repeated forward prefetch: $requests")
            val backward = listOf("4" to 3, "4" to 1, "4" to 0, "3" to 3)
            for (expected in backward) {
                onNodeWithTag("reader").performTouchInput { swipe(Offset(900f, 350f), Offset(250f, 350f), 200) }
                waitForIdle()
                assertEquals(expected, reports.last(), "Backward position drift: $reports; requests=$requests")
                assertVisiblePage(expected)
            }
            assertTrue(requests.count { it == "3" } <= 2, "Evicted chapter must reload only once: $requests")
        } finally { owner.destroy() }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.capture(name: String) {
        val pixels = onRoot().captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x,y,pixels[x,y].toArgb())
        val file = File("build/reports/reader-navigation/$name.png")
        file.parentFile.mkdirs(); ImageIO.write(image,"png",file)
    }

    @Test fun autoLandscapePrependAppendDoesNotActivateTransientChapter() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
        fun chapter(n: Int) = ReaderSessionChapter("$n", "default", ChapterContent(
            (0..4).map { "fixture-$n-$it" }, (n - 1).takeIf { it > 0 }?.toString(),
            (n + 1).takeIf { it < 6 }?.toString(), "Chapter $n"))
        var chapters by mutableStateOf(listOf(chapter(2)))
        var active by mutableStateOf("2")
        val pending = mutableStateOf<Int?>(4)
        val reports = mutableListOf<Pair<String, Int>>()
        val activations = mutableListOf<String>()
        val requests = mutableListOf<String>()
        var attemptedPrevious: String? = null
        var attemptedNext: String? = null
        fun adjacent(previous: Boolean) {
            val boundary = if (previous) chapters.first() else chapters.last()
            val id = (if (previous) boundary.content.prevUuid else boundary.content.nextUuid) ?: return
            if (chapters.any { it.uuid == id } || id == if (previous) attemptedPrevious else attemptedNext) return
            if (previous) attemptedPrevious = id else attemptedNext = id
            requests += id
            check(requests.size < 20) { "Unexpected cross-chapter request loop: $requests; reports=$reports" }
            chapters = if (previous) listOf(chapter(id.toInt())) + chapters else chapters + chapter(id.toInt())
        }
        var scene: ImageComposeScene? = null
        try {
            val raster = ImageComposeScene(1200, 800, coroutineContext = coroutineContext) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalInspectionMode provides true) {
                    CopyManga_NeoTheme { Box(Modifier.size(1100.dp, 700.dp)) {
                        ReaderSuccessContent(images = chapters.first().content.imageUrls, chapterName = "Fixture",
                            readingDirection = "RIGHT_TO_LEFT", pageScale = "FIT_SCREEN", screenOrientation = "SYSTEM_DEFAULT",
                            onPrevChapter = {}, onNextChapter = {}, hasPrevChapter = true, hasNextChapter = true,
                            onRefresh = {}, onReadingDirectionChange = {}, onPageScaleChange = {}, onScreenOrientationChange = {},
                            pendingInitialPage = pending, sessionChapters = chapters, activeChapterUuid = active,
                            tools = ReaderToolsSettings(pageLayout = "AUTO", continuousChapters = true, inlineComments = false),
                            onPosition = { id, page, _, _ -> reports += id to page },
                            onActivateChapter = { id -> active = id; activations += id },
                            onWindowSettled = { id ->
                                if (id == active) {
                                    val keep = ReaderWindowPolicy.around(chapters, id) { it.uuid }
                                    if (keep != chapters) { chapters = keep; attemptedPrevious = null; attemptedNext = null }
                                }
                            }, onAppendNext = { adjacent(false) }, onPrependPrevious = { adjacent(true) })
                    } }
                }
            }
            scene = raster
            var time = 0L
            fun settle() { repeat(16) { runCurrent(); time += 16_000_000; raster.render(time).close(); runCurrent() } }
            settle()
            assertEquals("2" to 4, reports.last())
            // 上一章到达会改变所有 raw/spread 下标，但当前真实页仍必须留在第 2 章。
            adjacent(true); settle()
            assertEquals("2" to 4, reports.last())
            assertTrue(activations.all { it == "2" }, "Transient chapter was activated: $activations")
            assertTrue(requests.groupingBy { it }.eachCount().values.all { it == 1 }, "Duplicate chapter requests: $requests")
        } finally { scene?.close(); owner.destroy(); Dispatchers.resetMain() }
    }
}
