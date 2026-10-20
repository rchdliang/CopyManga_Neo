package com.rchdliang.copymanga_neo.multiplatform.app

/** 空间不足时预留一个完整的「更多」入口，不压缩导航项的点击区域。 */
internal fun navigationRailVisibleCount(total: Int, availableHeight: Int, itemHeight: Int): Int {
    require(total >= 0 && itemHeight > 0)
    val slots = availableHeight.coerceAtLeast(0) / itemHeight
    return if (slots >= total) total else (slots - 1).coerceIn(0, total)
}
