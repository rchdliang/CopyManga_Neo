package com.rchdliang.copymanga_neo.domain.model

/** 阅读偏好的完整快照；首次阅读时从全局默认值复制到对应漫画。 */
data class ReaderPreferences(
    val readingDirection: String = "RIGHT_TO_LEFT",
    val pageScale: String = "FIT_SCREEN",
    val screenOrientation: String = "SYSTEM_DEFAULT"
) {
    fun normalized() = ReaderPreferences(
        readingDirection.takeIf { it in setOf("LEFT_TO_RIGHT", "RIGHT_TO_LEFT", "TOP_TO_BOTTOM") }
            ?: "RIGHT_TO_LEFT",
        pageScale.takeIf { it in setOf("FIT_SCREEN", "FIT_HEIGHT", "FIT_WIDTH") } ?: "FIT_SCREEN",
        screenOrientation.takeIf { it in setOf("AUTO", "LANDSCAPE", "PORTRAIT", "SYSTEM_DEFAULT") }
            ?: "SYSTEM_DEFAULT"
    )
}
