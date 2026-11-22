package com.rchdliang.copymanga_neo.multiplatform.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/** 在窗口内容处理点击之前消费两个侧键，每次按下后退键只分发一次返回。 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.desktopMouseNavigation(onBack: () -> Unit): Modifier = composed {
    val back by rememberUpdatedState(onBack)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.button != PointerButton.Back && event.button != PointerButton.Forward) continue
                if (event.type != PointerEventType.Press && event.type != PointerEventType.Release) continue
                event.changes.forEach { it.consume() }
                if (event.button == PointerButton.Back && event.type == PointerEventType.Press) back()
            }
        }
    }
}

/** X11 的 8/9 键在 AWT 中为 6/7，超出 Compose 桌面按下状态支持的五个按钮。 */
internal class X11MouseNavigation(private val onBack: () -> Unit) : MouseAdapter() {
    override fun mousePressed(event: MouseEvent) {
        if (event.button == 6 || event.button == 7) {
            event.consume()
            if (event.button == 6) onBack()
        }
    }

    override fun mouseReleased(event: MouseEvent) {
        if (event.button == 6 || event.button == 7) event.consume()
    }
}

@Composable
fun Modifier.awtMouseNavigation(window: ComposeWindow, onBack: () -> Unit): Modifier {
    if (window.toolkit.javaClass.name != "sun.awt.X11.XToolkit") return desktopMouseNavigation(onBack)
    val back by rememberUpdatedState(onBack)
    DisposableEffect(window) {
        val listener = X11MouseNavigation { back() }
        window.addMouseListener(listener)
        onDispose { window.removeMouseListener(listener) }
    }
    return this
}
