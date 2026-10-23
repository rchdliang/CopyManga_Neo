package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import coil3.decode.Decoder
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Size
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** 由平台显式启用；独立缓存标记避免复用旧采样算法或预加载的小图。 */
class ReaderImageQuality(val decoderFactory: Decoder.Factory, val cacheKey: String)

internal data class ReaderImageDecodeOptions(val quality: ReaderImageQuality, val size: Size) {
    fun applyTo(builder: ImageRequest.Builder) {
        builder.size(size).decoderFactory(quality.decoderFactory)
            .memoryCacheKeyExtra("reader_sampling", quality.cacheKey)
    }
}

/** 请求使用物理像素；连续阅读的无界高度保持无界，由图片比例决定高度。 */
internal fun readerDecodeSize(constraints: Constraints, zoom: Float): Size {
    fun dimension(value: Int): Dimension = if (value == Constraints.Infinity) Dimension.Undefined
    else Dimension.Pixels(ceil(value * zoom.toDouble()).toInt().coerceIn(1, 32768))
    return Size(dimension(constraints.maxWidth), dimension(constraints.maxHeight))
}

/** 半倍档位及短暂防抖避免手势每一帧都重新解码；窗口尺寸变化也会更新请求。 */
@Composable
internal fun ReaderImageRequestLayout(
    quality: ReaderImageQuality?,
    zoom: Float,
    modifier: Modifier,
    content: @Composable (ReaderImageDecodeOptions?) -> Unit,
) {
    if (quality == null) {
        content(null)
        return
    }
    BoxWithConstraints(modifier, propagateMinConstraints = true) {
        val zoomBucket = ceil(zoom.coerceIn(1f, 5f) * 2f) / 2f
        var settledZoom by remember { mutableFloatStateOf(zoomBucket) }
        LaunchedEffect(zoomBucket) {
            delay(120)
            settledZoom = zoomBucket
        }
        val size = readerDecodeSize(constraints, settledZoom)
        content(remember(quality, size) { ReaderImageDecodeOptions(quality, size) })
    }
}
