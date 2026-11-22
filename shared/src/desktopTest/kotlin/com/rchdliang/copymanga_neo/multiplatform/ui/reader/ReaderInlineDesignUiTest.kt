package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.test.v2.runInternalSkikoComposeUiTest
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ReaderInlineDesignUiTest {
    @Test fun displayNarrow() = runSkikoComposeUiTest(size = Size(390f, 800f)) {
        setContent { ReaderInlineDesignContent(false) }
        onNodeWithTag("reader-option-页面布局-单页").performClick().assertIsSelected()
        onNodeWithText("显示设置").assertExists()
        capture("display-narrow")
        onNodeWithTag("reader-menu-屏幕方向").performClick()
        onNodeWithTag("reader-option-屏幕方向-横屏").performClick()
        onNodeWithText("横屏").assertExists()
    }
    @Test fun displayWideDark() = runSkikoComposeUiTest(size = Size(560f, 800f)) {
        setContent { ReaderInlineDesignContent(false, true) }; capture("display-wide-dark")
    }
    @Test fun qualityNarrow() = runSkikoComposeUiTest(size = Size(390f, 800f)) {
        setContent { ReaderInlineDesignContent(true) }
        onNodeWithTag("reader-restore-RESTORE").performScrollTo().performClick().assertIsOn()
        onNodeWithText("画质增强").assertExists(); capture("quality-narrow")
    }
    @Test fun qualityWideDark() = runSkikoComposeUiTest(size = Size(560f, 800f)) {
        setContent { ReaderInlineDesignContent(true, true) }; capture("quality-wide-dark")
    }
    @Test fun menuCancelAndNarrowFallbackDoNotChangeTheChoice() = runSkikoComposeUiTest(size = Size(260f,600f)) {
        var selected = "单页"
        setContent { MaterialTheme { ReaderInlineChoice(Icons.Outlined.ViewWeek, "页面布局", selected,
            listOf("单页","双页","自动"), {it}, Modifier.width(180.dp)) {selected=it} } }
        onNodeWithTag("reader-segments-页面布局").assertDoesNotExist()
        onNodeWithTag("reader-menu-页面布局").performClick()
        onNodeWithTag("reader-option-页面布局-双页").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        onNodeWithTag("reader-option-页面布局-双页").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("reader-option-页面布局-双页").assertDoesNotExist()
        assertEquals("单页",selected)
    }
    @Test fun restingOverlayShowsChapterAndPagesWithoutRepairStatus() = runSkikoComposeUiTest(size=Size(390f,420f)) {
        setContent { ReaderRestingOverlayContent() }
        onNodeWithText("示例章节").assertExists()
        onNodeWithText("7 / 24").assertExists()
        onNodeWithText("09:41").assertIsDisplayed()
        onNodeWithText("85%").assertIsDisplayed()
        val time = onNodeWithText("09:41").fetchSemanticsNode().boundsInRoot
        val battery = onNodeWithText("85%").fetchSemanticsNode().boundsInRoot
        val pages = onNodeWithText("7 / 24").fetchSemanticsNode().boundsInRoot
        kotlin.test.assertTrue(time.right < battery.left)
        kotlin.test.assertTrue(kotlin.math.abs(time.center.y - battery.center.y) < 1f)
        onNodeWithContentDescription("电池电量").assertIsDisplayed()
        val chapter = onNodeWithText("示例章节").fetchSemanticsNode().boundsInRoot
        kotlin.test.assertTrue(kotlin.math.abs(chapter.center.y - pages.center.y) < 1f)
        kotlin.test.assertTrue(chapter.right < pages.left && time.bottom < pages.top)
        kotlin.test.assertTrue(kotlin.math.abs(pages.right - battery.right) < 1f)
        onNodeWithText("画质修复已应用").assertDoesNotExist()
        capture("resting-overlay")
    }
    @Test fun missingBatteryDoesNotDisplayAPercentage() = runSkikoComposeUiTest(size=Size(390f,100f)) {
        setContent { MaterialTheme { ReaderStatusOverlay("00:05", ReaderBatteryStatus()) } }
        onNodeWithText("00:05").assertIsDisplayed()
        onNodeWithContentDescription("电池状态未知").assertIsDisplayed()
        onAllNodes(hasText("%", substring = true)).assertCountEquals(0)
    }
    @OptIn(InternalTestApi::class, InternalComposeUiApi::class)
    @Test fun onlyStatusLabelsOverlappingTheCutoutMoveDown() {
        fun check(width: Int, height: Int, hole: Rect, timeTop: Float, batteryTop: Float) {
            val insets = object : PlatformWindowInsets {
                override val displayCutouts = listOf(hole)
                override val displayCutout = PlatformInsets(top = hole.bottom.toInt())
            }
            runInternalSkikoComposeUiTest(width = width, height = height, windowInsets = insets) {
                val chapter = "很长的章节名".repeat(20)
                setContent {
                    MaterialTheme {
                        Box(Modifier.fillMaxSize()) {
                            ReaderStatusOverlay("09:41", ReaderBatteryStatus(85))
                            ReaderRestingOverlay(chapter, "112–113 / 240", Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                        }
                    }
                }
                val clock = onNodeWithText("09:41").fetchSemanticsNode().boundsInRoot
                val battery = onNodeWithText("85%").fetchSemanticsNode().boundsInRoot
                assertEquals(timeTop, clock.top, 1f)
                assertEquals(batteryTop, battery.top, 1f)
                val pages = onNodeWithText("112–113 / 240").fetchSemanticsNode().boundsInRoot
                val title = onNodeWithText(chapter).fetchSemanticsNode().boundsInRoot
                kotlin.test.assertTrue(title.right < pages.left && pages.bottom > height / 2)
                onNodeWithText("112–113 / 240").assertTextEquals("112–113 / 240")
                val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                onNodeWithText("112–113 / 240").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
                kotlin.test.assertFalse(layout.single().isLineEllipsized(0))
            }
        }
        check(390, 700, Rect(179f, 0f, 211f, 36f), 6f, 6f)
        check(390, 700, Rect(8f, 0f, 42f, 36f), 42f, 6f)
        check(390, 700, Rect(348f, 0f, 382f, 36f), 6f, 42f)
        check(700, 390, Rect(0f, 172f, 36f, 218f), 6f, 6f)
    }
    @Test fun batteryIconVariantsRemainVisibleAcrossReaderBackgrounds() = runSkikoComposeUiTest(size=Size(780f,180f)) {
        val batteries = listOf(0, 15, 29, 43, 58, 72, 86, 100).map { ReaderBatteryStatus(it) } +
            listOf(ReaderBatteryStatus(52, charging = true), ReaderBatteryStatus())
        setContent {
            MaterialTheme {
                Column {
                    for (background in listOf(Color.Black, Color.Gray, Color.White)) {
                        Row(Modifier.fillMaxWidth().background(background).padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            batteries.forEach { ReaderBatteryIndicator(it) }
                        }
                    }
                }
            }
        }
        onAllNodesWithContentDescription("电池正在充电").assertCountEquals(3)
        onAllNodesWithContentDescription("电池状态未知").assertCountEquals(3)
        capture("battery-icon-variants")
    }
    private fun ComposeUiTest.capture(name: String) {
        val pixels = onRoot().captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x,y,pixels[x,y].toArgb())
        val file = File("build/reports/reader-inline-design/$name.png"); file.parentFile.mkdirs(); ImageIO.write(image,"png",file)
    }
}
