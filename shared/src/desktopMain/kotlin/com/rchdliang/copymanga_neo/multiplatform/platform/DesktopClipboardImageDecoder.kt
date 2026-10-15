package com.rchdliang.copymanga_neo.multiplatform.platform

import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.skia.*

internal const val MAX_CLIPBOARD_ENCODED_BYTES = 64 * 1024 * 1024
internal const val MAX_CLIPBOARD_PIXELS = 16L * 1024 * 1024
internal const val MAX_CLIPBOARD_DIMENSION = 32768
internal data class DesktopClipboardImage(val image: BufferedImage, val firstFrameOnly: Boolean)

internal fun readDesktopClipboardImage(path: Path, checkCancelled: () -> Unit): DesktopClipboardImage {
    checkCancelled()
    require(Files.isRegularFile(path)) { "图片文件不可用" }
    require(Files.size(path) in 1..MAX_CLIPBOARD_ENCODED_BYTES.toLong()) { "图片过大，请使用保存原图" }
    val bytes = Files.newInputStream(path).use { it.readNBytes(MAX_CLIPBOARD_ENCODED_BYTES + 1) }
    checkCancelled()
    return decodeDesktopClipboardImage(bytes, checkCancelled = checkCancelled)
}

internal fun validateClipboardDimensions(width: Int, height: Int) {
    require(width in 1..MAX_CLIPBOARD_DIMENSION && height in 1..MAX_CLIPBOARD_DIMENSION &&
        width.toLong() * height <= MAX_CLIPBOARD_PIXELS) { "图片尺寸过大，无法安全复制，请使用保存原图" }
}

/** Uses the already bundled reader codec, including WebP. No encoded-file save path is changed. */
internal fun decodeDesktopClipboardImage(
    bytes: ByteArray,
    maximumWebpRasterDimension: Int? = null,
    checkCancelled: () -> Unit = {}
): DesktopClipboardImage {
    require(bytes.isNotEmpty() && bytes.size <= MAX_CLIPBOARD_ENCODED_BYTES) { "图片过大或为空，请使用保存原图" }
    checkCancelled()
    Data.makeFromBytes(bytes).use { data ->
        Codec.makeFromData(data).use { codec ->
            val sourceWidth = codec.width
            val sourceHeight = codec.height
            // Metadata only until these checks pass, including overflow-safe pixel accounting.
            validateClipboardDimensions(sourceWidth, sourceHeight)
            // WebP's SkCodec destination dimensions configure libwebp scaled decoding. Other
            // formats retain their existing ImageIO subsampling path instead of assuming support.
            val scale = maximumWebpRasterDimension?.let { maximum ->
                require(maximum in 1..2048 && codec.encodedImageFormat == EncodedImageFormat.WEBP)
                (maximum.toDouble() / maxOf(sourceWidth, sourceHeight)).coerceAtMost(1.0)
            } ?: 1.0
            val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
            val height = (sourceHeight * scale).toInt().coerceAtLeast(1)
            val origin = codec.encodedOrigin
            val swaps = origin.swapsWidthHeight()
            val outputWidth = if (swaps) height else width
            val outputHeight = if (swaps) width else height
            val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
            Bitmap().use { bitmap ->
                checkCancelled()
                check(bitmap.allocPixels(info)) { "无法分配图片内存，请使用保存原图" }
                codec.readPixels(bitmap, 0)
                checkCancelled()
                val rgba = requireNotNull(bitmap.readPixels(info, width * 4, 0, 0)) { "无法读取图片像素，请使用保存原图" }
                require(rgba.size == width * height * 4) { "图片像素不完整" }
                val output = BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB)
                try {
                    val pixels = (output.raster.dataBuffer as DataBufferInt).data
                    for (y in 0 until height) {
                        checkCancelled()
                        for (x in 0 until width) {
                            val offset = (y * width + x) * 4
                            val argb = ((rgba[offset + 3].toInt() and 255) shl 24) or
                                ((rgba[offset].toInt() and 255) shl 16) or
                                ((rgba[offset + 1].toInt() and 255) shl 8) or (rgba[offset + 2].toInt() and 255)
                            pixels[clipboardPixelIndex(x, y, width, height, origin)] = argb
                        }
                    }
                    return DesktopClipboardImage(output, codec.frameCount > 1)
                } catch (failure: Throwable) { output.flush(); throw failure }
            }
        }
    }
}

/** Encoded EXIF orientation maps source coordinates to the clipboard's upright raster. */
internal fun clipboardPixelIndex(x: Int, y: Int, width: Int, height: Int, origin: EncodedOrigin): Int =
    when (origin) {
        EncodedOrigin.TOP_RIGHT -> y * width + width - 1 - x
        EncodedOrigin.BOTTOM_RIGHT -> (height - 1 - y) * width + width - 1 - x
        EncodedOrigin.BOTTOM_LEFT -> (height - 1 - y) * width + x
        EncodedOrigin.LEFT_TOP -> x * height + y
        EncodedOrigin.RIGHT_TOP -> x * height + height - 1 - y
        EncodedOrigin.RIGHT_BOTTOM -> (width - 1 - x) * height + height - 1 - y
        EncodedOrigin.LEFT_BOTTOM -> (width - 1 - x) * height + y
        else -> y * width + x
    }
