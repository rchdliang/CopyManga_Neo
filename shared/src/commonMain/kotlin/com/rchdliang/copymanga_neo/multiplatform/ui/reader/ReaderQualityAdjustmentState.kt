package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset

internal const val READER_MAX_SCALE = 5f
internal const val READER_CONTINUOUS_MAX_SCALE = 3f

/** 每次进入只自动放大一次；之后的切档、切换模式与退出均不改变用户视图。 */
@Stable
internal class ReaderQualityAdjustmentState {
    var active by mutableStateOf(false)
        private set
    private var target: ReaderZoomState? = null
    private var initialMaximum = READER_MAX_SCALE
    var pageKey: String? = null
        private set
    fun isAttachedTo(zoom: ReaderZoomState?) = target === zoom

    fun begin(key: String, zoom: ReaderZoomState?, focal: Offset? = null, maximum: Float = READER_MAX_SCALE) {
        if (active && pageKey != key) finish()
        if (!active) {
            active = true
            pageKey = key
            initialMaximum = maximum
        }
        attach(zoom, focal)
    }

    fun attach(zoom: ReaderZoomState?, focal: Offset? = null) {
        if (!active || zoom == null || target === zoom) return
        val observing = target?.snapshot()
        target = zoom
        if (observing == null) {
            zoom.maximize(focal ?: zoom.viewportCenter(), initialMaximum)
        } else {
            // 布局重建了缩放容器时，继承正在观察的视图，不重新放大或居中。
            zoom.restore(observing)
        }
    }

    fun finish() {
        target = null
        pageKey = null
        active = false
    }
}
