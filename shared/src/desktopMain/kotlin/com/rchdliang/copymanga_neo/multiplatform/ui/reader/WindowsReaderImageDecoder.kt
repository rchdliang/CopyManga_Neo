package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.util.component1
import coil3.util.component2
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.SamplingMode
import kotlin.math.sqrt

internal val windowsReaderImageQuality = ReaderImageQuality(WindowsReaderImageDecoder.Factory, "windows-cubic-v1")

/** 漫画页专用：缩小用三次插值，不放大源像素，并限制单页解码内存。 */
internal class WindowsReaderImageDecoder(private val source: ImageSource, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult {
        currentCoroutineContext().ensureActive()
        val bytes = source.source().use { it.readByteArray() }
        currentCoroutineContext().ensureActive()
        val result = decodeWindowsReaderImage(bytes, options)
        try {
            currentCoroutineContext().ensureActive()
            return result
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            (result.image as coil3.BitmapImage).bitmap.close()
            throw cancelled
        }
    }

    object Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder =
            WindowsReaderImageDecoder(result.source, options)
    }
}

/** 保留宽高比，最多约 64 MiB 输出位图；不对原始文件及磁盘缓存做重采样。 */
@OptIn(coil3.annotation.ExperimentalCoilApi::class)
internal fun decodeWindowsReaderImage(bytes: ByteArray, options: Options): DecodeResult =
    Image.makeFromEncoded(bytes).use { image ->
        val (width, height) = DecodeUtils.computeDstSize(
            image.width, image.height, options.size, options.scale, options.maxBitmapSize,
        )
        val multiplier = minOf(
            1.0,
            DecodeUtils.computeSizeMultiplier(
                image.width,
                image.height,
                width,
                height,
                options.scale,
                options.maxBitmapSize
            ),
            sqrt(16_777_216.0 / (image.width.toDouble() * image.height)),
        )
        val outWidth = (image.width * multiplier).toInt().coerceAtLeast(1)
        val outHeight = (image.height * multiplier).toInt().coerceAtLeast(1)
        val bitmap = Bitmap()
        try {
            check(bitmap.allocN32Pixels(outWidth, outHeight)) { "无法分配阅读图片内存" }
            Canvas(bitmap).use { canvas ->
                canvas.drawImageRect(
                    image, 0f, 0f, image.width.toFloat(), image.height.toFloat(),
                    0f, 0f, outWidth.toFloat(), outHeight.toFloat(), SamplingMode.MITCHELL, null, false,
                )
            }
            bitmap.setImmutable()
            DecodeResult(bitmap.asImage(), outWidth < image.width || outHeight < image.height)
        } catch (error: Throwable) {
            bitmap.close()
            throw error
        }
    }
