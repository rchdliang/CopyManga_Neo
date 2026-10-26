package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlin.test.*

/** 仅验证生产设置控件；不把桌面 UI 测试当成 Android GPU 验收。 */
@OptIn(ExperimentalTestApi::class)
class ReaderSettingsUiTest {
    @Composable private fun Settings(onDismiss: () -> Unit = {}) {
        var tools by remember { mutableStateOf(ReaderToolsSettings()) }
        var layout by remember { mutableStateOf("AUTO") }
        var restore by remember { mutableStateOf(ReaderRestoreMode.OFF) }
        MaterialTheme {
            ReaderSettingsDialog(ReadingDirection.RIGHT_TO_LEFT, PageScale.FIT_SCREEN,
                ScreenOrientationSetting.SYSTEM_DEFAULT, {}, {}, {}, onDismiss,
                onResetPreferences = { layout = "SINGLE" },
                leadingSettings = { ReaderSpreadOptions(tools, { tools = it(tools) }, "chapter", 2, 20, true, false, layout, { layout = it }) },
                qualitySettings = {
                    ReaderQualitySection {
                        ReaderSharpnessOptions(tools.sharpness, tools.sharpnessRestore, { tools = tools.copy(sharpness = 0f) }) {
                            tools = tools.copy(sharpness = it)
                        }
                    }
                    ReaderQualitySection { ReaderRestoreControls(restore, null, null, { restore = it }, {}) }
                },
                turnSettings = { ReaderToolsOptions(tools, { tools = it(tools) }) },
                loadingSettings = { ReaderLoadingOptions(tools, { tools = it(tools) }) },
                pairingSettings = { ReaderPairingOption(tools, { tools = it(tools) }, "chapter", 2, 20) },
                helpSettings = { ReaderOperationHelp() })
        }
    }

    @Test fun backReturnsFromQualityBeforeClosingDialog() = runSkikoComposeUiTest(size = Size(520f, 900f)) {
        var open by mutableStateOf(true)
        setContent { if (open) Settings { open = false } }
        onNodeWithText("画质增强").performClick()
        onNodeWithTag("reader-restore-SOFT").performScrollTo().performClick().assertIsSelected()
        onNodeWithText("返回").performClick()
        onNodeWithText("阅读设置").assertExists()
        onNodeWithText("完成").performClick()
        onNodeWithText("阅读设置").assertDoesNotExist()
    }

    @Test fun sharpnessUsesFivePercentSteps() = runSkikoComposeUiTest(size = Size(520f, 900f)) {
        setContent { Settings() }
        onNodeWithText("画质增强").performClick()
        onNodeWithTag("reader-sharpen-switch").performScrollTo().performClick()
        val slider = onNodeWithTag("reader-sharpen-strength").performScrollTo()
        assertEquals(19, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].steps)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        onNodeWithText("强度 50%").assertExists()
        onNodeWithText("恢复默认").performClick()
        onNodeWithTag("reader-sharpen-switch").assertIsOff()
    }

    @Test fun narrowSettingsAndPairingRejectInvalidInput() = runSkikoComposeUiTest(size = Size(390f, 760f)) {
        setContent { Settings() }
        onNodeWithText("显示设置").performClick()
        onNodeWithTag("reader-option-页面布局-双页").performClick()
        onNodeWithText("双页").assertExists()
        onNodeWithText("返回").performClick()
        onNodeWithText("本章配页").performScrollTo().performClick()
        onNodeWithText("起始页").performTextReplacement("0")
        onNodeWithText("请输入 1–19 的页码").assertExists()
        onNodeWithText("应用配页").assertIsNotEnabled()
    }

    @Test fun unavailableRestoreKeepsOriginalAndSharpnessChoices() = runSkikoComposeUiTest(size = Size(520f, 300f)) {
        var choice by mutableStateOf(ReaderQualityChoice.ORIGINAL)
        setContent { MaterialTheme { ReaderQualityComparison(choice, false, { choice = it }, {}) } }
        onNodeWithTag("reader-quality-RESTORE").assertIsNotEnabled()
        onNodeWithTag("reader-quality-SOFT").assertIsNotEnabled()
        onNodeWithTag("reader-quality-SHARPEN").performClick().assertIsSelected()
        assertEquals(ReaderQualityChoice.SHARPEN, choice)
    }
}
