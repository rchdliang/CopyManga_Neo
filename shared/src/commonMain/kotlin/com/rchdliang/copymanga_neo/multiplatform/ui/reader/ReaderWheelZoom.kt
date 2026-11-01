package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.pow
import kotlin.math.ln

/** 每个标准滚轮刻度按 3 倍放大或缩小，分页和连续阅读共用相同灵敏度。 */
internal fun readerWheelZoomFactor(delta: Float): Float = 3f.pow(-delta.coerceIn(-10f, 10f))

internal val LocalReaderCtrlWheelPinchSteps = staticCompositionLocalOf<Float?> { null }

/** 还原宿主合成捏合前的滚轮刻度，再应用阅读器倍率；普通双指缩放不转换。 */
internal fun readerPinchZoomFactor(zoom: Float, ctrlPressed: Boolean, stepsPerDoubling: Float?): Float {
    if (!ctrlPressed || stepsPerDoubling == null || !stepsPerDoubling.isFinite() || stepsPerDoubling <= 0f ||
        !zoom.isFinite() || zoom <= 0f) return zoom
    val wheelSteps = ln(zoom) * stepsPerDoubling / ln(2f)
    return readerWheelZoomFactor(-wheelSteps)
}

/** Ctrl＋滚轮围绕鼠标位置缩放，在列表和翻页处理前消费事件。 */
internal fun Modifier.readerWheelZoom(
    state: ReaderZoomState,
    enabled: Boolean,
    maxScale: Float,
    onUserInteraction: () -> Unit,
    enabledAt: (Offset) -> Boolean = { true },
): Modifier = composed {
    val interaction by rememberUpdatedState(onUserInteraction)
    val canZoom by rememberUpdatedState(enabledAt)
    pointerInput(state, enabled, maxScale) {
        if (!enabled) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll || !event.keyboardModifiers.isCtrlPressed) continue
                val wheel = event.changes.firstOrNull {
                    !it.isConsumed && it.type == PointerType.Mouse && it.scrollDelta.y.isFinite() && it.scrollDelta.y != 0f
                } ?: continue
                if (!canZoom(wheel.position)) continue
                interaction()
                state.stopFling()
                state.transform(
                    readerWheelZoomFactor(wheel.scrollDelta.y),
                    wheel.position,
                    Offset.Zero,
                    maxScale
                )
                event.changes.forEach { it.consume() }
            }
        }
    }
}
