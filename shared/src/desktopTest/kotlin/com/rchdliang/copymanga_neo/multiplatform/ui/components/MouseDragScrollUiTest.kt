package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class MouseDragScrollUiTest {
    @Test fun mouseDragScrollsList() = runSkikoComposeUiTest(size = Size(400f, 400f)) {
        lateinit var state: LazyListState
        setContent {
            MaterialTheme {
                state = rememberLazyListState()
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize().testTag("list")
                        .mouseDragScroll(state, Orientation.Vertical)
                ) {
                    items(50) { Text("item $it", Modifier.fillMaxWidth().height(80.dp)) }
                }
            }
        }
        onNodeWithTag("list").performMouseInput {
            moveTo(Offset(200f, 350f)); press(); moveBy(Offset(0f, -100f)); moveBy(Offset(0f, -100f)); release()
        }
        waitForIdle()
        assertTrue(state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)
    }

    @Test fun mouseDragDownAtTopTriggersPullToRefresh() = runSkikoComposeUiTest(size = Size(400f, 600f)) {
        var refreshes = 0
        setContent {
            MaterialTheme {
                val state = rememberLazyListState()
                PullToRefreshBox(isRefreshing = false, onRefresh = { refreshes++ }) {
                    LazyColumn(
                        state = state,
                        modifier = Modifier.fillMaxSize().testTag("list")
                            .mouseDragScroll(state, Orientation.Vertical)
                    ) {
                        items(50) { Text("item $it", Modifier.fillMaxWidth().height(80.dp)) }
                    }
                }
            }
        }
        onNodeWithTag("list").performMouseInput {
            moveTo(Offset(200f, 50f)); press()
            repeat(10) { moveBy(Offset(0f, 50f)) }
            release()
        }
        waitForIdle()
        assertEquals(1, refreshes)
    }

    @Test fun mouseDragTurnsPage() = runSkikoComposeUiTest(size = Size(400f, 400f)) {
        lateinit var state: PagerState
        setContent {
            MaterialTheme {
                state = rememberPagerState { 5 }
                HorizontalPager(
                    state = state,
                    modifier = Modifier.fillMaxSize().testTag("pager")
                        .mouseDragScroll(
                            state, Orientation.Horizontal,
                            flingBehavior = PagerDefaults.flingBehavior(state)
                        )
                ) { Box(Modifier.fillMaxSize()) { Text("page $it") } }
            }
        }
        onNodeWithTag("pager").performMouseInput {
            moveTo(Offset(350f, 200f)); press()
            repeat(6) { moveBy(Offset(-50f, 0f)) }
            release()
        }
        waitForIdle()
        assertEquals(1, state.currentPage)
        assertEquals(0f, state.currentPageOffsetFraction, 0.001f)
    }
}
