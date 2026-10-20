package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Compose 的 scrollable/Pager 会忽略鼠标拖动；桌面端用鼠标左键拖动时补上同等的用户滚动。
 * 拖动增量与惯性都经 NestedScroll 分发，下拉刷新等父级连接可与触摸拖动一样生效。
 * 触摸等其他指针类型仍交给组件自带的 scrollable 处理，避免重复滚动。
 */
fun Modifier.mouseDragScroll(
    state: ScrollableState,
    orientation: Orientation,
    enabled: Boolean = true,
    reverseLayout: Boolean = false,
    flingBehavior: FlingBehavior? = null
): Modifier = composed {
    val dispatcher = remember { NestedScrollDispatcher() }
    val scope = rememberCoroutineScope()
    val defaultFling = ScrollableDefaults.flingBehavior()
    val currentFling by rememberUpdatedState(flingBehavior ?: defaultFling)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // 指针增量与滚动值方向相反；反向布局或 RTL 横向时再翻转一次，与 scrollable 规则一致。
    var reverse = !reverseLayout
    if (rtl && orientation == Orientation.Horizontal) reverse = !reverse
    val sign = if (reverse) -1f else 1f

    fun Float.toOffset() =
        if (orientation == Orientation.Vertical) Offset(0f, this) else Offset(this, 0f)

    fun Offset.axis() = if (orientation == Orientation.Vertical) y else x
    fun Float.toVelocity() =
        if (orientation == Orientation.Vertical) Velocity(0f, this) else Velocity(this, 0f)

    fun Velocity.axis() = if (orientation == Orientation.Vertical) y else x

    fun ScrollScope.dispatchDrag(pointerDelta: Float) {
        val available = pointerDelta.toOffset()
        val preConsumed = dispatcher.dispatchPreScroll(available, NestedScrollSource.UserInput)
        val left = available - preConsumed
        val consumed = (scrollBy(left.axis() * sign) * sign).toOffset()
        dispatcher.dispatchPostScroll(consumed, left - consumed, NestedScrollSource.UserInput)
    }

    this
        .nestedScroll(NoOpNestedScrollConnection, dispatcher)
        .pointerInput(state, orientation, enabled, sign) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) {
                    return@awaitEachGesture
                }
                var overSlop = 0f
                val onSlop = { change: PointerInputChange, over: Float ->
                    change.consume()
                    overSlop = over
                }
                val start = if (orientation == Orientation.Vertical) {
                    awaitVerticalTouchSlopOrCancellation(down.id, onSlop)
                } else {
                    awaitHorizontalTouchSlopOrCancellation(down.id, onSlop)
                } ?: return@awaitEachGesture

                val deltas = Channel<Float>(Channel.UNLIMITED)
                val tracker = VelocityTracker().apply { addPosition(start.uptimeMillis, start.position) }
                val dragJob = scope.launch {
                    state.scroll(MutatePriority.UserInput) {
                        for (delta in deltas) dispatchDrag(delta)
                    }
                }
                deltas.trySend(overSlop)
                val onDrag = { change: PointerInputChange ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    deltas.trySend(change.positionChange().axis())
                    change.consume()
                }
                val completed = if (orientation == Orientation.Vertical) {
                    verticalDrag(start.id, onDrag)
                } else {
                    horizontalDrag(start.id, onDrag)
                }
                deltas.close()
                val velocity = if (completed) tracker.calculateVelocity().axis() else 0f
                scope.launch {
                    dragJob.join()
                    // 即使速度为 0 也分发 fling：下拉刷新在 preFling 中判定释放，Pager 也需要吸附到整页。
                    val available = velocity.toVelocity()
                    val preConsumed = dispatcher.dispatchPreFling(available)
                    val left = available - preConsumed
                    var remaining = left.axis()
                    state.scroll(MutatePriority.UserInput) {
                        val fling = currentFling
                        remaining = with(fling) { performFling(left.axis() * sign) } * sign
                    }
                    dispatcher.dispatchPostFling(
                        (left.axis() - remaining).toVelocity(),
                        remaining.toVelocity()
                    )
                }
            }
        }
}

private object NoOpNestedScrollConnection : NestedScrollConnection
