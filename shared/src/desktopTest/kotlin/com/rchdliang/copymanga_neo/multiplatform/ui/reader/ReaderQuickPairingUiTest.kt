package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.multiplatform.app.RouteLifecycleOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ReaderQuickPairingUiTest {
    @Test fun toolbarAdjustsAndUndoesWithoutMovingSecondLeafOrChangingOtherBoundaries() =
        runSkikoComposeUiTest(size = Size(1100f, 750f)) {
            val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
            val saved = mapOf("preview" to listOf(6), "other" to listOf(3))
            var tools by mutableStateOf(ReaderToolsSettings(pageLayout = "DOUBLE", inlineComments = false, pairingRestarts = saved))
            val reported = mutableListOf<Int>()
            try {
                setContent {
                    CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalInspectionMode provides true) {
                        CopyManga_NeoTheme {
                            ReaderSuccessContent(images = (0..8).map { "fixture-$it" }, chapterName = "Fixture",
                                readingDirection = "RIGHT_TO_LEFT", pageScale = "FIT_SCREEN", screenOrientation = "SYSTEM_DEFAULT",
                                onPrevChapter = {}, onNextChapter = {}, hasPrevChapter = false, hasNextChapter = false,
                                onRefresh = {}, onReadingDirectionChange = {}, onPageScaleChange = {}, onScreenOrientationChange = {},
                                pendingInitialPage = remember { mutableStateOf(2) }, tools = tools, onToolsChange = { tools = it(tools) },
                                onPosition = { _, page, _, _ -> reported += page }, modifier = Modifier.testTag("reader"))
                        }
                    }
                }
                waitForIdle()
                onNodeWithTag("reader").performTouchInput { click(center) }
                mainClock.advanceTimeBy(400)
                waitForIdle()
                onNodeWithText("调整配页").assertIsDisplayed()
                onNodeWithTag("reader-quick-pairing").assertIsEnabled().performClick()
                onNodeWithText("撤销配页").assertIsDisplayed()
                runOnIdle {
                    assertEquals(mapOf("preview" to listOf(2, 6), "other" to listOf(3)), tools.pairingRestarts)
                    assertEquals(2, reported.last())
                }
                onNodeWithTag("reader-quick-pairing").assertIsEnabled().performClick()
                onNodeWithText("调整配页").assertIsDisplayed()
                runOnIdle {
                    assertEquals(saved, tools.pairingRestarts)
                    assertTrue(reported.all { it == 2 }, "配页调整不能写入临时分组位置：$reported")
                    tools = tools.copy(pageLayout = "SINGLE")
                }
                onNodeWithTag("reader-quick-pairing").assertDoesNotExist()
                onNodeWithText("起始页").assertDoesNotExist()
            } finally { owner.destroy() }
        }
}
