package com.rchdliang.copymanga_neo.multiplatform.platform

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlinx.coroutines.CancellationException
import org.jetbrains.skia.*
import kotlin.test.*

class DesktopClipboardImageDecoderTest {
    private fun png(): ByteArray = ByteArrayOutputStream().use { output ->
        val image = BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0x80ff0000.toInt())
        image.setRGB(1, 2, 0xff00ff00.toInt())
        ImageIO.write(image, "png", output)
        image.flush()
        output.toByteArray()
    }

    @Test fun pngPreservesUnpremultipliedAlphaAndColors() {
        val bytes = png()
        val original = bytes.copyOf()
        val decoded = decodeDesktopClipboardImage(bytes)
        try {
            assertEquals(2, decoded.image.width)
            assertEquals(3, decoded.image.height)
            assertEquals(0x80ff0000.toInt(), decoded.image.getRGB(0, 0))
            assertEquals(0xff00ff00.toInt(), decoded.image.getRGB(1, 2))
            assertFalse(decoded.firstFrameOnly)
            assertContentEquals(original, bytes)
        } finally { decoded.image.flush() }
    }

    @Test fun bundledCodecDecodesWebpWithoutChangingEncodedBytes() {
        val info = ImageInfo(2, 2, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        val rgba = ByteArray(16) { if (it % 4 == 0 || it % 4 == 3) 255.toByte() else 0 }
        val bytes = Image.makeRaster(info, rgba, 8).use { image ->
            requireNotNull(image.encodeToData(EncodedImageFormat.WEBP, 100)).use { it.bytes }
        }
        val original = bytes.copyOf()
        val decoded = decodeDesktopClipboardImage(bytes)
        try {
            assertEquals(2, decoded.image.width)
            assertEquals(2, decoded.image.height)
            val pixel = decoded.image.getRGB(0, 0)
            assertEquals(255, pixel ushr 24)
            assertTrue((pixel ushr 16 and 255) >= 245)
            assertTrue((pixel ushr 8 and 255) <= 10)
            assertTrue((pixel and 255) <= 10)
            assertContentEquals(original, bytes)
        } finally { decoded.image.flush() }
    }

    @Test fun oversizedValidHeaderRejectedBeforeRasterDecode() {
        val bytes = png()
        ByteBuffer.wrap(bytes, 16, 8).putInt(32768).putInt(32768)
        val crc = CRC32().apply { update(bytes, 12, 17) }
        ByteBuffer.wrap(bytes, 29, 4).putInt(crc.value.toInt())
        val error = assertFailsWith<IllegalArgumentException> { decodeDesktopClipboardImage(bytes) }
        assertTrue(error.message.orEmpty().contains("尺寸过大"))
    }

    @Test fun limitsRejectInvalidAndOverflowingDimensions() {
        listOf(0 to 1, 1 to 0, -1 to 2, Int.MAX_VALUE to Int.MAX_VALUE, 32768 to 32768).forEach { (w, h) ->
            assertFailsWith<IllegalArgumentException> { validateClipboardDimensions(w, h) }
        }
        validateClipboardDimensions(1024, 16384)
        assertFailsWith<IllegalArgumentException> { decodeDesktopClipboardImage(byteArrayOf()) }
        assertFails { decodeDesktopClipboardImage(byteArrayOf(1, 2, 3, 4)) }
    }

    @Test fun cancellationRemainsCancellation() {
        assertFailsWith<CancellationException> {
            decodeDesktopClipboardImage(png()) { throw CancellationException("cancelled") }
        }
    }

    @Test fun exifRotationUsesUprightCoordinatesWithoutAdditionalRaster() {
        val destinations = (0 until 3).flatMap { y -> (0 until 2).map { x ->
            clipboardPixelIndex(x, y, 2, 3, EncodedOrigin.RIGHT_TOP)
        } }
        assertEquals(listOf(2, 5, 1, 4, 0, 3), destinations)
        EncodedOrigin.entries.forEach { origin ->
            val indices = (0 until 3).flatMap { y -> (0 until 2).map { x -> clipboardPixelIndex(x, y, 2, 3, origin) } }
            assertEquals((0..5).toSet(), indices.toSet())
        }
    }
}
