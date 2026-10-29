package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReaderContextClickUiTest {
    @Test fun rightClickOpensSettingsWithoutDispatchingPageGestures() =
        runSkikoComposeUiTest(size = Size(400f, 400f)) {
            var settings = 0
            var forward = 0
            var backward = 0
            var menu = 0
            var imageMenu = 0
            var zoom = 0
            setContent {
                Box(Modifier.fillMaxSize().readerContextClick(true) { settings++ }) {
                    Box(Modifier.fillMaxSize().testTag("page").readerTapGestures(
                        isRtl = false, isContinuous = false,
                        onForward = { forward++ }, onBackward = { backward++ },
                        onShowMenu = { menu++ }, onLongPress = { imageMenu++ },
                        onBottomAreaTap = { menu++ }, onDoubleTap = { zoom++ }, allowTap = { true }
                    ))
                }
            }
            // 覆盖两侧翻页区、中央和底部工具栏区。
            for (position in listOf(Offset(10f, 150f), Offset(390f, 150f), Offset(200f, 150f), Offset(200f, 350f))) {
                onNodeWithTag("page").performMouseInput { rightClick(position) }
            }
            waitForIdle()
            assertEquals(4, settings)
            assertEquals(0, forward + backward + menu + imageMenu + zoom)
            onNodeWithTag("page").performMouseInput { click(Offset(390f, 150f)) }
            mainClock.advanceTimeBy(500)
            waitForIdle()
            assertEquals(1, forward)
            assertEquals(4, settings)
            onNodeWithTag("page").performTouchInput { longClick(Offset(200f, 150f)) }
            assertEquals(1, imageMenu)
            assertEquals(4, settings)
        }

    @Test fun blockedReaderIgnoresRightClickAndUsesLatestCallbackAfterRecomposition() =
        runSkikoComposeUiTest(size = Size(400f, 400f)) {
            var enabled by mutableStateOf(false)
            var amount by mutableStateOf(1)
            var opened = 0
            setContent {
                val currentAmount = amount
                Box(Modifier.fillMaxSize().testTag("reader").readerContextClick(enabled) { opened += currentAmount })
            }
            onNodeWithTag("reader").performMouseInput { rightClick() }
            assertEquals(0, opened)
            runOnIdle { enabled = true; amount = 2 }
            onNodeWithTag("reader").performMouseInput { rightClick() }
            assertEquals(2, opened)
            runOnIdle { enabled = false }
            onNodeWithTag("reader").performMouseInput { rightClick() }
            assertEquals(2, opened)
        }
}
