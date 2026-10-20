package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AdaptiveNavigationRailTest {
    @Test fun normalHeightKeepsEveryDestinationVisible() = checkRail(720f, 1f, false, false)
    @Test fun shortHeightHidesHeaderBeforeCollectingDestinations() = checkRail(400f, 1f, true, false)
    @Test fun tinyHeightCollectsDestinationsInMoreMenu() = checkRail(200f, 1f, true, true)
    @Test fun enlargedLabelsRemainReachable() = checkRail(300f, 2f, false, true)

    private fun checkRail(height: Float, fontScale: Float, dark: Boolean, hasOverflow: Boolean) =
        runSkikoComposeUiTest(size = Size(1000f, height)) {
            var selected by mutableStateOf(MainTab.HOME)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                        MainNavigationShell(selected, { selected = it }, false, null, 2) { Box(Modifier.fillMaxSize()) }
                    }
                }
            }
            saveRender("${height.toInt()}-${fontScale}-${if (dark) "dark" else "light"}")
            if (hasOverflow) onNodeWithContentDescription("更多导航").assertIsDisplayed()
            else onNodeWithContentDescription("更多导航").assertDoesNotExist()
            for (tab in MainTab.entries) {
                val matcher = hasText(tab.title) and isSelectable()
                if (onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) {
                    val node = onNode(matcher).assertIsDisplayed().performClick().assertIsSelected()
                    val full = node.getUnclippedBoundsInRoot()
                    val clipped = node.getBoundsInRoot()
                    assertTrue((full.bottom - full.top).value >= 48f, "Navigation touch targets must not shrink")
                    assertTrue(kotlin.math.abs((full.bottom - full.top).value - (clipped.bottom - clipped.top).value) < 1f)
                } else {
                    onNodeWithContentDescription("更多导航").performClick()
                    onNodeWithText(tab.title).assertIsDisplayed().performClick()
                }
                runOnIdle { assertEquals(tab, selected) }
            }
            if (height < 480f) {
                onNodeWithContentDescription("展开侧栏").assertDoesNotExist()
                return@runSkikoComposeUiTest
            }
            onNodeWithContentDescription("展开侧栏").performClick()
            waitForIdle()
            saveRender("${height.toInt()}-${fontScale}-${if (dark) "dark" else "light"}-expanded")
            for (tab in MainTab.entries) {
                val node = onNode(hasText(tab.title) and isSelectable())
                    .assertIsDisplayed().performClick().assertIsSelected()
                val full = node.getUnclippedBoundsInRoot()
                val clipped = node.getBoundsInRoot()
                assertTrue((full.bottom - full.top).value >= 48f)
                assertTrue(kotlin.math.abs((full.bottom - full.top).value - (clipped.bottom - clipped.top).value) < 1f)
            }
            onNodeWithContentDescription("折叠侧栏").assertIsDisplayed()
        }

    @Test fun shrinkingAndGrowingWindowRestoresAllDestinations() = runSkikoComposeUiTest(size = Size(1000f, 720f)) {
        var height by mutableStateOf(720.dp)
        setContent {
            MaterialTheme {
                Box(Modifier.height(height)) {
                    MainNavigationShell(MainTab.HOME, {}, false, null, 0) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        onNodeWithContentDescription("展开侧栏").performClick()
        waitForIdle()
        runOnIdle { height = 200.dp }
        waitForIdle()
        onNodeWithContentDescription("折叠侧栏").assertDoesNotExist()
        onNodeWithContentDescription("更多导航").assertIsDisplayed().performClick()
        onNodeWithText("我的").assertIsDisplayed().performClick()
        runOnIdle { height = 720.dp }
        waitForIdle()
        MainTab.entries.forEach { onNode(hasText(it.title) and isSelectable()).assertIsDisplayed() }
        onNodeWithContentDescription("展开侧栏").assertIsDisplayed()
        onNodeWithContentDescription("更多导航").assertDoesNotExist()
    }

    @Test fun expansionAndCollapseKeepIntermediateAnimationFrames() = runSkikoComposeUiTest(size = Size(1000f, 720f)) {
        setContent {
            MaterialTheme {
                MainNavigationShell(MainTab.HOME, {}, false, null, 0) {
                    Box(Modifier.fillMaxSize().testTag("content"))
                }
            }
        }
        val collapsed = onNodeWithTag("content").getBoundsInRoot().left.value
        fun labelIconGap(): Float {
            val label = onNode(hasText("首页") and !isSelectable(), useUnmergedTree = true).getBoundsInRoot()
            val icon = onNodeWithContentDescription("首页", useUnmergedTree = true).getBoundsInRoot()
            return (label.top.value + label.bottom.value - icon.top.value - icon.bottom.value) / 2f
        }
        val collapsedGap = labelIconGap()
        mainClock.autoAdvance = false
        onNodeWithContentDescription("展开侧栏").performClick()
        // 原生动画先淡出文字，再切换文字位置；在宽度尚未完成时检查这一阶段。
        mainClock.advanceTimeBy(160)
        val expanding = onNodeWithTag("content").getBoundsInRoot().left.value
        val expandingGap = labelIconGap()
        saveRender("animation-expanding")
        mainClock.advanceTimeBy(2000)
        val expanded = onNodeWithTag("content").getBoundsInRoot().left.value
        saveRender("animation-expanded")
        assertTrue(expanding > collapsed && expanding < expanded, "Expansion must retain intermediate widths")
        assertTrue(expandingGap < collapsedGap,
            "Icon and label must transition before expansion completes: $expandingGap / $collapsedGap")
        onNodeWithContentDescription("折叠侧栏").performClick()
        mainClock.advanceTimeBy(160)
        val collapsing = onNodeWithTag("content").getBoundsInRoot().left.value
        val collapsingGap = labelIconGap()
        saveRender("animation-collapsing")
        mainClock.advanceTimeBy(2000)
        assertTrue(collapsing > collapsed && collapsing < expanded, "Collapse must retain intermediate widths")
        assertTrue(collapsingGap > 0f,
            "Icon and label must transition before collapse completes: $collapsingGap")
        assertTrue(kotlin.math.abs(onNodeWithTag("content").getBoundsInRoot().left.value - collapsed) < 1f)
    }

    private fun ComposeUiTest.saveRender(name: String) {
        val pixels = onRoot().captureToImage().toPixelMap()
        val rendered = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) rendered.setRGB(x, y, pixels[x, y].toArgb())
        val output = File("build/reports/navigation-rail/$name.png")
        output.parentFile.mkdirs()
        ImageIO.write(rendered, "png", output)
    }
}
