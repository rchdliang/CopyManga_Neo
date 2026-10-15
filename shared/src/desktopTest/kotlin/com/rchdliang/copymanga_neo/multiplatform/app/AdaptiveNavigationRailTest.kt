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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AdaptiveNavigationRailTest {
    @Test fun normalHeightKeepsEveryDestinationVisible() = checkRail(720f, 1f, false, false)
    @Test fun shortHeightCompressesWhitespaceBeforeScrolling() = checkRail(400f, 1f, true, false)
    @Test fun tinyHeightCanScrollToEveryDestination() = checkRail(200f, 1f, true, true)
    @Test fun enlargedLabelsRemainReachable() = checkRail(300f, 2f, false, true)

    private fun checkRail(height: Float, fontScale: Float, dark: Boolean, needsScroll: Boolean) =
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
            for (tab in MainTab.entries) {
                val node = onNode(hasText(tab.title) and isSelectable())
                if (needsScroll) node.performScrollTo()
                node.assertIsDisplayed().performClick().assertIsSelected()
                val full = node.getUnclippedBoundsInRoot()
                val clipped = node.getBoundsInRoot()
                assertTrue((full.bottom - full.top).value >= 48f, "Navigation touch targets must not shrink")
                assertTrue(kotlin.math.abs((full.bottom - full.top).value - (clipped.bottom - clipped.top).value) < 1f,
                    "Entire destination must be reachable: ${tab.title}")
            }
            onNodeWithContentDescription("展开侧栏").performScrollTo().performClick()
            waitForIdle()
            for (tab in MainTab.entries) {
                val node = onNode(hasText(tab.title) and isSelectable()).performScrollTo()
                    .assertIsDisplayed().performClick().assertIsSelected()
                val full = node.getUnclippedBoundsInRoot()
                val clipped = node.getBoundsInRoot()
                assertTrue((full.bottom - full.top).value >= 48f)
                assertTrue(kotlin.math.abs((full.bottom - full.top).value - (clipped.bottom - clipped.top).value) < 1f)
            }
            onNodeWithContentDescription("折叠侧栏").performScrollTo().assertIsDisplayed()
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
        runOnIdle { height = 200.dp }
        onNode(hasText("我的") and isSelectable()).performScrollTo().assertIsDisplayed()
        runOnIdle { height = 720.dp }
        waitForIdle()
        MainTab.entries.forEach { onNode(hasText(it.title) and isSelectable()).assertIsDisplayed() }
        onNodeWithContentDescription("展开侧栏").assertIsDisplayed()
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
