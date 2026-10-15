package com.rchdliang.copymanga_neo.multiplatform.platform

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.jetbrains.skia.*
import kotlin.test.*

class DesktopWebpAvatarTest {
    private fun webp(width: Int, height: Int): ByteArray {
        val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        val rgba = ByteArray(width * height * 4) { if (it % 4 == 3) 255.toByte() else 0 }
        return Image.makeRaster(info, rgba, width * 4).use { image ->
            requireNotNull(image.encodeToData(EncodedImageFormat.WEBP, 100)).use { it.bytes }
        }
    }

    @Test fun webpIsDecodedToBoundedRasterBeforeAvatarJpegEncoding() {
        val bytes = webp(3072, 1536)
        val preview = decodeDesktopClipboardImage(bytes, maximumWebpRasterDimension = 2048)
        try {
            assertEquals(2048, preview.image.width)
            assertEquals(1024, preview.image.height)
        } finally { preview.image.flush() }
        val jpeg = DesktopAvatarEncoder.jpeg(bytes)
        val image = requireNotNull(ImageIO.read(ByteArrayInputStream(jpeg)))
        try {
            assertEquals(1024, image.width)
            assertEquals(512, image.height)
            assertFalse(image.colorModel.hasAlpha())
        } finally { image.flush() }
    }

    @Test fun selectableBmpAvatarIsDecodedAndEncodedAsBoundedJpeg() {
        val bmp = ByteArrayOutputStream().use { output ->
            val source = BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB)
            try {
                source.createGraphics().let { graphics ->
                    try {
                        graphics.color = Color(40, 160, 220)
                        graphics.fillRect(0, 0, source.width, source.height)
                    } finally { graphics.dispose() }
                }
                assertTrue(ImageIO.write(source, "bmp", output))
                output.toByteArray()
            } finally { source.flush() }
        }
        val jpeg = DesktopAvatarEncoder.jpeg(bmp)
        assertEquals(0xff, jpeg[0].toInt() and 255)
        assertEquals(0xd8, jpeg[1].toInt() and 255)
        val image = requireNotNull(ImageIO.read(ByteArrayInputStream(jpeg)))
        try {
            assertEquals(1024, image.width)
            assertEquals(512, image.height)
            assertFalse(image.colorModel.hasAlpha())
            val color = Color(image.getRGB(image.width / 2, image.height / 2))
            assertTrue(color.red in 35..45 && color.green in 155..165 && color.blue in 215..225)
        } finally { image.flush() }
    }

    @Test fun malformedAvatarStillFailsWithAvatarDecodeMessage() {
        val failure = assertFailsWith<IllegalStateException> {
            DesktopAvatarEncoder.jpeg(byteArrayOf(1, 2, 3, 4))
        }
        assertTrue(failure.message.orEmpty().contains("无法解码头像"))
    }

    @Test fun unsupportedScaledCodecCannotFallBackToUnboundedDecode() {
        val png = java.io.ByteArrayOutputStream().use { output ->
            val image = java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            try { ImageIO.write(image, "png", output); output.toByteArray() }
            finally { image.flush() }
        }
        assertFailsWith<IllegalArgumentException> {
            decodeDesktopClipboardImage(png, maximumWebpRasterDimension = 2048)
        }
    }
}
