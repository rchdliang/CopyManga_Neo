package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ReaderCommentMouseNavigationUiTest {
    @Test fun centerWheelScrollsAndOnlyContinuesPastLoadedBoundaries() = runSkikoComposeUiTest(size = Size(1100f, 700f)) {
        lateinit var list: LazyListState
        var endLoaded by mutableStateOf(false)
        var forward = 0
        var backward = 0
        setContent {
            list = rememberLazyListState()
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("comments")
                    .readerCommentMouseNavigation(list, true, false, endLoaded, { forward++ }, { backward++ })
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
        assertEquals(0, forward, "An unfinished comment feed must not be skipped at its temporary bottom")
        runOnIdle { endLoaded = true }
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

    @Test fun rtlMouseDragAndEdgesWinOverClickableComments() = dragAndClick(rtl = true)
    @Test fun ltrMouseDragAndEdgesWinOverClickableComments() = dragAndClick(rtl = false)

    private fun dragAndClick(rtl: Boolean) = runSkikoComposeUiTest(size = Size(1100f, 700f)) {
        var forward = 0
        var backward = 0
        var clicks = 0
        var blocked by mutableStateOf(false)
        setContent {
            val list = rememberLazyListState()
            Box(Modifier.fillMaxSize().testTag("comments")
                .readerCommentMouseNavigation(list, rtl, blocked, true, { forward++ }, { backward++ })
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

        fun drag(start: Offset, end: Offset) {
            onNodeWithTag("comments").performMouseInput {
                moveTo(start); press(); advanceEventTime(40); moveTo((start + end) / 2f)
                advanceEventTime(40); moveTo(end); release()
            }
            waitForIdle()
        }
        drag(Offset(550f, 350f), Offset(if (rtl) 850f else 250f, 350f))
        assertEquals(2, forward)
        assertEquals(1, clicks, "Dragging must cancel the child's click")
        drag(Offset(550f, 350f), Offset(550f, 550f))
        assertEquals(2, forward, "Vertical gestures belong to the comments")
        assertEquals(1, backward)

        runOnIdle { blocked = true }
        onNodeWithTag("comments").performMouseInput { click(Offset(10f, 350f)); scroll(1f) }
        drag(Offset(550f, 350f), Offset(850f, 350f))
        assertEquals(2, forward, "An open editor must keep mouse interaction")
        assertEquals(1, backward)
    }
}
