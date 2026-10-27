package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import coil3.asImage
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import org.junit.Assume.assumeTrue
import kotlin.test.*

class ReaderVulkanIntegrationTest {
    @Test fun allBundledModelsRunThroughProductionJniAndReuseResults() = runBlocking {
        assumeTrue(System.getenv("COPYMANGA_VULKAN_TESTS") == "1")
        val width = 33
        val height = 19
        val bytes = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val i = (y * width + x) * 4
            bytes[i] = (x * 7 + y * 3).toByte()
            bytes[i + 1] = (y * 13).toByte()
            bytes[i + 2] = (x * 5).toByte()
            bytes[i + 3] = 255.toByte()
        }
        Bitmap().use { original ->
            check(original.installPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE, ColorSpace.sRGB), bytes, width * 4))
            val source = original.asImage()
            val engine = DesktopReaderRestoreEngine()
            try {
                for (mode in listOf(ReaderRestoreMode.RESTORE, ReaderRestoreMode.SOFT)) {
                    for (level in ReaderRestoreLevel.entries) {
                        val selection = ReaderRestoreSelection(mode, level)
                        val result = engine.restore(source, selection, "fixture")
                        assertEquals(width, result.image.width)
                        assertEquals(height, result.image.height)
                        assertFalse(result.cacheHit)
                        val cached = engine.restore(source, selection, "fixture")
                        assertTrue(cached.cacheHit)
                        assertSame(result.image, cached.image)
                    }
                }
                original.erase(0xff202020.toInt())
                val changed = engine.restore(source, ReaderRestoreSelection(ReaderRestoreMode.SOFT, ReaderRestoreLevel.UL), "fixture")
                assertFalse(changed.cacheHit, "Changing source pixels must invalidate the cached result")
                engine.clear()
                val fresh = engine.restore(source, ReaderRestoreSelection(ReaderRestoreMode.RESTORE), "fixture")
                assertFalse(fresh.cacheHit)
            } finally { engine.close() }
        }
    }
}
