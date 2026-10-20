package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

/** In paged mode a vertical mouse wheel notch advances one comic page. */
internal fun Modifier.readerWheelPaging(
    enabled: Boolean,
    onForward: () -> Unit,
    onBackward: () -> Unit
): Modifier = composed {
    val forward by rememberUpdatedState(onForward)
    val backward by rememberUpdatedState(onBackward)
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        var lastPageAt: Long? = null
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val wheel = event.changes.firstOrNull {
                    it.type == PointerType.Mouse && it.scrollDelta.y != 0f
                } ?: continue
                val now = wheel.uptimeMillis
                val previous = lastPageAt
                if (previous == null || now - previous >= 220L) {
                    lastPageAt = now
                    if (wheel.scrollDelta.y > 0f) forward() else backward()
                }
                event.changes.forEach { it.consume() }
            }
        }
    }
}
