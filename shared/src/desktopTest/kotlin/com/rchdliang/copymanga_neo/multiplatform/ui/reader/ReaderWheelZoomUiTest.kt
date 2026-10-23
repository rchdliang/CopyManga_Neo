package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ReaderWheelZoomUiTest {
    @Test
    fun controlWheelKeepsMouseAnchorAndDoesNotTurnPages() = runSkikoComposeUiTest(size = Size(400f, 400f)) {
        val zoom = ReaderZoomState()
        var pages = 0
        var interactions = 0
        setContent {
            Box(
                Modifier.fillMaxSize().testTag("reader").onSizeChanged(zoom::updateViewport)
                .readerWheelZoom(zoom, true, 5f, { interactions++ })
                .readerWheelPaging(true, { pages++ }, { pages-- })
            )
        }
        val focal = Offset(300f, 100f)
        onNodeWithTag("reader").performMultiModalInput {
            key { keyDown(Key.CtrlLeft) }
            mouse { moveTo(focal); scroll(-1f) }
            key { keyUp(Key.CtrlLeft) }
        }
        waitForIdle()
        assertTrue(zoom.scale > 1f)
        assertEquals(0, pages)
        assertEquals(1, interactions)
        val scenePoint = (focal - Offset(200f, 200f) - zoom.offset) / zoom.scale
        assertEquals(100f, scenePoint.x, 0.001f)
        assertEquals(-100f, scenePoint.y, 0.001f)
        onNodeWithTag("reader").performMultiModalInput {
            key { keyDown(Key.CtrlLeft) }
            mouse { repeat(10) { scroll(-10f) } }
            key { keyUp(Key.CtrlLeft) }
        }
        waitForIdle(); assertEquals(5f, zoom.scale)
        onNodeWithTag("reader").performMultiModalInput {
            key { keyDown(Key.CtrlLeft) }
            mouse { repeat(10) { scroll(10f) } }
            key { keyUp(Key.CtrlLeft) }
        }
        waitForIdle(); assertEquals(1f, zoom.scale); assertEquals(0, pages)
        onNodeWithTag("reader").performMouseInput { scroll(1f) }
        waitForIdle(); assertEquals(1, pages)
    }

    @Test
    fun controlWheelDoesNotAlsoScrollContinuousReader() = runSkikoComposeUiTest(size = Size(400f, 400f)) {
        val zoom = ReaderZoomState()
        lateinit var list: LazyListState
        setContent {
            list = rememberLazyListState()
            Box(
                Modifier.fillMaxSize().testTag("reader").onSizeChanged(zoom::updateViewport)
                .readerWheelZoom(zoom, true, 3f, {})
            ) {
                LazyColumn(state = list) { items(30) { Box(Modifier.fillMaxWidth().height(80.dp)) } }
            }
        }
        onNodeWithTag("reader").performMultiModalInput {
            key { keyDown(Key.CtrlLeft) }
            mouse { moveTo(Offset(200f, 200f)); scroll(-1f) }
            key { keyUp(Key.CtrlLeft) }
        }
        waitForIdle(); assertTrue(zoom.zoomed)
        assertEquals(0, list.firstVisibleItemIndex); assertEquals(0, list.firstVisibleItemScrollOffset)
        onNodeWithTag("reader").performMouseInput { scroll(3f) }
        waitForIdle()
        assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0)
    }
}
