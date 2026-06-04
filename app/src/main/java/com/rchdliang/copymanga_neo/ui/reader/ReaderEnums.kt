package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.ui.layout.ContentScale

enum class ReadingDirection(val label: String) {
    LEFT_TO_RIGHT("从左到右"),
    RIGHT_TO_LEFT("从右到左"),
    TOP_TO_BOTTOM("从上至下")
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
}

enum class ScreenOrientationSetting(val label: String, val activityOrientation: Int) {
    AUTO("自动旋转", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR),
    LANDSCAPE("横屏", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    PORTRAIT("竖屏", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    SYSTEM_DEFAULT("系统默认", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

    companion object {
        fun fromName(
            name: String,
            default: ScreenOrientationSetting = SYSTEM_DEFAULT
        ): ScreenOrientationSetting =
            entries.find { it.name == name } ?: default
    }
}
