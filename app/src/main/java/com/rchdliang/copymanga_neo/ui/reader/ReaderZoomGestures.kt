package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize

/**
 * 阅读器图片缩放状态 — 记录当前图片的缩放倍率与平移偏移
 */
@Stable
class ReaderZoomState {
    var scale by mutableFloatStateOf(1f)
        internal set
    var offset by mutableStateOf(Offset.Zero)
        internal set

    internal fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

/**
 * 阅读器图片缩放手势 — 自定义 pointerInput
 *
 * - 连续模式：只处理双指缩放 + 双指平移。单指拖动由 NestedScrollConnection
 *   (continuousZoomConnection.onPreScroll) 处理，在 LazyColumn 滚动之前拦截。
 *   到边界后 onPreScroll 不消费滚动量，LazyColumn 自动滚动。
 * - 分页模式：双指缩放 + 单指拖动 + 边界翻页(onTurnPage)，始终消费事件。
 */
fun Modifier.readerImageZoomable(
    state: ReaderZoomState,
    containerSizeProvider: () -> IntSize,
    isContinuous: Boolean,
    isRtl: Boolean = false,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    onTurnPage: ((isForward: Boolean) -> Unit)? = null,
    turnPageThresholdPx: Float = 0f
): Modifier = this.pointerInput(isContinuous, isRtl, minScale, maxScale, turnPageThresholdPx) {
    if (isContinuous) {
        // 连续模式：只处理双指缩放 + 双指平移
        // 单指事件不处理，交给 LazyColumn + NestedScrollConnection
        awaitEachGesture {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }

                if (pressed.isEmpty()) break
                if (pressed.size < 2) continue  // 单指事件跳过

                val containerSize = containerSizeProvider()
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                // graphicsLayer 与 pointerInput 同节点，补偿缩放对事件坐标的影响
                val effectivePanChange =
                    Offset(panChange.x * state.scale, panChange.y * state.scale)

                val newScale = (state.scale * zoomChange).coerceIn(minScale, maxScale)
                val maxX = containerSize.width * (newScale - 1f) / 2f
                val maxY = containerSize.height * (newScale - 1f) / 2f

                val newOffset = if (newScale <= 1f) {
                    Offset.Zero
                } else {
                    Offset(
                        x = (state.offset.x + effectivePanChange.x).coerceIn(-maxX, maxX),
                        y = (state.offset.y + effectivePanChange.y).coerceIn(-maxY, maxY)
                    )
                }

                state.scale = newScale
                state.offset = newOffset

                event.changes.forEach { it.consume() }
            }
        }
    } else {
        // 分页模式：双指缩放 + 单指拖动 + 边界翻页
        var beyondAccumulation: Float
        awaitEachGesture {
            beyondAccumulation = 0f
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break

                val containerSize = containerSizeProvider()

                // 双指及以上：缩放 + 双指平移，消费事件
                if (pressed.size >= 2) {
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()

                    val newScale = (state.scale * zoomChange).coerceIn(minScale, maxScale)
                    val maxX = containerSize.width * (newScale - 1f) / 2f
                    val maxY = containerSize.height * (newScale - 1f) / 2f

                    val newOffset = if (newScale <= 1f) {
                        Offset.Zero
                    } else {
                        Offset(
                            x = (state.offset.x + panChange.x).coerceIn(-maxX, maxX),
                            y = (state.offset.y + panChange.y).coerceIn(-maxY, maxY)
                        )
                    }

                    state.scale = newScale
                    state.offset = newOffset

                    event.changes.forEach { it.consume() }
                    continue
                }

                // 单指 + 已放大：拖动图片，始终消费事件，到边界后调用 onTurnPage
                if (pressed.size == 1 && state.scale > 1f) {
                    val change = pressed.first()
                    val pan = change.positionChange()

                    val maxX = containerSize.width * (state.scale - 1f) / 2f
                    val maxY = containerSize.height * (state.scale - 1f) / 2f

                    val newOffsetX = (state.offset.x + pan.x).coerceIn(-maxX, maxX)
                    val newOffsetY = (state.offset.y + pan.y).coerceIn(-maxY, maxY)

                    val consumedX = newOffsetX - state.offset.x
                    val consumedY = newOffsetY - state.offset.y

                    state.offset = Offset(newOffsetX, newOffsetY)

                    change.consume()

                    if (turnPageThresholdPx > 0f && onTurnPage != null) {
                        val beyond = pan.x - consumedX

                        if (beyond != 0f) {
                            beyondAccumulation += beyond
                            when {
                                beyondAccumulation <= -turnPageThresholdPx -> {
                                    // 向左拖到左边界 → LTR: forward, RTL: backward
                                    onTurnPage.invoke(!isRtl)
                                    beyondAccumulation = 0f
                                }

                                beyondAccumulation >= turnPageThresholdPx -> {
                                    // 向右拖到右边界 → LTR: backward, RTL: forward
                                    onTurnPage.invoke(isRtl)
                                    beyondAccumulation = 0f
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
