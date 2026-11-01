package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.abs

internal val READER_COMMENT_EDGE_WIDTH = 64.dp

internal class ReaderCommentPager(val state: PagerState, val fling: FlingBehavior, val scope: CoroutineScope) {
    val blockers = mutableStateMapOf<Any, Boolean>()
    val blocked: Boolean get() = blockers.isNotEmpty()
}

internal val LocalReaderCommentPager = staticCompositionLocalOf<ReaderCommentPager?> { null }

/** 评论正文保留滚动和点击；边缘、滚动边界及明确的横向鼠标拖动交给阅读器翻页。 */
internal fun Modifier.readerCommentMouseNavigation(
    list: ScrollableState,
    rtl: Boolean,
    blocked: Boolean,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    edgeWidth: Dp = READER_COMMENT_EDGE_WIDTH,
    canLeaveEnd: () -> Boolean = { true }
): Modifier = composed {
    val forward by rememberUpdatedState(onForward)
    val backward by rememberUpdatedState(onBackward)
    val mayLeaveEnd by rememberUpdatedState(canLeaveEnd)
    val paging = LocalReaderCommentPager.current
    val blocker = remember { Any() }
    DisposableEffect(paging, blocked) {
        if (blocked) paging?.blockers?.set(blocker, true)
        onDispose { paging?.blockers?.remove(blocker) }
    }
    val edge = with(LocalDensity.current) { edgeWidth.toPx() }

    this.pointerInput(list, rtl, blocked, edge) {
        if (blocked) return@pointerInput
        var accumulated = 0f
        var lastEvent = 0L
        var lastTurn: Long? = null
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll || event.keyboardModifiers.isCtrlPressed ||
                    event.keyboardModifiers.isAltPressed || event.keyboardModifiers.isMetaPressed
                ) continue
                val change = event.changes.firstOrNull {
                    it.type == PointerType.Mouse && !it.isConsumed && it.scrollDelta != Offset.Zero
                } ?: continue
                val delta = change.scrollDelta
                if (!delta.x.isFinite() || !delta.y.isFinite()) continue
                val vertical = abs(delta.y) >= abs(delta.x)
                val amount = if (vertical) delta.y else if (rtl) -delta.x else delta.x
                val atEdge = change.position.x < edge || change.position.x > size.width - edge
                // 正文向前翻页还须确认评论已加载并布局；两侧黑边保留主动翻页入口。
                val atBoundary = if (amount > 0) !list.canScrollForward && mayLeaveEnd() else !list.canScrollBackward
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
    }.pointerInput(paging, rtl, blocked, edge) {
        if (blocked) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            val leftEdge = down.position.x < edge
            val atEdge = leftEdge || down.position.x > size.width - edge
            if (atEdge) down.consume()
            var dragging = false
            var moved = false
            var deltas: Channel<Float>? = null
            var velocity = 0f
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var lastSampleTime = down.uptimeMillis
            var distance = Offset.Zero
            val sign = if (rtl) 1f else -1f
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (event.changes.count { it.pressed } > 1 || event.buttons.isSecondaryPressed) return@awaitEachGesture
                    // 页面本身正在移动，不能用它的局部坐标与按下位置直接相减。
                    // positionChange 的前后坐标使用同一变换，累加后得到稳定的指针位移。
                    val movement = change.positionChangeIgnoreConsumed()
                    distance += movement
                    if (distance.getDistance() > viewConfiguration.touchSlop) moved = true
                    if (!dragging && moved) {
                        if (abs(distance.y) >= abs(distance.x)) return@awaitEachGesture
                        val host = paging ?: return@awaitEachGesture
                        dragging = true
                        val channel = Channel<Float>(Channel.UNLIMITED)
                        deltas = channel
                        // 持续滚动同一个 Pager，松开后沿用图片页的吸附与惯性。
                        host.scope.launch {
                            host.state.scroll(MutatePriority.UserInput) {
                                for (delta in channel) scrollBy(delta * sign)
                                with(host.fling) { performFling(velocity * sign) }
                            }
                        }
                        channel.trySend(distance.x - viewConfiguration.touchSlop * if (distance.x > 0) 1f else -1f)
                    } else if (dragging) {
                        deltas?.trySend(change.positionChangeIgnoreConsumed().x)
                    }
                    // 页面移动会产生无位移的鼠标事件；只采样实际位移，避免抹掉甩动速度。
                    if (movement != Offset.Zero && change.uptimeMillis > lastSampleTime) {
                        tracker.addPosition(change.uptimeMillis, down.position + distance)
                        lastSampleTime = change.uptimeMillis
                    }
                    // Initial 阶段先识别方向，避免正文的 combinedClickable 提前消费横向拖动。
                    if (dragging || atEdge) change.consume()
                    if (!change.pressed) {
                        if (dragging) {
                            velocity = if (change.uptimeMillis - lastSampleTime <= 100) tracker.calculateVelocity().x else 0f
                        } else if (atEdge && !moved && change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                            if (leftEdge == rtl) forward() else backward()
                        }
                        break
                    }
                }
            } finally {
                // 方向取消、窗口切换或新手势取消旧手势时也必须释放滚动锁并归位。
                deltas?.close()
            }
        }
    }
}
