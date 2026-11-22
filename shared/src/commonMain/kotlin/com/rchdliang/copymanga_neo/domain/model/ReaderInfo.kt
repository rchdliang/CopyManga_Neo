package com.rchdliang.copymanga_neo.domain.model

import kotlinx.serialization.Serializable

enum class ReaderInfoPosition { TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT, CUSTOM, HIDDEN }

enum class ReaderInfoItem(val defaultPosition: ReaderInfoPosition) {
    CLOCK(ReaderInfoPosition.TOP_LEFT),
    BATTERY(ReaderInfoPosition.TOP_RIGHT),
    CHAPTER(ReaderInfoPosition.BOTTOM_LEFT),
    PAGES(ReaderInfoPosition.BOTTOM_RIGHT)
}

@Serializable
data class ReaderInfoStyle(
    val position: ReaderInfoPosition,
    val fontSizeSp: Int = 16,
    val outlineDp: Int = 1,
    val darkText: Boolean = false,
    val horizontalFraction: Float = 0.5f,
    val verticalFraction: Float = 0.5f
) {
    fun normalized() = copy(
        fontSizeSp = fontSizeSp.coerceIn(10, 28), outlineDp = outlineDp.coerceIn(0, 4),
        horizontalFraction = horizontalFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f,
        verticalFraction = verticalFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f
    )
}

@Serializable
data class ReaderInfoSettings(val items: Map<ReaderInfoItem, ReaderInfoStyle> = emptyMap()) {
    operator fun get(item: ReaderInfoItem) = items[item] ?: ReaderInfoStyle(item.defaultPosition)

    fun update(item: ReaderInfoItem, change: (ReaderInfoStyle) -> ReaderInfoStyle) =
        copy(items = items + (item to change(get(item))))

    fun normalized() = copy(items = items.mapValues { it.value.normalized() })
}
