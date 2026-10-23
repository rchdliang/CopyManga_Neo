package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.intercept.Interceptor
import coil3.request.*
import coil3.size.Dimension
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class WindowsReaderImageQualityTest {
    private fun page(width: Int = 836, height: Int = 1200): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width)
            image.setRGB(x, y, if ((x + y) % 2 == 0) 0xffffff else 0)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun options(width: Int, height: Int) = Options(
        PlatformContext.INSTANCE, size = Size(width, height), scale = Scale.FIT, precision = Precision.INEXACT,
    )

    @Test
    fun downsamplingAveragesFineLinesInsteadOfPickingNearestPixels() {
        val result = decodeWindowsReaderImage(page(64, 64), options(25, 25))
        (result.image as BitmapImage).bitmap.use { bitmap ->
            assertTrue(result.isSampled)
            val midtones = (2 until 23).sumOf { y ->
                (2 until 23).count { x ->
                    (bitmap.getColor(x, y) and 255) in 16..239
                }
            }
            assertTrue(midtones > 400, "细线缩小应产生过渡灰度，最近邻只会留下黑白跳变")
        }
    }

    @Test
    fun zoomDecodesMoreSourceDetailButNeverUpscalesTheBitmap() {
        val bytes = page()
        val small = decodeWindowsReaderImage(bytes, options(1185, 784))
        val zoom = decodeWindowsReaderImage(bytes, options(2963, 1960))
        try {
            assertEquals(546, small.image.width); assertEquals(784, small.image.height)
            assertTrue(small.isSampled)
            assertEquals(836, zoom.image.width); assertEquals(1200, zoom.image.height)
            assertFalse(zoom.isSampled)
        } finally {
            (small.image as BitmapImage).bitmap.close(); (zoom.image as BitmapImage).bitmap.close()
        }
    }

    @Test
    fun decoderHonorsMaximumBitmapDimensions() {
        val request =
            ImageRequest.Builder(PlatformContext.INSTANCE).data("unused").maxBitmapSize(Size(128, 128)).build()
        val result = decodeWindowsReaderImage(page(), options(5000, 5000).copy(extras = request.extras))
        try {
            assertTrue(result.image.width <= 128); assertTrue(result.image.height <= 128)
        } finally {
            (result.image as BitmapImage).bitmap.close()
        }
    }

    @Test
    fun continuousReaderKeepsUnboundedHeightAndUsesPhysicalPixels() {
        val size = readerDecodeSize(Constraints(maxWidth = 600), 2.5f)
        assertEquals(Dimension.Pixels(1500), size.width)
        assertEquals(Dimension.Undefined, size.height)
        assertEquals(Size(1000, 1500), readerDecodeSize(Constraints.fixed(400, 600), 2.5f))
    }

    @Test
    fun realReaderReloadsOnZoomAndResizeWithoutChangingDiskIdentity() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val file = Files.createTempFile("reader-quality-", ".png")
        Files.write(file, page())
        val loaded = mutableListOf<SuccessResult>()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).coroutineContext(dispatcher)
            .components {
                add(object : Interceptor {
                    override suspend fun intercept(chain: Interceptor.Chain): ImageResult = chain.proceed().also {
                        if (it is SuccessResult) loaded += it
                    }
                })
            }.build()
        val zoom = ReaderZoomState()
        var width by mutableStateOf(400.dp)
        var scene: ImageComposeScene? = null
        try {
            val raster = ImageComposeScene(800, 600, coroutineContext = coroutineContext) {
                Box(Modifier.width(width)) {
                    ReaderImageItem(
                        0, file.toString(), ContentScale.Fit, false, true, true,
                        zoomState = zoom, imageLoader = loader, tools = ReaderToolsSettings(),
                        imageQuality = windowsReaderImageQuality
                    )
                }
            }
            scene = raster
            var time = 0L
            fun frames() {
                repeat(5) { runCurrent(); time += 16_000_000; raster.render(time).close(); runCurrent() }
            }
            frames()
            val initial = loaded.last()
            assertEquals(400, initial.image.width)
            width = 600.dp
            frames()
            assertTrue(loaded.last().image.width > initial.image.width, "窗口变大须提高解码尺寸")
            zoom.toggleZoom(Offset(300f, 300f), 5f)
            frames(); advanceTimeBy(150); frames()
            assertEquals(836, loaded.last().image.width)
            assertEquals(1200, loaded.last().image.height)
            assertFalse(loaded.last().isSampled)
            assertEquals(1, loaded.map { it.request.data }.distinct().size)
            assertTrue(loaded.all { it.memoryCacheKey?.extras?.get("reader_sampling") == "windows-cubic-v1" })
        } finally {
            scene?.close(); loader.shutdown(); Dispatchers.resetMain(); Files.deleteIfExists(file)
        }
    }
}
