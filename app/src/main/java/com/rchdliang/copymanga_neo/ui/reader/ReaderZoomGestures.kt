package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/** 缩放和平移共用视口坐标；手指焦点下的内容在缩放时保持原位。 */
@Stable
class ReaderZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    val zoomed: Boolean get() = scale > 1.01f

    private var viewport = IntSize.Zero
    private var flingJob: Job? = null
    private var brakeTap = false

    internal fun updateViewport(size: IntSize) {
        viewport = size
        offset = clamp(offset)
    }

    internal fun beginTouch(contentScrolling: Boolean) {
        brakeTap = flingJob?.isActive == true || contentScrolling
        stopFling()
    }

    /** 惯性中按下的这次点击只刹车；下一次触摸重新判定。 */
    internal fun allowTap(): Boolean = !brakeTap

    internal fun transform(zoom: Float, focal: Offset, pan: Offset, maxScale: Float) {
        val nextScale = (scale * zoom).coerceIn(1f, maxScale)
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val ratio = nextScale / scale
        offset = (offset - (focal - center)) * ratio + (focal - center) + pan
        scale = nextScale
        offset = clamp(offset)
    }

    internal fun pan(delta: Offset) {
        if (zoomed) offset = clamp(offset + delta)
    }

    internal fun toggleZoom(focal: Offset, maxScale: Float) {
        stopFling()
        if (zoomed) reset() else transform(minOf(2.5f, maxScale), focal, Offset.Zero, maxScale)
    }

    internal fun stopFling() {
        flingJob?.cancel()
        flingJob = null
    }

    /** 松手后衰减平移速度；边界、再次按下、离页或取消组合时停止。 */
    internal fun fling(scope: CoroutineScope, initialVelocity: Offset) {
        stopFling()
        if (!zoomed || initialVelocity.getDistance() < 100f) return
        flingJob = scope.launch {
            var velocity = initialVelocity
            var previousTime = withFrameNanos { it }
            while (velocity.getDistance() >= 20f && zoomed) {
                val now = withFrameNanos { it }
                val seconds = ((now - previousTime) / 1_000_000_000f).coerceAtMost(0.05f)
                previousTime = now
                val attenuation = exp(-4f * seconds)
                val delta = velocity * ((1f - attenuation) / 4f)
                val before = offset
                pan(delta)
                val moved = offset - before
                velocity = Offset(
                    if (abs(moved.x - delta.x) > 0.1f) 0f else velocity.x * attenuation,
                    if (abs(moved.y - delta.y) > 0.1f) 0f else velocity.y * attenuation
                )
            }
        }
    }

    private fun clamp(value: Offset): Offset {
        val maxX = viewport.width * (scale - 1f) / 2f
        val maxY = viewport.height * (scale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    internal fun reset() {
        stopFling()
        scale = 1f
        offset = Offset.Zero
    }
}

/**
 * 外层未缩放视口识别手势，内层只负责显示变换，避免倍率影响触摸坐标。
 * 双指在 Initial 阶段接管；多指变单指后保持接管直至全部抬起。
 * 连续模式单指只移动横向视野，纵向仍由列表滚动；分页放大后只平移。
 */
fun Modifier.readerImageZoomable(
    state: ReaderZoomState,
    scope: CoroutineScope,
    isContinuous: Boolean,
    maxScale: Float = 5f,
    isContentScrolling: () -> Boolean = { false },
    enabledAt: (Offset) -> Boolean = { true }
): Modifier = composed {
    val currentEnabled by rememberUpdatedState(enabledAt)
    val currentScrolling by rememberUpdatedState(isContentScrolling)
    pointerInput(state, isContinuous, maxScale) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.beginTouch(currentScrolling())
            val enabled = currentEnabled(down.position)
            var multiTouch = false
            var dragStarted = false
            var horizontalDrag = false
            var totalDrag = Offset.Zero
            var trackedPointer = down.id
            var tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            val canFling: Boolean
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) {
                    event.changes.firstOrNull { it.id == trackedPointer }?.let {
                        tracker.addPosition(it.uptimeMillis, it.position)
                    }
                    // 取消事件不启动惯性；真正抬手才使用最后一根手指的速度。
                    canFling =
                        dragStarted && event.changes.any { it.previousPressed && !it.pressed }
                    break
                }
                if (!enabled) continue
                if (pressed.size >= 2) {
                    multiTouch = true
                    dragStarted = false
                    tracker.resetTracking()
                    state.transform(
                        event.calculateZoom(), event.calculateCentroid(useCurrent = false),
                        event.calculatePan(), maxScale
                    )
                    event.changes.forEach { it.consume() }
                    continue
                }

                val change = pressed.single()
                if (change.id != trackedPointer || event.changes.count { it.previousPressed } > 1) {
                    trackedPointer = change.id
                    tracker =
                        VelocityTracker().apply {
                            addPosition(
                                change.uptimeMillis,
                                change.position
                            )
                        }
                    totalDrag = Offset.Zero
                }
                tracker.addPosition(change.uptimeMillis, change.position)
                val delta = change.positionChange()
                totalDrag += delta
                if (!dragStarted && totalDrag.getDistance() > viewConfiguration.touchSlop) {
                    dragStarted = true
                    horizontalDrag = abs(totalDrag.x) > abs(totalDrag.y)
                }
                if (multiTouch || (state.zoomed && dragStarted)) {
                    state.pan(if (isContinuous) Offset(delta.x, 0f) else delta)
                    // 纵向拖动不消费，让列表滚动；横向拖动、多指流和分页平移独占。
                    if (multiTouch || !isContinuous || horizontalDrag) {
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            if (canFling) {
                val velocity = tracker.calculateVelocity()
                state.fling(scope, Offset(velocity.x, if (isContinuous) 0f else velocity.y))
            }
        }
    }
}
