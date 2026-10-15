package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.ui.layout.ContentScale

enum class ReadingDirection(val label: String) {
    LEFT_TO_RIGHT("从左到右"),
    RIGHT_TO_LEFT("从右到左"),
    TOP_TO_BOTTOM("从上至下");

    companion object {
        fun fromName(name: String): ReadingDirection =
            entries.firstOrNull { it.name == name } ?: RIGHT_TO_LEFT
    }
}

enum class PageScale(val label: String) {
    FIT_SCREEN("匹配屏幕"),
    FIT_HEIGHT("匹配高度"),
    FIT_WIDTH("匹配宽度");

    fun toContentScale(): ContentScale = when (this) {
        FIT_SCREEN -> ContentScale.Fit
        FIT_HEIGHT -> ContentScale.FillHeight
        FIT_WIDTH -> ContentScale.FillWidth
    }

    companion object {
        fun fromName(name: String): PageScale =
            entries.firstOrNull { it.name == name } ?: FIT_SCREEN
    }
}

enum class ScreenOrientationSetting(val label: String) {
    AUTO("自动旋转"),
    LANDSCAPE("横屏"),
    PORTRAIT("竖屏"),
    SYSTEM_DEFAULT("系统默认");

    companion object {
        fun fromName(
            name: String,
            default: ScreenOrientationSetting = SYSTEM_DEFAULT
        ): ScreenOrientationSetting =
            entries.find { it.name == name } ?: default
    }
}
