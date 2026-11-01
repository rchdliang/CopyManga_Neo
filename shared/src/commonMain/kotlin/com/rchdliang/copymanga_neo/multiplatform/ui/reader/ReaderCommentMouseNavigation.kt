package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal val READER_COMMENT_EDGE_WIDTH = 40.dp

/** 评论正文保留滚动和点击；边缘、滚动边界及明确的横向鼠标拖动交给阅读器翻页。 */
internal fun Modifier.readerCommentMouseNavigation(
    list: LazyListState,
    rtl: Boolean,
    blocked: Boolean,
    endLoaded: Boolean,
    onForward: () -> Unit,
    onBackward: () -> Unit
): Modifier = composed {
    val forward by rememberUpdatedState(onForward)
    val backward by rememberUpdatedState(onBackward)
    val currentEndLoaded by rememberUpdatedState(endLoaded)
    val edge = with(LocalDensity.current) { READER_COMMENT_EDGE_WIDTH.toPx() }
    val dragThreshold = with(LocalDensity.current) { 48.dp.toPx() }

    this.pointerInput(list, rtl, blocked, edge) {
        if (blocked) return@pointerInput
        var accumulated = 0f
        var lastEvent = 0L
        var lastTurn: Long? = null
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll || event.keyboardModifiers.isCtrlPressed ||
                    event.keyboardModifiers.isAltPressed || event.keyboardModifiers.isMetaPressed) continue
                val change = event.changes.firstOrNull {
                    it.type == PointerType.Mouse && !it.isConsumed && it.scrollDelta != Offset.Zero
                } ?: continue
                val delta = change.scrollDelta
                if (!delta.x.isFinite() || !delta.y.isFinite()) continue
                val vertical = abs(delta.y) >= abs(delta.x)
                val amount = if (vertical) delta.y else if (rtl) -delta.x else delta.x
                val atEdge = change.position.x < edge || change.position.x > size.width - edge
                // 自动分页尚未结束时，暂时触底不能跳走；滚动动画结束后的下一次滚轮才越过边界。
                val atBoundary = !list.isScrollInProgress && if (amount > 0)
                    currentEndLoaded && !list.canScrollForward else !list.canScrollBackward
                if (!atEdge && (!vertical || !atBoundary)) {
                    accumulated = 0f
                    continue
                }
                if (change.uptimeMillis - lastEvent > 180) accumulated = 0f
                accumulated += amount
                if (abs(accumulated) >= 1f && (lastTurn?.let { change.uptimeMillis - it >= 350 } ?: true)) {
                    if (accumulated > 0) forward() else backward()
                    accumulated = 0f
                    lastTurn = change.uptimeMillis
                }
                lastEvent = change.uptimeMillis
                event.changes.forEach { it.consume() }
            }
        }
    }.pointerInput(list, rtl, blocked, edge, dragThreshold) {
        if (blocked) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            val leftEdge = down.position.x < edge
            val atEdge = leftEdge || down.position.x > size.width - edge
            if (atEdge) down.consume()
            var dragging = false
            var moved = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (event.changes.count { it.pressed } > 1 || event.buttons.isSecondaryPressed) return@awaitEachGesture
                val distance = change.position - down.position
                if (distance.getDistance() > viewConfiguration.touchSlop) moved = true
                if (!dragging && moved) {
                    if (abs(distance.y) >= abs(distance.x)) return@awaitEachGesture
                    dragging = true
                }
                // Initial 阶段先识别方向，避免正文的 combinedClickable 提前消费横向拖动。
                if (dragging || atEdge) change.consume()
                if (!change.pressed) {
                    if (dragging && abs(distance.x) >= dragThreshold) {
                        if ((distance.x > 0) == rtl) forward() else backward()
                    } else if (atEdge && !moved && change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                        if (leftEdge == rtl) forward() else backward()
                    }
                    break
                }
            }
        }
    }
}
