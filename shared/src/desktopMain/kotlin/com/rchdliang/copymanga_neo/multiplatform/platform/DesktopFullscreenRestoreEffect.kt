package com.rchdliang.copymanga_neo.multiplatform.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import java.awt.EventQueue

/**
 * Windows/AWT can restore logical bounds as physical pixels after exclusive fullscreen.
 * Java's bounds then look correct while the native window is smaller at non-100% DPI.
 */
@Composable
fun DesktopFullscreenRestoreEffect(
    window: ComposeWindow,
    placement: WindowPlacement,
    onRestored: () -> Unit = {}
) {
    val history = remember(window) { FullscreenPlacementHistory(placement) }
    val restoredCallback = rememberUpdatedState(onRestored)
    LaunchedEffect(window, placement) {
        val restore = history.previous == WindowPlacement.Fullscreen && placement == WindowPlacement.Floating
        history.previous = placement
        if (!restore) return@LaunchedEffect

        // Leave the placement update's render/composition callback before touching the peer.
        // Do not wait for a frame: an idle page might not render another one after restoration.
        // A newer fullscreen/maximize request takes precedence.
        EventQueue.invokeLater {
            if (!window.isDisplayable || window.placement != WindowPlacement.Floating) return@invokeLater
            val transform = window.graphicsConfiguration.defaultTransform
            if (System.getProperty("os.name").startsWith("Windows") &&
                (transform.scaleX != 1.0 || transform.scaleY != 1.0)) {
                val bounds = window.bounds
                // Component.setBounds is a no-op for identical logical bounds. This synchronous
                // pair forces the peer to apply DPI conversion again, including window position.
                window.setBounds(bounds.x, bounds.y, bounds.width + 1, bounds.height)
                window.bounds = bounds
                window.validate()
            }
            restoredCallback.value()
        }
    }
}

private class FullscreenPlacementHistory(var previous: WindowPlacement)
