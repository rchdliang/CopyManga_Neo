package com.rchdliang.copymanga_neo.domain.reading

import kotlin.math.min

/** 按图片实际 Fit 尺寸限制平移，横屏查看竖图时不能把图片拖进大块空白。 */
fun novelImagePanLimits(
    width: Int,
    height: Int,
    imageWidth: Int,
    imageHeight: Int,
    scale: Float
): Pair<Float, Float> {
    val zoom = scale.takeIf { it.isFinite() }?.coerceIn(1f, 5f) ?: 1f
    if (width <= 0 || height <= 0) return 0f to 0f
    val factor = if (imageWidth > 0 && imageHeight > 0) min(
        width.toFloat() / imageWidth,
        height.toFloat() / imageHeight
    ) else 1f
    val drawnWidth = if (imageWidth > 0) imageWidth * factor else width.toFloat()
    val drawnHeight = if (imageHeight > 0) imageHeight * factor else height.toFloat()
    return ((drawnWidth * zoom - width) / 2f).coerceAtLeast(0f) to
            ((drawnHeight * zoom - height) / 2f).coerceAtLeast(0f)
}
