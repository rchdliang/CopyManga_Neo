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
import androidx.compose.runtime.mutableStateOf
import com.rchdliang.copymanga_neo.domain.model.ReaderInfoItem
import com.rchdliang.copymanga_neo.domain.model.ReaderInfoPosition
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlinx.serialization.json.Json

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
    @Test fun customInfoEditsUpdatePreviewAndPersist() = runSkikoComposeUiTest(size = Size(480f, 1000f)) {
        val tools = mutableStateOf(Json.decodeFromString<ReaderToolsSettings>("""{"pageAnimation":false}"""))
        setContent {
            MaterialTheme {
                ReaderSettingsDialog(
                    ReadingDirection.RIGHT_TO_LEFT, PageScale.FIT_SCREEN, ScreenOrientationSetting.SYSTEM_DEFAULT,
                    {}, {}, {}, {}, infoSettings = {
                        ReaderInfoOptions(tools.value.readingInfo) { change -> tools.value = change(tools.value) }
                    }
                )
            }
        }
        onNodeWithText("阅读信息").performScrollTo().performClick()
        fun choose(menu: String, option: String) {
            onNodeWithTag("reader-menu-$menu").performScrollTo().performClick()
            onNodeWithTag("reader-option-$menu-$option").performClick()
        }
        val preview = onNodeWithTag("reader-info-preview").fetchSemanticsNode().boundsInRoot
        fun pageBounds() = onNodeWithText("7 / 24").fetchSemanticsNode().boundsInRoot
        choose("显示位置", "顶部居中")
        val topCenter = pageBounds()
        kotlin.test.assertTrue(kotlin.math.abs(topCenter.center.x - preview.center.x) <= 1f)
        choose("显示位置", "底部居中")
        val bottomCenter = pageBounds()
        kotlin.test.assertTrue(kotlin.math.abs(bottomCenter.center.x - preview.center.x) <= 1f)
        kotlin.test.assertTrue(bottomCenter.top > topCenter.bottom)
        onNodeWithText("7 / 24").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(-60f, -70f)) }
        val free = tools.value.readingInfo[ReaderInfoItem.PAGES]
        assertEquals(ReaderInfoPosition.CUSTOM, free.position)
        kotlin.test.assertTrue(free.horizontalFraction in 0f..0.49f && free.verticalFraction in 0.1f..0.9f)
        assertEquals(tools.value, Json.decodeFromString<ReaderToolsSettings>(Json.encodeToString(tools.value)))
        onNodeWithText("7 / 24").performMouseInput {
            moveTo(center)
            press()
            moveBy(androidx.compose.ui.geometry.Offset(50f, -30f))
            release()
        }
        val moved = tools.value.readingInfo[ReaderInfoItem.PAGES]
        kotlin.test.assertTrue(moved.horizontalFraction > free.horizontalFraction && moved.verticalFraction < free.verticalFraction)
        choose("显示位置", "左上")
        choose("字号", "24 sp")
        choose("描边宽度", "无描边")
        val clock = onNodeWithText("09:41").fetchSemanticsNode().boundsInRoot
        val pages = onNodeWithText("7 / 24").fetchSemanticsNode().boundsInRoot
        assertEquals(clock.left, pages.left)
        kotlin.test.assertTrue(clock.bottom <= pages.top)
        val restored = Json.decodeFromString<ReaderToolsSettings>(Json.encodeToString(tools.value))
        val style = restored.readingInfo[ReaderInfoItem.PAGES]
        assertEquals(ReaderInfoPosition.TOP_LEFT, style.position)
        assertEquals(24, style.fontSizeSp)
        assertEquals(0, style.outlineDp)
        choose("信息项", "时间")
        choose("显示位置", "隐藏")
        onNodeWithText("09:41").assertDoesNotExist()
        onNodeWithText("7 / 24").assertIsDisplayed()
        onNodeWithText("重置全部阅读信息").performScrollTo().performClick()
        onNodeWithText("09:41").assertIsDisplayed()
        assertEquals(ReaderToolsSettings(pageAnimation = false), tools.value)
        onNodeWithText("返回").performClick()
        onNodeWithText("阅读设置").assertIsDisplayed()
    }

    @Test fun missingBatteryDoesNotDisplayAPercentage() = runSkikoComposeUiTest(size=Size(390f,100f)) {
        setContent { MaterialTheme { ReaderInfoOverlay("示例章节", "1 / 24", "00:05", ReaderBatteryStatus()) } }
        onNodeWithText("00:05").assertIsDisplayed()
        onNodeWithContentDescription("电池状态未知").assertIsDisplayed()
        onAllNodes(hasText("%", substring = true)).assertCountEquals(0)
    }
    @OptIn(InternalTestApi::class, InternalComposeUiApi::class)
    @Test fun statusLabelsUseHorizontalSpaceBeforeDroppingBelowTheCutout() {
        fun check(width: Int, height: Int, hole: Rect, clockLeft: Float = 12f, batteryRight: Float = width - 12f,
                  timeTop: Float = 6f, batteryTop: Float = 6f) {
            val insets = object : PlatformWindowInsets {
                override val displayCutouts = listOf(hole)
                override val displayCutout = PlatformInsets(top = hole.bottom.toInt())
            }
            runInternalSkikoComposeUiTest(width = width, height = height, windowInsets = insets) {
                val chapter = "很长的章节名".repeat(20)
                setContent {
                    MaterialTheme {
                        Box(Modifier.fillMaxSize()) {
                            ReaderInfoOverlay(chapter, "112–113 / 240", "09:41", ReaderBatteryStatus(85))
                        }
                    }
                }
                val clock = onNodeWithText("09:41").fetchSemanticsNode().boundsInRoot
                val battery = onNodeWithText("85%").fetchSemanticsNode().boundsInRoot
                assertEquals(timeTop, clock.top, 1f)
                assertEquals(batteryTop, battery.top, 1f)
                assertEquals(clockLeft, clock.left, 1f)
                assertEquals(batteryRight, battery.right, 1f)
                val icon = onNodeWithContentDescription("电池电量").fetchSemanticsNode().boundsInRoot
                val batteryGroup = Rect(icon.left, battery.top, battery.right, battery.bottom)
                kotlin.test.assertFalse(clock.overlaps(batteryGroup))
                kotlin.test.assertFalse(clock.overlaps(hole) || batteryGroup.overlaps(hole))
                val pages = onNodeWithText("112–113 / 240").fetchSemanticsNode().boundsInRoot
                val title = onNodeWithText(chapter).fetchSemanticsNode().boundsInRoot
                kotlin.test.assertTrue(title.right < pages.left && pages.bottom > height / 2)
                onNodeWithText("112–113 / 240").assertTextEquals("112–113 / 240")
                val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                onNodeWithText("112–113 / 240").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
                kotlin.test.assertFalse(layout.single().isLineEllipsized(0))
            }
        }
        check(390, 700, Rect(179f, 0f, 211f, 36f))
        check(390, 700, Rect(76f, 0f, 106f, 36f))
        check(390, 700, Rect(8f, 0f, 42f, 36f), clockLeft = 48f)
        check(390, 700, Rect(348f, 0f, 382f, 36f), batteryRight = 342f)
        // 孔右侧只够放一组，保留本来就放得下的电量。
        check(390, 700, Rect(8f, 0f, 296f, 36f), timeTop = 42f)
        check(390, 700, Rect(70f, 0f, 382f, 36f), batteryTop = 42f)
        check(390, 700, Rect(8f, 0f, 382f, 36f), timeTop = 42f, batteryTop = 42f)
        check(700, 390, Rect(0f, 172f, 36f, 218f))
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
