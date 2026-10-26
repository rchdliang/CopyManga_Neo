package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.foundation.layout.fillMaxSize
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.math.abs
import kotlin.test.*

class ReaderEnhancementTest {
    private fun fixture(color: (Int, Int) -> Int): ByteArray {
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 32) for (x in 0 until 32) image.setRGB(x, y, color(x, y))
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
    private fun render(bytes: ByteArray, strength: Float, inspect: (Bitmap) -> Unit) {
        Image.makeFromEncoded(bytes).use { source ->
            Bitmap().use { output ->
                check(output.allocN32Pixels(source.width, source.height)); output.erase(0)
                createReaderSharpenFilter(strength).use { effect ->
                    Paint().use { paint ->
                        paint.imageFilter = effect
                        Canvas(output).use { it.drawImage(source, 0f, 0f, paint) }
                    }
                }
                inspect(output)
            }
        }
    }
    private fun gray(value: Int) = (0xff000000L or (value.toLong() * 0x010101)).toInt()

    @Test fun defaultsAndSurfaceBudgetAreConservative() {
        assertEquals(0f, normalizedReaderSharpness(Float.NaN))
        assertEquals(0f, normalizedReaderSharpness(Float.POSITIVE_INFINITY))
        assertEquals(0f, normalizedReaderSharpness(-1f))
        assertEquals(1f, normalizedReaderSharpness(2f))
        assertTrue(readerSharpenSurfaceAllowed(2048, 2048))
        assertTrue(readerSharpenSurfaceAllowed(2032, 3048))
        assertTrue(readerSharpenSurfaceAllowed(3048, 2032))
        assertTrue(readerSharpenSurfaceAllowed(3840, 2160))
        assertFalse(readerSharpenSurfaceAllowed(4096, 2160))
        assertFalse(readerSharpenSurfaceAllowed(512, 10000))
        assertFalse(readerSharpenSurfaceAllowed(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test fun zeroStrengthIsPixelIdentical() {
        val bytes = fixture { x, y -> gray((x * 7 + y * 3) % 256) }
        val expected = ImageIO.read(bytes.inputStream())
        render(bytes, 0f) { output ->
            for (y in 0 until 32) for (x in 0 until 32) assertEquals(expected.getRGB(x, y), output.getColor(x, y))
        }
    }

    @Test fun constantAndStrongEdgesAreUnchangedAndDoNotCreateHalo() {
        val bytes = fixture { x, _ -> gray(if (x < 16) 0 else 255) }
        render(bytes, 1f) { output ->
            for (y in 0 until 32) for (x in 0 until 32) assertEquals(gray(if (x < 16) 0 else 255), output.getColor(x, y))
        }
        render(fixture { _, _ -> gray(130) }, 1f) { output ->
            for (y in 0 until 32) for (x in 0 until 32) assertEquals(gray(130), output.getColor(x, y))
        }
    }

    @Test fun moderateEdgesChangeGentlyWithinLocalRange() {
        fun value(x: Int) = when (x % 8) { 0, 1 -> 100; 2 -> 105; 3 -> 135; 4 -> 145; 5 -> 148; else -> 150 }
        val bytes = fixture { x, _ -> gray(value(x)) }
        var changed = 0
        render(bytes, .35f) { output ->
            for (y in 1 until 31) for (x in 1 until 31) {
                val actual = output.getColor(x, y) and 255
                if (actual != value(x)) changed++
                assertTrue(abs(actual - value(x)) <= 11)
                assertTrue(actual in minOf(value(x - 1), value(x), value(x + 1))..maxOf(value(x - 1), value(x), value(x + 1)))
            }
        }
        assertTrue(changed > 0, "The real runtime shader must actually sharpen the moderate edge fixture")
    }

    @Test fun transparentPixelsAndTheirNeighborsArePreserved() {
        val bytes = fixture { x, _ -> if (x < 16) 0 else gray(120 + x % 4 * 8) }
        render(bytes, .35f) { output ->
            assertEquals(0, output.getColor(15, 15))
            assertEquals(gray(120), output.getColor(16, 15))
        }
    }

    @Test fun antialiasedBlackTextEdgesSharpenWithoutOvershootAndRespondToStrength() {
        // 模拟黑字边缘的灰阶过渡；旧算法会因邻域反差超过 0.4 而完全跳过。
        val levels = listOf(0, 0, 32, 96, 192, 240, 255, 255)
        val bytes = fixture { x, _ -> gray(levels[x % levels.size]) }
        var gentleChange = 0
        var strongChange = 0
        for (strength in listOf(.2f, 1f)) render(bytes, strength) { output ->
            for (x in 1 until 31) {
                val original = levels[x % levels.size]
                val value = output.getColor(x, 16) and 255
                val neighbors = listOf(levels[(x - 1) % levels.size], original, levels[(x + 1) % levels.size])
                assertTrue(value in neighbors.min()..neighbors.max(), "No new extrema at x=$x")
                if (strength == 1f) strongChange += abs(value - original) else gentleChange += abs(value - original)
            }
            if (strength == 1f) assertTrue((output.getColor(2, 16) and 255) <= 12, "Gray text edge must visibly darken")
        }
        assertTrue(gentleChange > 0)
        assertTrue(strongChange > gentleChange)
    }

    @Test fun platformKernelsStayInParity() {
        val android = File("src/androidMain/kotlin/com/rchdliang/copymanga_neo/multiplatform/ui/reader/AndroidReaderEnhancement.kt").readText()
        val shader = android.substringAfter("internal const val READER_SHARPEN_SHADER = \"\"\"").substringBefore("\"\"\"")
        assertEquals(READER_SHARPEN_SHADER, shader.replace("\r\n", "\n"))
    }

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
    @Test fun composeEffectCanBeAdjustedDisabledAndDisposed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
        var amount by mutableFloatStateOf(0f)
        var pageKey by mutableStateOf("first")
        val reported = mutableMapOf<String, Boolean>()
        val bytes = fixture { x, _ -> gray(listOf(100, 100, 105, 135, 145, 148, 150, 150)[x % 8]) }
        Image.makeFromEncoded(bytes).use { source ->
            val bitmap = source.toComposeImageBitmap()
            val scene = ImageComposeScene(32, 32, coroutineContext = coroutineContext) {
                CompositionLocalProvider(LocalReaderSharpenStatus provides { key, applied -> reported[key] = applied }) {
                    ComposeImage(bitmap, null, Modifier.fillMaxSize().readerSharpen(amount, pageKey))
                }
            }
            try {
                val renders = mutableListOf<ByteArray>()
                var time = 0L
                for (strength in listOf(0f, .20f, .35f, 0f)) {
                    amount = strength
                    pageKey = "page-$strength"
                    repeat(3) { runCurrent(); time += 16_000_000; scene.render(time).close(); runCurrent() }
                    time += 16_000_000
                    scene.render(time).use { rendered ->
                        rendered.encodeToData()!!.use { renders += it.bytes }
                    }
                    if (strength > 0f) assertEquals(true, reported[pageKey], "Measured surface must publish its status, including same-size page changes")
                }
                assertContentEquals(renders.first(), renders.last())
                assertFalse(renders[0].contentEquals(renders[2]))
            } finally { scene.close() }
        }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun renderSameLabSamplesWhenProvided() {
        val directory = System.getenv("READER_ENHANCEMENT_SAMPLES")?.let(::File) ?: return
        val files = directory.listFiles()!!.filter { it.name.endsWith("-bicubic-control.png") }
        assertEquals(4, files.size)
        for (file in files) for (amount in listOf(.20f, .35f)) {
            render(file.readBytes(), amount) { output ->
                Image.makeFromBitmap(output).use { image ->
                    image.encodeToData()!!.use { data ->
                        File(directory, file.name.replace("bicubic-control", "skia-sharpen-${(amount * 100).toInt()}")).writeBytes(data.bytes)
                    }
                }
            }
        }
    }
}
