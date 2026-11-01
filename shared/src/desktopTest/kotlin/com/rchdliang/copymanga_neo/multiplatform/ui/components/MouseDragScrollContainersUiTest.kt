package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class MouseDragScrollContainersUiTest {
    @Test fun bookDetailCatalogueDragsWithoutClickingAndStillAcceptsTouchAndWheel() = runSkikoComposeUiTest(size = Size(500f, 700f)) {
        lateinit var grid: LazyGridState
        var clicks = 0
        setContent {
            grid = rememberLazyGridState()
            MaterialTheme {
                BookDetailLayout("book", "Fixture", {}, "阅读", null, {},
                    modifier = Modifier.fillMaxSize().testTag("detail"), gridState = grid,
                    header = { Box(it.fillMaxWidth().height(180.dp)) },
                    catalogue = {
                        items(200) { index ->
                            Box(Modifier.fillMaxWidth().height(64.dp).clickable { clicks++ }) {
                                Text("Chapter $index", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    })
            }
        }
        onNodeWithTag("detail").performMouseInput {
            moveTo(Offset(240f, 600f)); press()
            repeat(5) { advanceEventTime(16); moveBy(Offset(0f, -70f)) }
            release()
        }
        waitForIdle()
        assertTrue(grid.firstVisibleItemIndex > 0)
        assertEquals(0, clicks, "Dragging a chapter must not open it")
        onNodeWithTag("detail").performMouseInput { click(Offset(240f, 350f)) }
        waitForIdle(); assertEquals(1, clicks)
        onNodeWithTag("detail").performMouseInput { exit() }
        runOnIdle { grid.requestScrollToItem(0) }; waitForIdle()
        onNodeWithTag("detail").performTouchInput { swipeUp() }
        waitForIdle(); assertTrue(grid.firstVisibleItemIndex > 0)
        runOnIdle { grid.requestScrollToItem(0) }; waitForIdle()
        onNodeWithTag("detail").performMouseInput { moveTo(Offset(250f, 350f)); scroll(8f) }
        waitForIdle(); assertTrue(grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 0)
    }

    @Test fun formDragsButTextFieldStillOwnsTextSelection() = runSkikoComposeUiTest(size = Size(500f, 600f)) {
        lateinit var scroll: ScrollState
        var value by mutableStateOf(TextFieldValue("Select text in this editable field"))
        setContent {
            scroll = rememberScrollState()
            MaterialTheme {
                Column(Modifier.fillMaxSize().testTag("form").verticalScrollWithMouse(scroll)) {
                    TextField(value, { value = it }, Modifier.fillMaxWidth().testTag("editor"))
                    repeat(30) { Text("Setting $it", Modifier.fillMaxWidth().height(60.dp), color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
        onNodeWithTag("editor").performMouseInput {
            moveTo(Offset(30f, 28f)); press(); advanceEventTime(16); moveTo(Offset(170f, 28f)); release()
        }
        waitForIdle()
        assertTrue(value.selection.start != value.selection.end, "The editor must retain selection drags")
        assertEquals(0, scroll.value)
        onNodeWithTag("form").performMouseInput {
            moveTo(Offset(300f, 500f)); press(); advanceEventTime(16); moveTo(Offset(300f, 200f)); release()
        }
        waitForIdle(); assertTrue(scroll.value > 0)
    }

    @Test fun rtlHorizontalListDragsAndDisabledListDoesNotScroll() = runSkikoComposeUiTest(size = Size(500f, 400f)) {
        lateinit var state: LazyListState
        var enabled by mutableStateOf(true)
        setContent {
            state = rememberLazyListState()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MouseDragLazyRow(Modifier.fillMaxSize().testTag("row"), state, userScrollEnabled = enabled) {
                    items(30) { Box(Modifier.width(100.dp).fillMaxHeight().clickable {}) }
                }
            }
        }
        onNodeWithTag("row").performMouseInput { moveTo(Offset(80f, 200f)); press(); moveTo(Offset(380f, 200f)); release() }
        waitForIdle(); assertTrue(state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)
        runOnIdle { enabled = false; state.requestScrollToItem(0) }; waitForIdle()
        onNodeWithTag("row").performMouseInput { moveTo(Offset(80f, 200f)); press(); moveTo(Offset(380f, 200f)); release() }
        waitForIdle(); assertEquals(0, state.firstVisibleItemIndex); assertEquals(0, state.firstVisibleItemScrollOffset)
    }

    @Test fun disablingDuringDragReleasesTheScrollMutation() = runSkikoComposeUiTest(size = Size(500f, 600f)) {
        lateinit var state: LazyListState
        var enabled by mutableStateOf(true)
        setContent {
            state = rememberLazyListState()
            MouseDragLazyColumn(Modifier.fillMaxSize().testTag("list"), state, userScrollEnabled = enabled) {
                items(30) { Box(Modifier.fillMaxWidth().height(100.dp)) }
            }
        }
        onNodeWithTag("list").performMouseInput { moveTo(Offset(250f, 500f)); press(); moveTo(Offset(250f, 250f)) }
        waitForIdle(); assertTrue(state.isScrollInProgress)
        runOnIdle { enabled = false }; waitForIdle()
        assertFalse(state.isScrollInProgress, "Cancelling a drag must release the scrolling coroutine")
        onNodeWithTag("list").performMouseInput { release() }
    }
}
