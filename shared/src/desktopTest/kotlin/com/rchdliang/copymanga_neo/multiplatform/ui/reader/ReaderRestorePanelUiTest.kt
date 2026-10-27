package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReaderRestorePanelUiTest {
    @Test fun livePanelKeepsControlsVisibleAndRemembersEachMode() = runSkikoComposeUiTest(size = Size(420f, 360f)) {
        var tools by mutableStateOf(ReaderToolsSettings().withRestoreMode(ReaderRestoreMode.RESTORE))
        var finished by mutableStateOf(false)
        val zoom = ReaderZoomState().apply { updateViewport(IntSize(1000, 800)) }
        val adjustment = ReaderQualityAdjustmentState().apply { begin("page", zoom) }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                if (!finished) ReaderRestoreAdjustmentPanel(tools.restoreSelection(), null, "已修复 · Vulkan",
                    { tools = tools.withRestoreMode(it); adjustment.begin("page", zoom) },
                    { tools = tools.withRestoreLevel(it); adjustment.begin("page", zoom) },
                    { adjustment.finish(); finished = true })
            }
        }
        onNodeWithTag("reader-restore-RESTORE").assertIsOn()
        onNodeWithTag("reader-restore-level").assertIsEnabled()
        runOnIdle { zoom.transform(.4f, zoom.viewportCenter(), Offset(40f, 20f), READER_MAX_SCALE) }
        val observing = zoom.snapshot()
        onNodeWithTag("reader-restore-SOFT").performClick()
        onNodeWithTag("reader-restore-level").performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        runOnIdle { assertEquals(observing, zoom.snapshot()) }
        onNodeWithTag("reader-restore-RESTORE").performClick()
        runOnIdle { assertEquals(observing, zoom.snapshot()) }
        val pixels = onRoot().captureToImage().toPixelMap()
        val png = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) png.setRGB(x, y, pixels[x, y].toArgb())
        val file = File("build/reports/reader-restore-ui/live-panel.png")
        file.parentFile.mkdirs()
        ImageIO.write(png, "png", file)
        onNodeWithTag("reader-restore-OFF").performClick().assertIsOn()
        onNodeWithTag("reader-restore-level").assertIsNotEnabled()
        onNodeWithTag("reader-restore-finish").performClick()
        onNodeWithTag("reader-restore-finish").assertDoesNotExist()
        runOnIdle { assertEquals(observing, zoom.snapshot()) }
    }
}
