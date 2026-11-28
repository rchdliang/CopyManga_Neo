package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.multiplatform.app.RouteLifecycleOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.jetbrains.skia.Bitmap
import kotlin.test.*

/** Exercises the production screen's restoration/reporting; preview images avoid network/services. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ReaderSpreadPositionUiTest {
    @Test fun continuousIndicatorUsesVisibleHeightWhileProgressRetainsItsTopAnchor() = runSkikoComposeUiTest {
        val pages = (0..3).map { ReaderPageEntry("key-$it", "chapter", "Chapter", "image-$it", it, 4) }
        val heights = listOf(60, 120, 120, 200)
        lateinit var list: LazyListState
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                list = rememberLazyListState()
                LazyColumn(Modifier.height(200.dp), state = list) {
                    items(pages.size, key = { pages[it].key }) { index ->
                        Box(Modifier.fillMaxWidth().height(heights[index].dp))
                    }
                }
            }
        }
        runOnIdle {
            assertEquals(pages[1], list.layoutInfo.mostVisiblePage(pages))
            assertEquals(pages[0], list.layoutInfo.mostVisiblePage(pages, pages[0].key), "显式选择完整可见的短图")
            list.requestScrollToItem(1, 20)
        }
        runOnIdle {
            assertEquals(pages[1], list.layoutInfo.mostVisiblePage(pages), "两页各露出 100 px 时取上方")
            list.requestScrollToItem(1, 21)
        }
        runOnIdle {
            assertEquals(pages[2], list.layoutInfo.mostVisiblePage(pages))
            assertEquals(1, list.firstVisibleItemIndex)
            assertEquals(21, list.firstVisibleItemScrollOffset)
            assertEquals(pages[2], list.layoutInfo.mostVisiblePage(pages.drop(1)), "裁剪旧窗口后仍按实测 key 识别")
        }
    }

    @Test fun clampedShortLastPageKeepsItsLabelAndGetsAKeyTurnBeforeChangingChapter() =
        runSkikoComposeUiTest(size = Size(133f, 530f)) {
            val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
            fun chapter(id: String, count: Int, next: String?) = ReaderSessionChapter(id, "default",
                ChapterContent((1..count).map { "$id-$it" }, null, next, id))
            val first = chapter("A", 12, "B")
            var chapters by mutableStateOf(listOf(first))
            try {
                setContent {
                    CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalInspectionMode provides true) {
                        CopyManga_NeoTheme {
                            ReaderSuccessContent(
                                images = first.content.imageUrls, chapterName = "A", sessionChapters = chapters,
                                readingDirection = "TOP_TO_BOTTOM", pageScale = "FIT_SCREEN", screenOrientation = "SYSTEM_DEFAULT",
                                onPrevChapter = {}, onNextChapter = {}, hasPrevChapter = false, hasNextChapter = true,
                                onRefresh = {}, onReadingDirectionChange = {}, onPageScaleChange = {}, onScreenOrientationChange = {},
                                pendingInitialPage = remember { mutableStateOf(11) }, activeChapterUuid = "A",
                                tools = ReaderToolsSettings(volumeKeys = true, pageAnimation = false, inlineComments = false,
                                    continuousChapters = true),
                                onAppendNext = { explicit -> if (explicit) chapters = listOf(first, chapter("B", 6, null)) },
                                modifier = Modifier.testTag("reader")
                            )
                        }
                    }
                }
                waitForIdle()
                onNodeWithText("12 / 12").assertIsDisplayed()
                fun press(key: Key) {
                    onNodeWithTag("reader").performKeyInput { pressKey(key) }
                    waitForIdle()
                }
                press(Key.VolumeUp)
                onNodeWithText("11 / 12").assertIsDisplayed()
                press(Key.VolumeDown)
                onNodeWithText("12 / 12").assertIsDisplayed()
                runOnIdle { assertEquals(1, chapters.size) }
                press(Key.VolumeDown)
                onNodeWithText("1 / 6").assertIsDisplayed()
            } finally { owner.destroy() }
        }

    @Test fun longChapterBoundaryPagesScrollToTheirEdgeWithOrWithoutTheAdjacentChapter() = runSkikoComposeUiTest {
        val last = ReaderPageEntry("a:last", "a", "A", "image-a", 8, 9)
        val first = ReaderPageEntry("b:first", "b", "B", "image-b", 0, 7)
        val pages = listOf(last, first)
        lateinit var list: LazyListState
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                list = rememberLazyListState()
                LazyColumn(Modifier.height(200.dp), state = list) {
                    items(pages.size, key = { pages[it].key }) { Box(Modifier.fillMaxWidth().height(500.dp)) }
                }
            }
        }
        fun assertStep(current: ReaderPageEntry, adjacent: ReaderPageEntry, forward: Boolean, pixels: Float) {
            assertEquals(pixels, list.layoutInfo.chapterEdgeScroll(current, adjacent, forward), "相邻章节已预取")
            assertEquals(pixels, list.layoutInfo.chapterEdgeScroll(current, null, forward), "相邻章节未预取")
        }
        runOnIdle {
            assertStep(last, first, true, 180f)
            list.requestScrollToItem(0, 250)
        }
        runOnIdle {
            assertStep(last, first, true, 50f)
            list.requestScrollToItem(0, 300)
        }
        runOnIdle {
            assertStep(last, first, true, 0f)
            list.requestScrollToItem(1, 40)
        }
        runOnIdle {
            assertStep(first, last, false, -40f)
            list.requestScrollToItem(1, 0)
        }
        runOnIdle { assertStep(first, last, false, 0f) }
    }

    @Test fun singlePageLabelFollowsTheDragWithoutSavingTheIntermediatePage() =
        checkDragLabel("SINGLE", "3 / 9", "4 / 9")

    @Test fun manuallyPairedLabelFollowsTheDragWithoutSavingTheIntermediateSpread() =
        checkDragLabel("DOUBLE", "3–4 / 9", "5–6 / 9")

    private fun checkDragLabel(layout: String, initialLabel: String, followingLabel: String) =
        runSkikoComposeUiTest(size = Size(1100f, 700f)) {
            val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
            val reports = mutableListOf<Int>()
            try {
                setContent {
                    CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalInspectionMode provides true) {
                        CopyManga_NeoTheme {
                            ReaderSuccessContent(
                                images = (0..8).map { "fixture-$it" }, chapterName = "Fixture",
                                readingDirection = "LEFT_TO_RIGHT", pageScale = "FIT_SCREEN", screenOrientation = "SYSTEM_DEFAULT",
                                onPrevChapter = {}, onNextChapter = {}, hasPrevChapter = false, hasNextChapter = false,
                                onRefresh = {}, onReadingDirectionChange = {}, onPageScaleChange = {}, onScreenOrientationChange = {},
                                pendingInitialPage = remember { mutableStateOf(2) },
                                tools = ReaderToolsSettings(pageLayout = layout, inlineComments = false,
                                    pairingRestarts = mapOf("preview" to listOf(2))),
                                onPosition = { _, page, _, _ -> reports += page }, modifier = Modifier.testTag("reader")
                            )
                        }
                    }
                }
                waitForIdle()
                onNodeWithText(initialLabel).assertIsDisplayed()
                mainClock.autoAdvance = false
                val reader = onNodeWithTag("reader")
                reader.performTouchInput {
                    down(Offset(1000f, 300f))
                    moveTo(Offset(900f, 300f))
                    moveTo(Offset(200f, 300f))
                }
                mainClock.advanceTimeBy(64)
                onNodeWithText(followingLabel).assertIsDisplayed()
                runOnUiThread { assertTrue(reports.isNotEmpty() && reports.all { it == 2 }, reports.toString()) }
                reader.performTouchInput {
                    moveTo(Offset(980f, 300f), delayMillis = 200)
                    advanceEventTime(200)
                    up()
                }
                mainClock.advanceTimeBy(600)
                onNodeWithText(initialLabel).assertIsDisplayed()
                runOnUiThread { assertTrue(reports.all { it == 2 }, reports.toString()) }
            } finally { owner.destroy() }
        }

    @Test fun continuousVolumeKeyWithAnimationDisabledJumpsWithinOneFrame() =
        runSkikoComposeUiTest(size = Size(440f, 620f)) {
            val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
            try {
                setContent {
                    CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalInspectionMode provides true) {
                        CopyManga_NeoTheme {
                            ReaderSuccessContent(
                                images = (0..8).map { "fixture-$it" }, chapterName = "Fixture",
                                readingDirection = "TOP_TO_BOTTOM", pageScale = "FIT_SCREEN", screenOrientation = "SYSTEM_DEFAULT",
                                onPrevChapter = {}, onNextChapter = {}, hasPrevChapter = false, hasNextChapter = false,
                                onRefresh = {}, onReadingDirectionChange = {}, onPageScaleChange = {}, onScreenOrientationChange = {},
                                pendingInitialPage = remember { mutableStateOf(2) },
                                tools = ReaderToolsSettings(volumeKeys = true, pageAnimation = false, inlineComments = false),
                                modifier = Modifier.testTag("reader")
                            )
                        }
                    }
                }
                waitForIdle()
                onNodeWithText("3 / 9").assertIsDisplayed()
                mainClock.autoAdvance = false
                onNodeWithTag("reader").performKeyInput { pressKey(Key.VolumeDown) }
                mainClock.advanceTimeByFrame()
                onNodeWithText("4 / 9").assertIsDisplayed()
            } finally { owner.destroy() }
        }

    @Test fun firstFrameUsesTheRequestedSpread() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
        try {
            for (mode in listOf("AUTO", "DOUBLE")) {
                val pending = mutableStateOf<Int?>(4)
                val scene = ImageComposeScene(1100, 700, coroutineContext = coroutineContext) {
                    CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalInspectionMode provides true) {
                        CopyManga_NeoTheme {
                            ReaderSuccessContent(
                                images = (0..8).map { "fixture-$it" }, chapterName = "Fixture",
                                readingDirection = "RIGHT_TO_LEFT", pageScale = "FIT_SCREEN",
                                screenOrientation = "SYSTEM_DEFAULT", onPrevChapter = {}, onNextChapter = {},
                                hasPrevChapter = false, hasNextChapter = false, onRefresh = {},
                                onReadingDirectionChange = {}, onPageScaleChange = {}, onScreenOrientationChange = {},
                                pendingInitialPage = pending,
                                tools = ReaderToolsSettings(pageLayout = mode, inlineComments = false)
                            )
                        }
                    }
                }
                try {
                    var time = 0L
                    fun frame(): List<Int> {
                        runCurrent()
                        time += 16_000_000
                        return scene.render(time).use { image ->
                            Bitmap.makeFromImage(image).use { bitmap ->
                                (60..1040 step 35).flatMap { x ->
                                    (60..640 step 35).map { y -> bitmap.getColor(x, y) }
                                }
                            }
                        }
                    }
                    val first = frame()
                    repeat(6) { frame() }
                    val settled = frame()
                    assertTrue(settled.any { it != 0xff000000.toInt() }, "The spread must contain visible images")
                    assertTrue(first == settled, "The first frame must show the settled spread in $mode mode")
                } finally {
                    scene.close()
                }
            }
        } finally {
            owner.destroy()
            Dispatchers.resetMain()
        }
    }

    @Test fun exactSecondLeafSurvivesModeDirectionWindowAndLocalRePairing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
        val pending = mutableStateOf<Int?>(4)
        var tools by mutableStateOf(ReaderToolsSettings(pageLayout = "DOUBLE", inlineComments = false))
        var direction by mutableStateOf("RIGHT_TO_LEFT")
        var width by mutableStateOf(1100.dp)
        val reported = mutableListOf<Int>()
        var scene: ImageComposeScene? = null
        try {
            val raster = ImageComposeScene(1200, 800, coroutineContext = coroutineContext) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalInspectionMode provides true) {
                    CopyManga_NeoTheme {
                        Box(Modifier.size(width, 700.dp)) {
                            ReaderSuccessContent(images = (0..8).map { "fixture-$it" }, chapterName = "Fixture",
                                readingDirection = direction, pageScale = "FIT_SCREEN", screenOrientation = "SYSTEM_DEFAULT",
                                onPrevChapter = {}, onNextChapter = {}, hasPrevChapter = false, hasNextChapter = false,
                                onRefresh = {}, onReadingDirectionChange = {}, onPageScaleChange = {}, onScreenOrientationChange = {},
                                pendingInitialPage = pending, tools = tools, onPosition = { _, page, _, _ -> reported += page })
                        }
                    }
                }
            }
            scene = raster
            var time = 0L
            fun settle() { repeat(8) { runCurrent(); time += 16_000_000; raster.render(time).close(); runCurrent() } }
            settle(); assertEquals(4, reported.last())
            tools = tools.copy(pageLayout = "SINGLE"); settle(); assertEquals(4, reported.last())
            tools = tools.copy(pageLayout = "DOUBLE"); settle(); assertEquals(4, reported.last())
            direction = "LEFT_TO_RIGHT"; settle(); assertEquals(4, reported.last())
            width = 480.dp; settle(); assertEquals(4, reported.last())
            width = 1100.dp; settle(); assertEquals(4, reported.last())
            tools = tools.copy(pairingRestarts = mapOf("preview" to listOf(2, 5)))
            settle(); assertEquals(4, reported.last())
            tools = tools.copy(pairingRestarts = emptyMap()); settle(); assertEquals(4, reported.last())
            pending.value = 6; settle(); assertEquals(6, reported.last())
            tools = tools.copy(pageLayout = "AUTO"); settle(); assertEquals(6, reported.last())
            assertTrue(reported.all { it == 4 || it == 6 }, "Reflow must not write a transient group index to history: $reported")
        } finally { scene?.close(); owner.destroy(); Dispatchers.resetMain() }
    }
}
