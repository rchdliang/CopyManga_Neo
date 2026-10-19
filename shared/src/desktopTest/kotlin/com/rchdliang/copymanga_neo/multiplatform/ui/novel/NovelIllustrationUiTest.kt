package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NovelIllustrationUiTest {
    private val image = NovelSection("image-a", "插图 A", 2, -1, -1, "https://unused.test/a.png")
    private val volume = NovelReadingVolume(NovelBook("book", "小说", "", emptyList()),
        NovelVolume("volume", "第一卷", 1, null, null), listOf(image),
        NovelTextDocument.decode("text".encodeToByteArray(), "utf-8"), emptyMap(), false)

    @Test fun blurChangesOnlyImagePixelsAndLeavesCaptionClear() = runSkikoComposeUiTest(size = Size(400f, 500f)) {
        var blurred by mutableStateOf(false)
        var exported = false
        setContent {
            CompositionLocalProvider(LocalInspectionMode provides true, LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Surface {
                        Column(Modifier.fillMaxSize()) {
                            NovelReaderImageHeader(1, blurred, { blurred = it }, false, { exported = true })
                            NovelReaderImageGrid(volume, volume.sections, blurred) {}
                        }
                    }
                }
            }
        }
        val clear = onRoot().captureToImage().toPixelMap()
        val caption = onNodeWithText("插图 A", useUnmergedTree = true).getBoundsInRoot()
        onNodeWithContentDescription("模糊本书插图预览").performClick()
        onNodeWithContentDescription("关闭本书插图预览模糊").assertIsDisplayed()
        val blurredPixels = onRoot().captureToImage().toPixelMap()
        for ((name, pixels) in listOf("preview-blur" to blurredPixels, "preview-clear" to clear)) {
            val target = File("build/reports/novel-illustrations/$name.png")
            target.parentFile.mkdirs()
            val bitmap = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until pixels.height) for (x in 0 until pixels.width)
                bitmap.setRGB(x, y, pixels[x, y].toArgb())
            ImageIO.write(bitmap, "png", target)
        }
        var differences = 0
        for (y in 100 until 220) for (x in 35 until 145) {
            if (clear[x, y] != blurredPixels[x, y]) differences++
        }
        assertTrue(differences > 100)
        var maximumDifference = 0f
        for (y in caption.top.value.toInt() until caption.bottom.value.toInt()) {
            for (x in caption.left.value.toInt() until caption.right.value.toInt()) {
                val before = clear[x, y]
                val after = blurredPixels[x, y]
                val difference = maxOf(kotlin.math.abs(before.red - after.red),
                    kotlin.math.abs(before.green - after.green), kotlin.math.abs(before.blue - after.blue))
                maximumDifference = maxOf(maximumDifference, difference)
            }
        }
        assertTrue(maximumDifference <= 2f / 255f + .00001f, "Caption difference: $maximumDifference")
        onNodeWithContentDescription("打包导出本卷全部插图").performClick()
        assertTrue(exported)
        onNodeWithContentDescription("关闭").assertDoesNotExist()
    }

    @Test fun gallerySavesSelectedImageAndHasNoResetAction() = runSkikoComposeUiTest(size = Size(640f, 500f)) {
        var saved: NovelSection? = null
        setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme {
                    NovelReaderGallery(volume, volume.sections, image.key, onSave = { saved = it }) {}
                }
            }
        }
        onNodeWithContentDescription("另存为当前插图").performClick()
        assertEquals(image, saved)
        onNodeWithContentDescription("重置缩放").assertDoesNotExist()
    }
}
