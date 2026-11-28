package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackEvent

/**
 * Framework adapter. Host input still enters UiBackDispatcher exactly once. The private
 * framework dispatcher has no platform parent/input, so NavHost cannot outrank existing modals.
 * Construct before destination content registers its nested handlers; keep this registration
 * stable across route changes. NavController remains the only owner of the outer route stack.
 */
internal class NavHostBackBridge(
    uiDispatcher: UiBackDispatcher,
    private val previewEnabled: () -> Boolean,
    private val currentRouteId: () -> String?
) : NavigationEventDispatcherOwner {
    override val navigationEventDispatcher = NavigationEventDispatcher()
    private var frameworkEnabled by mutableStateOf(false)
    private var alive = true
    private data class Gesture(val routeId: String?, val preview: Boolean)
    private var gesture: Gesture? = null
    private val input = object : NavigationEventInput() {
        override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
            frameworkEnabled = hasEnabledHandlers
        }
        fun start(event: UiBackEvent) = dispatchOnBackStarted(event.frameworkEvent())
        fun progress(event: UiBackEvent) = dispatchOnBackProgressed(event.frameworkEvent())
        fun cancel() = dispatchOnBackCancelled()
        fun complete() = dispatchOnBackCompleted()
    }
    private val unregister: () -> Unit

    init {
        navigationEventDispatcher.addInput(input)
        unregister = uiDispatcher.register(
            enabled = { alive && frameworkEnabled },
            onStarted = { event ->
                val started = Gesture(currentRouteId(), previewEnabled())
                gesture = started
                if (started.preview) input.start(event)
            },
            onProgressed = { event -> if (gesture?.preview == true) input.progress(event) },
            onCancelled = { cancelGesture() }
        ) {
            val started = gesture
            if (started != null && started.routeId != currentRouteId()) {
                // A programmatic replacement during the gesture must never pop its new route.
                cancelGesture()
            } else {
                input.complete()
                gesture = null
            }
            true
        }
    }

    private fun cancelGesture() {
        // NavigationEventInput.cancel() without a start synthesizes one; avoid that side effect.
        if (gesture?.preview == true) input.cancel()
        gesture = null
    }

    fun dispose() {
        if (!alive) return
        alive = false
        unregister() // Cancels a pinned UiBackDispatcher gesture before detaching the input.
        cancelGesture()
        navigationEventDispatcher.removeInput(input)
        navigationEventDispatcher.dispose()
    }
}

private fun UiBackEvent.frameworkEvent() = NavigationEvent(
    swipeEdge = if (fromLeft) NavigationEvent.EDGE_LEFT else NavigationEvent.EDGE_RIGHT,
    progress = progress
)
