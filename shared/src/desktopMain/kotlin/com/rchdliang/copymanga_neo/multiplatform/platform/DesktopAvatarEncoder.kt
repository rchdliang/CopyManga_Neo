package com.rchdliang.copymanga_neo.multiplatform.platform

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt

/** Bounded input, subsampled decoding, EXIF orientation, 1024px maximum, JPEG quality 90. */
internal object DesktopAvatarEncoder {
    fun jpeg(bytes: ByteArray, checkCancelled: () -> Unit = {}): ByteArray {
        require(bytes.size <= 10 * 1024 * 1024) { "头像原图不能超过10MiB" }
        checkCancelled()
        // Codec selection must not apply the comic-export format allowlist: ImageIO also accepts BMP.
        val webp = bytes.size >= 12 &&
            "RIFF".indices.all { bytes[it] == "RIFF"[it].code.toByte() } &&
            "WEBP".indices.all { bytes[it + 8] == "WEBP"[it].code.toByte() }
        val decoded = if (webp) decodeDesktopClipboardImage(bytes, maximumWebpRasterDimension = 2048, checkCancelled = checkCancelled).image
        else requireNotNull(ImageIO.createImageInputStream(ByteArrayInputStream(bytes))) { "无法读取图片" }.use { input ->
            val readers = ImageIO.getImageReaders(input)
            check(readers.hasNext()) { "无法解码头像；此桌面支持JPEG、PNG、GIF、BMP和WebP" }
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(width > 0 && height > 0) { "图片尺寸无效" }
                val sample = ((max(width, height).toLong() + 2047L) / 2048L).coerceAtLeast(1).toInt()
                val parameters = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                reader.read(0, parameters) ?: error("无法解码头像")
            } finally { reader.dispose() }
        }
        val oriented = orient(decoded, if (webp) 1 else exifOrientation(bytes))
        val scale = (1024.0 / max(oriented.width, oriented.height)).coerceAtMost(1.0)
        val width = (oriented.width * scale).roundToInt().coerceAtLeast(1)
        val height = (oriented.height * scale).roundToInt().coerceAtLeast(1)
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        output.createGraphics().let { g ->
            try {
                g.color = Color.WHITE
                g.fillRect(0, 0, width, height)
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g.drawImage(oriented, 0, 0, width, height, null)
            } finally { g.dispose() }
        }
        val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull()
            ?: error("系统没有JPEG编码器")
        return ByteArrayOutputStream().use { bytesOut ->
            try {
                checkCancelled()
                requireNotNull(ImageIO.createImageOutputStream(bytesOut)).use { stream ->
                    writer.output = stream
                    val parameters = writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = 0.9f
                    }
                    writer.write(null, javax.imageio.IIOImage(output, null, null), parameters)
                    stream.flush()
                }
                checkCancelled()
                bytesOut.toByteArray()
            } finally { writer.dispose(); decoded.flush(); oriented.flush(); output.flush() }
        }
    }

    internal fun orient(source: BufferedImage, orientation: Int): BufferedImage {
        if (orientation !in 2..8) return source
        val w = source.width
        val h = source.height
        val result = BufferedImage(if (orientation >= 5) h else w, if (orientation >= 5) w else h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) for (x in 0 until w) {
            val (dx, dy) = when (orientation) {
                2 -> w - 1 - x to y
                3 -> w - 1 - x to h - 1 - y
                4 -> x to h - 1 - y
                5 -> y to x
                6 -> h - 1 - y to x
                7 -> h - 1 - y to w - 1 - x
                else -> y to w - 1 - x
            }
            result.setRGB(dx, dy, source.getRGB(x, y))
        }
        return result
    }

    /** Reads TIFF orientation in JPEG APP1 or PNG eXIf without trusting unbounded offsets. */
    internal fun exifOrientation(bytes: ByteArray): Int {
        fun u8(i: Int) = bytes[i].toInt() and 255
        if (bytes.size >= 4 && u8(0) == 255 && u8(1) == 216) {
            var p = 2
            while (p + 4 <= bytes.size) {
                if (u8(p) != 255) break
                val marker = u8(p + 1)
                if (marker == 218 || marker == 217) break
                val length = (u8(p + 2) shl 8) or u8(p + 3)
                if (length < 2 || p.toLong() + 2L + length > bytes.size) break
                if (marker == 225 && length >= 8 && bytes.copyOfRange(p + 4, p + 10).contentEquals(byteArrayOf(69, 120, 105, 102, 0, 0))) {
                    return tiffOrientation(bytes, p + 10, p + 2 + length)
                }
                p += 2 + length
            }
        }
        if (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))) {
            var p = 8
            while (p + 12 <= bytes.size) {
                val length = (u8(p).toLong() shl 24) or (u8(p + 1).toLong() shl 16) or (u8(p + 2).toLong() shl 8) or u8(p + 3).toLong()
                if (length > bytes.size - p - 12) break
                if (String(bytes, p + 4, 4, Charsets.US_ASCII) == "eXIf") return tiffOrientation(bytes, p + 8, p + 8 + length.toInt())
                p += 12 + length.toInt()
            }
        }
        return 1
    }

    private fun tiffOrientation(bytes: ByteArray, base: Int, end: Int): Int {
        if (base < 0 || base + 8 > end || end > bytes.size) return 1
        val little = bytes[base] == 73.toByte() && bytes[base + 1] == 73.toByte()
        if (!little && !(bytes[base] == 77.toByte() && bytes[base + 1] == 77.toByte())) return 1
        fun u8(i: Int) = bytes[i].toInt() and 255
        fun u16(i: Int): Int = if (little) u8(i) or (u8(i + 1) shl 8) else (u8(i) shl 8) or u8(i + 1)
        fun u32(i: Int): Long = if (little) u16(i).toLong() or (u16(i + 2).toLong() shl 16) else (u16(i).toLong() shl 16) or u16(i + 2).toLong()
        if (u16(base + 2) != 42) return 1
        val offset = u32(base + 4)
        if (offset > end - base - 2 || offset < 8) return 1
        val directory = base + offset.toInt()
        val count = u16(directory)
        if (count > (end - directory - 2) / 12) return 1
        for (i in 0 until count) {
            val entry = directory + 2 + i * 12
            if (u16(entry) == 0x112 && u16(entry + 2) == 3 && u32(entry + 4) == 1L) {
                return u16(entry + 8).takeIf { it in 1..8 } ?: 1
            }
        }
        return 1
    }
}
