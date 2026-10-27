package com.rchdliang.copymanga_neo.multiplatform.ui.components

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.util.component1
import coil3.util.component2
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.SamplingMode

internal val windowsCoverImageQuality = CoverImageQuality(
    WindowsCoverImageDecoder.Factory,
    "windows-bilinear-v1",
)

/** Windows 封面专用：沿用 Coil 的目标尺寸计算，解码缩放改为单次双线性插值。 */
@OptIn(coil3.annotation.ExperimentalCoilApi::class)
internal class WindowsCoverImageDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {
    override suspend fun decode(): DecodeResult {
        currentCoroutineContext().ensureActive()
        val bytes = source.source().use { it.readByteArray() }
        currentCoroutineContext().ensureActive()
        return Image.makeFromEncoded(bytes).use { image ->
            val (width, height) = DecodeUtils.computeDstSize(
                image.width, image.height, options.size, options.scale, options.maxBitmapSize,
            )
            var multiplier = DecodeUtils.computeSizeMultiplier(
                image.width, image.height, width, height, options.scale, options.maxBitmapSize,
            )
            if (options.precision == Precision.INEXACT) multiplier = multiplier.coerceAtMost(1.0)
            val outWidth = (image.width * multiplier).toInt().coerceAtLeast(1)
            val outHeight = (image.height * multiplier).toInt().coerceAtLeast(1)
            val bitmap = Bitmap()
            try {
                check(bitmap.allocN32Pixels(outWidth, outHeight)) { "无法分配封面图片内存" }
                Canvas(bitmap).use { canvas ->
                    canvas.drawImageRect(
                        image, 0f, 0f, image.width.toFloat(), image.height.toFloat(),
                        0f, 0f, outWidth.toFloat(), outHeight.toFloat(), SamplingMode.LINEAR, null, false,
                    )
                }
                currentCoroutineContext().ensureActive()
                bitmap.setImmutable()
                DecodeResult(bitmap.asImage(), outWidth < image.width || outHeight < image.height)
            } catch (error: Throwable) {
                bitmap.close()
                throw error
            }
        }
    }

    object Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder =
            WindowsCoverImageDecoder(result.source, options)
    }
}
