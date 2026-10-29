package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.*

/** 在子级翻页、缩放和长按手势之前接管鼠标右键，松开后打开设置。 */
internal fun Modifier.readerContextClick(enabled: Boolean, onOpenSettings: () -> Unit): Modifier = composed {
    val currentEnabled by rememberUpdatedState(enabled)
    val openSettings by rememberUpdatedState(onOpenSettings)
    pointerInput(Unit) {
        var handlingRightClick = false
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!handlingRightClick && currentEnabled &&
                    event.type == PointerEventType.Press && event.buttons.isSecondaryPressed &&
                    !event.buttons.isPrimaryPressed &&
                    event.changes.any { it.type == PointerType.Mouse && !it.isConsumed }
                ) {
                    handlingRightClick = true
                }
                if (!handlingRightClick) continue
                event.changes.forEach { it.consume() }
                if (!event.buttons.isSecondaryPressed) {
                    handlingRightClick = false
                    val position = event.changes.firstOrNull()?.position
                    if (currentEnabled && event.type == PointerEventType.Release && position != null &&
                        position.x >= 0 && position.x < size.width &&
                        position.y >= 0 && position.y < size.height
                    ) openSettings()
                }
            }
        }
    }
}
