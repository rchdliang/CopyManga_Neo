package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.multiplatform.app.RouteLifecycleOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalTestApi::class)
class ReaderLongVolumePreloadUiTest {
    @Test fun prepend129PagesKeepsCover() = checkPrepend(129)

    @Test fun prepend130PagesKeepsCover() = checkPrepend(130)

    @Test fun prepend180PagesAllowsManualChapterNavigation() = checkPrepend(180, checkNavigation = true)

    @Test fun longVolumeArrivingDuringSwipePreservesTheUsersTurn() = checkPrepend(180, duringSwipe = true)

    private fun checkPrepend(pageCount: Int, duringSwipe: Boolean = false, checkNavigation: Boolean = false) =
        runSkikoComposeUiTest(size = Size(400f, 780f)) {
            val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
            fun volume(number: Int) = ReaderSessionChapter(number.toString(), "tankobon", ChapterContent(
                List(pageCount) { "fixture-$number-$it" }, (number - 1).toString(),
                (number + 1).takeIf { it <= 16 }?.toString(), "第${number}卷"
            ))
            var chapters by mutableStateOf(listOf(volume(16)))
            var active by mutableStateOf("16")
            val initial = mutableStateOf<Int?>(0)
            val requests = mutableListOf<String>()
            val activations = mutableListOf<String>()
            val positions = mutableListOf<Pair<String, Int>>()
            fun deliverPrevious() {
                val requested = requests.last()
                assertEquals(chapters.first().content.prevUuid, requested)
                chapters = listOf(volume(requested.toInt())) + chapters
            }
            try {
                setContent {
                    CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalInspectionMode provides true) {
                        CopyManga_NeoTheme {
                            val current = chapters.first { it.uuid == active }.content
                            ReaderSuccessContent(
                                images = current.imageUrls, chapterName = current.chapterName,
                                readingDirection = "RIGHT_TO_LEFT", pageScale = "FIT_SCREEN", screenOrientation = "PORTRAIT",
                                onPrevChapter = { fail("上一卷已在窗口内") }, onNextChapter = { fail("下一卷已在窗口内") },
                                hasPrevChapter = true, hasNextChapter = current.nextUuid != null,
                                onRefresh = {}, onReadingDirectionChange = {}, onPageScaleChange = {}, onScreenOrientationChange = {},
                                pendingInitialPage = initial, sessionChapters = chapters, activeChapterUuid = active,
                                tools = ReaderToolsSettings(pageLayout = "SINGLE", continuousChapters = true, inlineComments = false),
                                modifier = Modifier.testTag("reader"),
                                onPosition = { id, page, _, _ -> positions += id to page },
                                onActivateChapter = { active = it; activations += it },
                                onWindowSettled = { id -> chapters = ReaderWindowPolicy.around(chapters, id) { it.uuid } },
                                onPrependPrevious = { explicit ->
                                    assertFalse(explicit)
                                    requests += chapters.first().content.prevUuid!!
                                }
                            )
                        }
                    }
                }
                waitForIdle()
                assertEquals(listOf("15"), requests)
                val reader = onNodeWithTag("reader")
                val start = Offset(65f, 380f)
                val end = Offset(345f, 380f)
                if (duringSwipe) {
                    mainClock.autoAdvance = false
                    reader.performTouchInput { down(start); moveTo(Offset(120f, 380f)); moveTo(end) }
                    mainClock.advanceTimeBy(32)
                    runOnUiThread { deliverPrevious() }
                    mainClock.advanceTimeBy(64)
                    reader.performTouchInput { up() }
                    mainClock.autoAdvance = true
                } else {
                    runOnIdle { deliverPrevious() }
                }
                waitForIdle()
                val page = if (duringSwipe) 1 else 0
                assertEquals(listOf("15"), requests, "预取完成后又请求了更早的卷")
                assertTrue(activations.isNotEmpty() && activations.all { it == "16" }, "激活了其他卷：$activations")
                assertTrue(positions.all { it.first == "16" && it.second in 0..page }, "上报了其他阅读位置：$positions")
                assertEquals("16" to page, positions.last())
                onNodeWithText("第16卷").assertIsDisplayed()
                onNodeWithText("${page + 1} / $pageCount").assertIsDisplayed()

                if (checkNavigation) {
                    // 从封面后退一页应落到上一卷末页，再前进一页就回到原卷封面。
                    reader.performTouchInput { swipe(end, start, 200) }
                    waitForIdle()
                    assertEquals("15" to pageCount - 1, positions.last())
                    onNodeWithText("第15卷").assertIsDisplayed()
                    onNodeWithText("$pageCount / $pageCount").assertIsDisplayed()
                    reader.performTouchInput { swipe(start, end, 200) }
                    waitForIdle()
                    assertEquals("16" to 0, positions.last())
                    assertEquals(listOf("15"), requests)

                    reader.performTouchInput { click(center) }
                    // 单击要等双击识别窗口结束后才会展开阅读菜单。
                    mainClock.advanceTimeBy(400)
                    waitForIdle()
                    onNodeWithContentDescription("上一章").performClick()
                    waitForIdle()
                    assertEquals("15" to 0, positions.last())
                    assertEquals(listOf("15", "14"), requests)
                    runOnIdle { deliverPrevious() }
                    waitForIdle()
                    assertEquals("15" to 0, positions.last())
                    reader.performTouchInput { swipe(start, end, 200) }
                    waitForIdle()
                    assertEquals("15" to 1, positions.last())
                    assertEquals(listOf("15", "14"), requests)
                }
            } finally {
                owner.destroy()
            }
        }
}
