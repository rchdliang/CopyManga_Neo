package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ReaderCommentMouseNavigationUiTest {
    @Test fun centerWheelScrollsAndContinuesPastVisibleBoundaries() = runSkikoComposeUiTest(size = Size(1100f, 700f)) {
        lateinit var list: LazyListState
        var forward = 0
        var backward = 0
        setContent {
            list = rememberLazyListState()
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("comments")
                    .readerCommentMouseNavigation(list, true, false, { forward++ }, { backward++ })
                    .padding(horizontal = READER_COMMENT_EDGE_WIDTH)) {
                    LazyColumn(state = list) {
                        items(30) { Text("Comment $it", Modifier.fillMaxWidth().height(100.dp), color = MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
        }
        onNodeWithTag("comments").performMouseInput { moveTo(Offset(550f, 350f)); scroll(1f) }
        waitForIdle()
        assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0)
        assertEquals(0, forward)
        assertEquals(0, backward)

        runOnIdle { list.requestScrollToItem(29) }
        waitForIdle()
        onNodeWithTag("comments").performMouseInput { advanceEventTime(400); scroll(1f) }
        waitForIdle()
        assertEquals(1, forward)
        onNodeWithTag("comments").performMouseInput { advanceEventTime(20); scroll(1f) }
        waitForIdle()
        assertEquals(1, forward, "A wheel burst must not skip several pages")

        runOnIdle { list.requestScrollToItem(0) }
        waitForIdle()
        onNodeWithTag("comments").performMouseInput { advanceEventTime(400); scroll(-1f) }
        waitForIdle()
        assertEquals(1, backward)
        runOnIdle { list.requestScrollToItem(12) }
        waitForIdle()
        val position = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
        onNodeWithTag("comments").performMouseInput { moveTo(Offset(10f, 350f)); advanceEventTime(400); scroll(1f) }
        waitForIdle()
        assertEquals(2, forward)
        assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
    }

    @Test fun rtlEdgesPreserveClickableComments() = edgesAndClick(rtl = true)
    @Test fun ltrEdgesPreserveClickableComments() = edgesAndClick(rtl = false)

    private fun edgesAndClick(rtl: Boolean) = runSkikoComposeUiTest(size = Size(1100f, 700f)) {
        var forward = 0
        var backward = 0
        var clicks = 0
        var blocked by mutableStateOf(false)
        setContent {
            val list = rememberLazyListState()
            Box(Modifier.fillMaxSize().testTag("comments")
                .readerCommentMouseNavigation(list, rtl, blocked, { forward++ }, { backward++ })
                .padding(horizontal = READER_COMMENT_EDGE_WIDTH)) {
                Box(Modifier.fillMaxSize().clickable { clicks++ })
            }
        }
        onNodeWithTag("comments").performMouseInput { click(Offset(550f, 350f)) }
        waitForIdle()
        assertEquals(1, clicks)
        onNodeWithTag("comments").performMouseInput { click(Offset(if (rtl) 10f else 1090f, 350f)) }
        waitForIdle()
        assertEquals(1, forward)
        onNodeWithTag("comments").performMouseInput { click(Offset(if (rtl) 1090f else 10f, 350f)) }
        waitForIdle()
        assertEquals(1, backward)

        runOnIdle { blocked = true }
        onNodeWithTag("comments").performMouseInput { click(Offset(10f, 350f)); scroll(1f) }
        waitForIdle()
        assertEquals(1, forward, "An open editor must keep mouse interaction")
        assertEquals(1, backward)
    }

    @Test fun rtlMouseDragMovesCommentsBeforeRelease() = followsPointer(rtl = true, touch = false)
    @Test fun ltrMouseDragMovesCommentsBeforeRelease() = followsPointer(rtl = false, touch = false)
    @Test fun rtlTouchDragMovesCommentsBeforeRelease() = followsPointer(rtl = true, touch = true)
    @Test fun ltrTouchDragMovesCommentsBeforeRelease() = followsPointer(rtl = false, touch = true)

    private fun followsPointer(rtl: Boolean, touch: Boolean) = runSkikoComposeUiTest(size = Size(1100f, 700f)) {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        val pages = listOf(
            ReaderPageEntry("1:image:0", "1", "One", "fixture-1", 0, 1),
            ReaderPageEntry("1:comments", "1", "One", null, 0, 1),
            ReaderPageEntry("2:image:0", "2", "Two", "fixture-2", 0, 1)
        )
        lateinit var pager: PagerState
        var clicks = 0
        var turns = 0
        var blocked by mutableStateOf(false)
        try {
            setContent {
                pager = rememberPagerState(initialPage = 1) { 3 }
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Box(Modifier.fillMaxSize().testTag("reader")) {
                        ReaderSpreadContent(pages, readerSpreads(pages, false), pager, rtl, ContentScale.Fit,
                            loader, ReaderToolsSettings(), { turns++ }, { turns-- }, {}, {}, {}, {}, { _, _, _ -> }, {}, {
                                val list = rememberLazyListState()
                                Box(Modifier.fillMaxSize().testTag("comments")
                                    .readerCommentMouseNavigation(list, rtl, blocked, { turns++ }, { turns-- })
                                    .padding(horizontal = READER_COMMENT_EDGE_WIDTH)) {
                                    Box(Modifier.fillMaxSize().clickable { clicks++ })
                                }
                            })
                    }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            val start = Offset(if (rtl) 200f else 900f, 350f)
            val end = Offset(if (rtl) 900f else 200f, 350f)
            fun pressAndMove() {
                if (touch) onNodeWithTag("reader").performTouchInput {
                    down(start); moveTo((start + end) / 2f, delayMillis = 32); moveTo(end, delayMillis = 32)
                } else onNodeWithTag("reader").performMouseInput {
                    moveTo(start); press(); advanceEventTime(32); moveTo((start + end) / 2f); advanceEventTime(32); moveTo(end)
                }
                mainClock.advanceTimeBy(48)
                waitForIdle()
            }
            fun releaseAt(position: Offset) {
                if (touch) onNodeWithTag("reader").performTouchInput {
                    moveTo(position, delayMillis = 200); advanceEventTime(200); up()
                } else onNodeWithTag("reader").performMouseInput {
                    advanceEventTime(200); moveTo(position); advanceEventTime(200); release()
                }
                mainClock.advanceTimeBy(1000)
                waitForIdle()
            }
            pressAndMove()
            assertTrue(pager.currentPage + pager.currentPageOffsetFraction > 1.5f, "The pager must follow the held pointer")
            val x = onNodeWithTag("comments").fetchSemanticsNode().positionInRoot.x
            assertTrue(if (rtl) x > 500f else x < -500f, "Comments disappeared or stayed still after crossing halfway: $x")
            releaseAt(start + Offset(if (rtl) 80f else -80f, 0f))
            assertEquals(1, pager.currentPage, "A short/reversed drag must snap back")
            assertEquals(0f, pager.currentPageOffsetFraction, .001f)
            assertEquals(0, clicks)
            assertEquals(0, turns, "Dragging must scroll the pager, not invoke discrete page callbacks")

            runOnIdle { blocked = true }
            mainClock.advanceTimeByFrame(); waitForIdle()
            pressAndMove(); releaseAt(end)
            assertEquals(1, pager.currentPage, "Editors must retain horizontal pointer gestures")
            runOnIdle { blocked = false }
            mainClock.advanceTimeByFrame(); waitForIdle()
            pressAndMove(); releaseAt(end)
            assertEquals(2, pager.currentPage)
            assertEquals(0f, pager.currentPageOffsetFraction, .001f)
        } finally { loader.shutdown() }
    }
}
