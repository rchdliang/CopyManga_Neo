package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*

/** Attach before the reader's own focusable target, never to a global/window shortcut handler. */
internal fun Modifier.readerKeyboardNavigation(
    enabled: Boolean,
    direction: ReadingDirection,
    blocked: Boolean,
    onNavigate: (ReaderKeyboardCommand) -> Unit
): Modifier = composed {
    var ownsFocus by remember { mutableStateOf(false) }
    val pressed = remember { mutableSetOf<Key>() }
    onFocusChanged {
        ownsFocus = it.isFocused // hasFocus would also intercept focused editors/buttons below us.
        if (!it.isFocused) pressed.clear()
    }.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyUp) {
            pressed.remove(event.key)
        } else if (event.type != KeyEventType.KeyDown) false
        else {
            val key = when (event.key) {
                Key.DirectionLeft -> ReaderNavigationKey.LEFT
                Key.DirectionRight -> ReaderNavigationKey.RIGHT
                Key.DirectionUp -> ReaderNavigationKey.UP
                Key.DirectionDown -> ReaderNavigationKey.DOWN
                Key.PageUp -> ReaderNavigationKey.PAGE_UP
                Key.PageDown -> ReaderNavigationKey.PAGE_DOWN
                Key.Spacebar -> ReaderNavigationKey.SPACE
                else -> ReaderNavigationKey.OTHER
            }
            val command = readerKeyboardCommand(key, direction, ownsFocus, blocked || !enabled,
                event.isShiftPressed, event.isCtrlPressed, event.isAltPressed, event.isMetaPressed)
            if (command == null) false else {
                if (pressed.add(event.key)) onNavigate(command)
                true
            }
        }
    }
}
