package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NavigationAvatarTest {
    @Test fun loggedOutAvatarFollowsLightAndDarkTheme() = runSkikoComposeUiTest {
        var dark by mutableStateOf(false)
        setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    // A stale saved URL must never be displayed while logged out.
                    NavigationAvatar(false, "https://invalid.example/old-avatar", Modifier.size(36.dp))
                }
            }
        }
        fun assertTint(expected: Color) {
            val pixels = onNodeWithContentDescription("用户头像").captureToImage().toPixelMap()
            var matchingPixels = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (kotlin.math.abs(color.red - expected.red) < 0.02f &&
                    kotlin.math.abs(color.green - expected.green) < 0.02f &&
                    kotlin.math.abs(color.blue - expected.blue) < 0.02f) matchingPixels++
            }
            val rendered = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) rendered.setRGB(x, y, pixels[x, y].toArgb())
            val output = File("build/reports/avatar-theme/${if (dark) "dark" else "light"}.png")
            output.parentFile.mkdirs()
            ImageIO.write(rendered, "png", output)
            assertTrue(matchingPixels > 100, "Expected an opaque, theme-colored avatar glyph; found $matchingPixels pixels")
        }
        assertTint(lightColorScheme().onSurface)
        runOnIdle { dark = true }
        waitForIdle()
        assertTint(darkColorScheme().onSurface)
    }

    @Test fun missingMemberAvatarUsesSameAccessibleFallback() = runSkikoComposeUiTest {
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                NavigationAvatar(true, null, Modifier.size(36.dp))
            }
        }
        onNodeWithContentDescription("用户头像").assertIsDisplayed()
    }
}
