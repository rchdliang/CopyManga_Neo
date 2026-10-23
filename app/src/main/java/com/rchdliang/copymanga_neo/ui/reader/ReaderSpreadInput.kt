package com.rchdliang.copymanga_neo.ui.reader

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import kotlin.math.abs
import kotlin.math.exp

/** Only the reader focus target owns shortcuts; descendant editors and modal controls do not. */
internal fun Modifier.readerSpreadKeyboard(
    enabled: Boolean, blocked: Boolean, rtl: Boolean, zoom: ReaderZoomState?,
    onInteraction: () -> Unit, onForward: () -> Unit, onBackward: () -> Unit
): Modifier = composed {
    var ownsFocus by remember { mutableStateOf(false) }
    val pressed = remember { mutableSetOf<Key>() }
    LaunchedEffect(enabled, blocked) { if (!enabled || blocked) pressed.clear() }
    onFocusChanged { ownsFocus = it.isFocused; if (!ownsFocus) pressed.clear() }
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp) return@onPreviewKeyEvent pressed.remove(event.key)
            if (!enabled || blocked || !ownsFocus || event.isAltPressed || event.isMetaPressed) return@onPreviewKeyEvent false
            val zoomKey = event.key in listOf(Key.Equals, Key.Plus, Key.Minus, Key.Zero)
            if (event.isCtrlPressed && !zoomKey) return@onPreviewKeyEvent false
            if (event.isShiftPressed && event.key !in listOf(Key.Equals, Key.Plus, Key.Spacebar)) return@onPreviewKeyEvent false
            val recognized = zoomKey || event.key in listOf(Key.DirectionLeft, Key.DirectionRight,
                Key.PageUp, Key.PageDown, Key.Spacebar) ||
                (zoom?.zoomed == true && event.key in listOf(Key.DirectionUp, Key.DirectionDown))
            if (!recognized) return@onPreviewKeyEvent false
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (!pressed.add(event.key)) return@onPreviewKeyEvent true
            onInteraction()
            when (event.key) {
                Key.Zero -> zoom?.reset()
                Key.Equals, Key.Plus, Key.Minus -> zoom?.let {
                    it.stopFling(); it.transform(if (event.key == Key.Minus) 0.8f else 1.25f, it.viewportCenter(), Offset.Zero, 5f)
                }
                Key.PageDown -> onForward()
                Key.PageUp -> onBackward()
                Key.Spacebar -> if (event.isShiftPressed) onBackward() else onForward()
                else -> if (zoom?.zoomed == true) {
                    zoom.stopFling()
                    zoom.pan(when (event.key) {
                        Key.DirectionLeft -> Offset(64f, 0f)
                        Key.DirectionRight -> Offset(-64f, 0f)
                        Key.DirectionUp -> Offset(0f, 64f)
                        else -> Offset(0f, -64f)
                    })
                } else when (event.key) {
                    Key.DirectionLeft -> if (rtl) onForward() else onBackward()
                    Key.DirectionRight -> if (rtl) onBackward() else onForward()
                    else -> return@onPreviewKeyEvent false
                }
            }
            true
        }
}

/** Accumulate small trackpad deltas; one page per deliberate wheel burst. */
internal fun Modifier.readerSpreadWheel(
    zoom: ReaderZoomState, enabled: Boolean, onInteraction: () -> Unit,
    onForward: () -> Unit, onBackward: () -> Unit, rtl: Boolean = false
): Modifier = composed {
    val currentZoom by rememberUpdatedState(zoom)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentRtl by rememberUpdatedState(rtl)
    val interaction by rememberUpdatedState(onInteraction)
    val forward by rememberUpdatedState(onForward)
    val backward by rememberUpdatedState(onBackward)
    pointerInput(Unit) {
        var accumulated = 0f
        var lastEvent = 0L
        var lastPage: Long? = null
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!currentEnabled || event.type != PointerEventType.Scroll || event.keyboardModifiers.isAltPressed || event.keyboardModifiers.isMetaPressed) continue
                val change = event.changes.firstOrNull { !it.isConsumed && it.scrollDelta != Offset.Zero } ?: continue
                val delta = change.scrollDelta
                if (!delta.x.isFinite() || !delta.y.isFinite()) continue
                val zoom = currentZoom
                interaction(); zoom.stopFling()
                if (event.keyboardModifiers.isCtrlPressed) {
                    zoom.transform(exp(-delta.y.coerceIn(-10f, 10f) * 0.12f), change.position, Offset.Zero, 5f)
                } else if (zoom.zoomed) {
                    zoom.pan(Offset(-delta.x * 40f, -delta.y * 40f))
                } else {
                    if (change.uptimeMillis - lastEvent > 180) accumulated = 0f
                    accumulated += if (abs(delta.y) >= abs(delta.x)) delta.y else if (currentRtl) -delta.x else delta.x
                    if (abs(accumulated) >= 1f && (lastPage?.let { change.uptimeMillis - it >= 350 } ?: true)) {
                        if (accumulated > 0) forward() else backward()
                        accumulated = 0f; lastPage = change.uptimeMillis
                    }
                    lastEvent = change.uptimeMillis
                }
                event.changes.forEach { it.consume() }
            }
        }
    }
}
