package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.multiplatform.app.RouteLifecycleOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ReaderQualityPreviewFlowUiTest {
    @Test fun menuEntersSinglePreviewAndReturnsWithoutApplyingAgain() = runSkikoComposeUiTest(size = Size(1100f, 750f)) {
        val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
        var tools by mutableStateOf(ReaderToolsSettings(sharpness = .4f))
        try {
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalInspectionMode provides true) {
                    CopyManga_NeoTheme {
                        ReaderSuccessContent(images = listOf("fixture"), chapterName = "Fixture",
                            readingDirection = "RIGHT_TO_LEFT", pageScale = "FIT_SCREEN", screenOrientation = "SYSTEM_DEFAULT",
                            onPrevChapter = {}, onNextChapter = {}, hasPrevChapter = false, hasNextChapter = false,
                            onRefresh = {}, onReadingDirectionChange = {}, onPageScaleChange = {}, onScreenOrientationChange = {},
                            pendingInitialPage = remember { mutableStateOf(0) }, tools = tools, onToolsChange = { tools = it(tools) },
                            modifier = Modifier.testTag("reader"))
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("reader").performMouseInput { rightClick(Offset(550f, 350f)) }
            onNodeWithText("阅读设置").assertIsDisplayed()
            onNodeWithText("画质增强").performClick()
            onNodeWithText("阅读设置").assertDoesNotExist()
            onAllNodesWithText("画质增强").assertCountEquals(1)
            onNodeWithTag("reader-quality-back").assertIsDisplayed()
            onNodeWithText("拖动画面查看细节").assertDoesNotExist()
            onNodeWithText("完成").assertDoesNotExist()
            onNodeWithTag("reader-sharpen-strength").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(.6f) }
            runOnIdle { assertEquals(.6f, tools.sharpness, .001f) }
            onNodeWithTag("reader-quality-back").performClick()
            onNodeWithText("阅读设置").assertIsDisplayed()
            onNodeWithTag("reader-quality-back").assertDoesNotExist()
            onNodeWithText("画质增强").performClick()
            onNodeWithText("强度 60%").performScrollTo().assertIsDisplayed()
        } finally { owner.destroy() }
    }
}
