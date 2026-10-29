package com.rchdliang.copymanga_neo.multiplatform.ui.reader

/** 电量未知与充电状态独立，避免把无法读取的电量误显示为 0%。 */
data class ReaderBatteryStatus(val percentage: Int? = null, val charging: Boolean = false) {
    val validPercentage: Int? get() = percentage?.takeIf { it in 0..100 }
}

internal enum class ReaderBatteryGlyph { EMPTY, BAR_1, BAR_2, BAR_3, BAR_4, BAR_5, BAR_6, FULL, CHARGING, UNKNOWN }

/** 非满电按七档显示；100% 才使用满电图标，充电状态优先。 */
internal fun ReaderBatteryStatus.glyph(): ReaderBatteryGlyph {
    if (charging) return ReaderBatteryGlyph.CHARGING
    val level = validPercentage ?: return ReaderBatteryGlyph.UNKNOWN
    return when {
        level == 100 -> ReaderBatteryGlyph.FULL
        level < 15 -> ReaderBatteryGlyph.EMPTY
        level < 29 -> ReaderBatteryGlyph.BAR_1
        level < 43 -> ReaderBatteryGlyph.BAR_2
        level < 58 -> ReaderBatteryGlyph.BAR_3
        level < 72 -> ReaderBatteryGlyph.BAR_4
        level < 86 -> ReaderBatteryGlyph.BAR_5
        else -> ReaderBatteryGlyph.BAR_6
    }
}
