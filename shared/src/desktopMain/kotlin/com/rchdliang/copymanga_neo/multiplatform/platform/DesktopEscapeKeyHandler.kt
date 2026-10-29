package com.rchdliang.copymanga_neo.multiplatform.platform

import androidx.compose.ui.input.key.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

enum class DesktopEscapeEvent { DOWN, UP, OTHER }

/** Child-first fallback: an orphan release (for example after a popup closes) never navigates. */
class DesktopEscapeKeyHandler {
    private var pressed = false
    private var owned = false
    private var freshDown = false
    private var releasedOwned = false
    private var windowBackEnabled = false
    private val windowInput = object : NavigationEventInput() {
        override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
            windowBackEnabled = hasEnabledHandlers
        }
        fun back() = dispatchOnBackCompleted()
    }

    /** 接入窗口原生返回入口，窗口弹出层先关闭，再交给应用的页面栈。 */
    @Composable
    fun WindowNavigationEffect() {
        val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
        DisposableEffect(dispatcher) {
            dispatcher?.addInput(windowInput)
            onDispose {
                dispatcher?.removeInput(windowInput)
                windowBackEnabled = false
            }
        }
    }

    /** 窗口预处理只记录按键，让弹窗和获得焦点的控件先处理 Esc。 */
    fun observe(event: KeyEvent) {
        if (event.key == Key.Escape) observe(event.escapeEvent())
    }

    /** 窗口级兜底不依赖页面焦点，与系统返回共用同一个分发器。 */
    fun handle(event: KeyEvent, dispatchBack: () -> Boolean): Boolean =
        event.key == Key.Escape && handle(
            event.escapeEvent(),
            event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || event.isShiftPressed,
            {
                if (windowBackEnabled) {
                    windowInput.back()
                    true
                } else dispatchBack()
            }
        )

    /** Observe before children, without consuming or navigating. A consumed down still starts a press. */
    fun observe(event: DesktopEscapeEvent) {
        when (event) {
            DesktopEscapeEvent.DOWN -> { freshDown = !pressed; pressed = true; releasedOwned = false }
            DesktopEscapeEvent.UP -> {
                releasedOwned = owned
                pressed = false; owned = false; freshDown = false
            }
            DesktopEscapeEvent.OTHER -> Unit
        }
    }

    /** Called only if the focused child did not consume this event. */
    fun handle(event: DesktopEscapeEvent, modified: Boolean, dispatchBack: () -> Boolean): Boolean {
        if (event == DesktopEscapeEvent.UP) {
            val consumed = releasedOwned
            releasedOwned = false
            return consumed
        }
        if (event != DesktopEscapeEvent.DOWN) return false
        if (!freshDown) return owned
        freshDown = false
        owned = !modified && dispatchBack()
        return owned
    }

    fun reset() { pressed = false; owned = false; freshDown = false; releasedOwned = false }
}

private fun KeyEvent.escapeEvent(): DesktopEscapeEvent = when (type) {
    KeyEventType.KeyDown -> DesktopEscapeEvent.DOWN
    KeyEventType.KeyUp -> DesktopEscapeEvent.UP
    else -> DesktopEscapeEvent.OTHER
}
