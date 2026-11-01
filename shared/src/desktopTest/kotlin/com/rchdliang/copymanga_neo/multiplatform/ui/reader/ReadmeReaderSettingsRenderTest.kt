package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/** README 设置图直接渲染生产组件；固定样例不访问账号、网络或 GPU 推理。 */
@OptIn(ExperimentalTestApi::class)
class ReadmeReaderSettingsRenderTest {
    @Test fun captureReadingAndDisplaySettings() = runSkikoComposeUiTest(size = Size(1120f, 1600f)) {
        val tools = ReaderToolsSettings()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                CopyManga_NeoTheme(darkTheme = false, dynamicColor = false) {
                    Surface(Modifier.fillMaxSize()) {
                        ReaderSettingsDialog(
                            ReadingDirection.RIGHT_TO_LEFT, PageScale.FIT_SCREEN, ScreenOrientationSetting.SYSTEM_DEFAULT,
                            {}, {}, {}, {},
                            leadingSettings = { ReaderSpreadOptions(tools, {}, "sample", 4, 31, true, false, "DOUBLE", {}) },
                            turnSettings = { ReaderToolsOptions(tools, {}) },
                            loadingSettings = { ReaderLoadingOptions(tools, {}) },
                            pairingSettings = { ReaderPairingOption(tools, {}, "sample", 4, 31) },
                            helpSettings = { ReaderOperationHelp() },
                            onQualitySelected = {}
                        )
                    }
                }
            }
        }
        onNodeWithText("阅读设置").assertIsDisplayed()
        onNodeWithText("画质增强").assertIsDisplayed()
        onNodeWithText("本章配页").assertIsDisplayed()
        onAllNodes(isRoot())[1].saveReadmeImage("reader-settings")
        onNodeWithText("显示设置").performClick()
        onNodeWithText("双页").assertIsDisplayed()
        onNodeWithText("重置显示设置").assertIsDisplayed()
        onAllNodes(isRoot())[1].saveReadmeImage("reader-display")
    }

    @Test fun captureCurrentVulkanQualityControls() = runSkikoComposeUiTest(size = Size(1120f, 1400f)) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                CopyManga_NeoTheme(darkTheme = false, dynamicColor = false) {
                    var tools by remember { mutableStateOf(ReaderToolsSettings().withRestoreMode(ReaderRestoreMode.RESTORE)) }
                    ReaderQualityPreviewPanel({}, Modifier.fillMaxWidth().testTag("readme-quality-panel")) {
                        ReaderQualityControls(tools, null, null, { tools = it(tools) })
                    }
                }
            }
        }
        onNodeWithTag("reader-restore-RESTORE").assertIsOn()
        onNodeWithTag("reader-restore-level").assertIsEnabled()
        onNodeWithText("画质修复开启时暂停锐化").assertIsDisplayed()
        onNodeWithText("返回").assertIsDisplayed()
        onNodeWithTag("readme-quality-panel").saveReadmeImage("reader-quality")
    }

    private fun SemanticsNodeInteraction.saveReadmeImage(name: String) {
        val pixels = captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x, y, pixels[x, y].toArgb())
        val file = File("build/reports/readme-settings/$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }
}
