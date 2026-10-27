package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.width
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
    @Test fun actualDisplayStatusUsesOutlinedOverlayWithoutScrim() = runSkikoComposeUiTest(size=Size(390f,420f)) {
        setContent { ReaderAiDisplayStates() }
        onNodeWithText("画质修复已应用").assertExists()
        capture("actual-display-states")
    }
    private fun ComposeUiTest.capture(name: String) {
        val pixels = onRoot().captureToImage().toPixelMap()
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x,y,pixels[x,y].toArgb())
        val file = File("build/reports/reader-inline-design/$name.png"); file.parentFile.mkdirs(); ImageIO.write(image,"png",file)
    }
}
