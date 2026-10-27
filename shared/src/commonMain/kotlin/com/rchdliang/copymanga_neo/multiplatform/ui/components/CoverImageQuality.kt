package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.compose.LocalPlatformContext
import coil3.decode.Decoder
import coil3.request.ImageRequest

/** 仅由需要修正封面采样的平台启用，与阅读器的图片处理独立。 */
class CoverImageQuality(val decoderFactory: Decoder.Factory, val cacheKey: String)

/** 保留原请求配置与磁盘缓存，仅隔离采用不同采样算法的内存位图。 */
@Composable
internal fun coverImageModel(model: Any?): Any? {
    val quality = LocalUiPlatform.current?.coverImageQuality ?: return model
    if (model == null) return null
    val context = LocalPlatformContext.current
    return remember(model, context, quality) {
        (if (model is ImageRequest) model.newBuilder(context) else ImageRequest.Builder(context).data(model))
            .decoderFactory(quality.decoderFactory)
            .memoryCacheKeyExtra("cover_sampling", quality.cacheKey)
            .build()
    }
}
