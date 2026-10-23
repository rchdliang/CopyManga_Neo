package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ReaderSpreadInputUiTest {
    @Test fun keyboardFollowsDirectionAndDoesNotStealEditorOrModifiedInput() =
        runSkikoComposeUiTest(size = Size(600f, 400f)) {
            val focus = FocusRequester()
            val zoom = ReaderZoomState()
            var rtl by mutableStateOf(true)
            var blocked by mutableStateOf(false)
            var page = 0
            setContent {
                var text by remember { mutableStateOf("") }
                Box(Modifier.fillMaxSize().testTag("reader").onSizeChanged(zoom::updateViewport)
                    .focusRequester(focus)
                    .readerSpreadKeyboard(true, blocked, rtl, zoom, {}, { page++ }, { page-- }).focusable()) {
                    BasicTextField(text, { text = it }, Modifier.testTag("editor"))
                }
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
            onNodeWithTag("reader").performKeyInput { pressKey(Key.DirectionLeft) }
            assertEquals(1, page)
            rtl = false
            waitForIdle()
            onNodeWithTag("reader").performKeyInput { pressKey(Key.DirectionRight) }
            assertEquals(2, page)
            onNodeWithTag("reader").performKeyInput {
                keyDown(Key.CtrlLeft); pressKey(Key.DirectionRight); keyUp(Key.CtrlLeft)
                keyDown(Key.AltLeft); pressKey(Key.PageDown); keyUp(Key.AltLeft)
                pressKey(Key.Equals)
            }
            assertEquals(2, page); assertTrue(zoom.zoomed)
            onNodeWithTag("reader").performKeyInput { pressKey(Key.DirectionRight) }
            assertEquals(2, page); assertTrue(zoom.offset.x < 0f)
            onNodeWithTag("reader").performKeyInput { pressKey(Key.Zero); pressKey(Key.PageDown) }
            assertEquals(1f, zoom.scale); assertEquals(3, page)
            blocked = true; waitForIdle()
            onNodeWithTag("reader").performKeyInput { pressKey(Key.PageDown) }
            assertEquals(3, page)
            blocked = false; waitForIdle()
            onNodeWithTag("editor").performClick().performTextInput("chapter 42")
            onNodeWithTag("editor").performKeyInput { pressKey(Key.Spacebar); pressKey(Key.DirectionLeft) }
            assertEquals(3, page)
        }

    @Test fun wheelZoomKeepsFocalPointAndPagingBurstSurvivesActiveSpreadChange() =
        runSkikoComposeUiTest(size = Size(400f, 400f)) {
            var zoom by mutableStateOf(ReaderZoomState())
            var pages = 0
            setContent {
                Box(Modifier.fillMaxSize().testTag("reader").onSizeChanged(zoom::updateViewport)
                    .readerSpreadWheel(zoom, true, {}, { pages++; zoom = ReaderZoomState() }, { pages-- }))
            }
            onNodeWithTag("reader").performMultiModalInput {
                key { keyDown(Key.CtrlLeft) }
                mouse { moveTo(Offset(300f, 100f)); scroll(-2f) }
                key { keyUp(Key.CtrlLeft) }
            }
            val scene = (Offset(300f, 100f) - zoom.viewportCenter() - zoom.offset) / zoom.scale
            assertEquals(100f, scene.x, 0.01f); assertEquals(-100f, scene.y, 0.01f)
            val before = zoom.offset
            onNodeWithTag("reader").performMouseInput { scroll(1f) }
            assertEquals(0, pages); assertTrue(zoom.offset != before)
            runOnIdle { zoom.reset() }
            onNodeWithTag("reader").performMouseInput { scroll(1f) }
            waitForIdle(); assertEquals(1, pages)
            onNodeWithTag("reader").performMouseInput { scroll(1f) }
            waitForIdle(); assertEquals(1, pages)
            onNodeWithTag("reader").performMouseInput { advanceEventTime(400); scroll(-1f) }
            waitForIdle(); assertEquals(0, pages)
        }
}
