package com.rchdliang.copymanga_neo.domain.reading

/** 阅读位置边界与长图比例，保持已存锚点的取整规则。 */
object ReadingPositionPolicy {
    fun fraction(offset: Int, itemSize: Int): Double =
        if (itemSize > 0) (offset.toDouble() / itemSize).coerceIn(0.0, 1.0) else 0.0

    fun offset(fraction: Double, itemSize: Int): Int =
        if (!fraction.isFinite() || itemSize < 0) 0
        else kotlin.math.floor(fraction.coerceIn(0.0, 1.0) * itemSize + 0.5).toInt()

    fun completed(
        page: Int,
        count: Int,
        settled: Boolean,
        lastImageBottomVisible: Boolean,
        continuous: Boolean = false
    ): Boolean =
        count > 0 && (continuous || page == count - 1) && settled && lastImageBottomVisible

    fun clampPage(page: Int, count: Int): Int = if (count <= 0) 0 else page.coerceIn(0, count - 1)
}
