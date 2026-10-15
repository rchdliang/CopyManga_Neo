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
    private val currentRouteId: () -> String?
) : NavigationEventDispatcherOwner {
    override val navigationEventDispatcher = NavigationEventDispatcher()
    private var frameworkEnabled by mutableStateOf(false)
    private var alive = true
    private var predictive = false
    private var pinnedRoute: String? = null
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
                predictive = true
                pinnedRoute = currentRouteId()
                input.start(event)
            },
            onProgressed = { event -> if (predictive) input.progress(event) },
            onCancelled = { cancelPredictive() }
        ) {
            if (predictive && pinnedRoute != currentRouteId()) {
                // A programmatic replacement during the gesture must never pop its new route.
                cancelPredictive()
            } else {
                input.complete()
                predictive = false
                pinnedRoute = null
            }
            true
        }
    }

    private fun cancelPredictive() {
        // NavigationEventInput.cancel() without a start synthesizes one; avoid that side effect.
        if (predictive) input.cancel()
        predictive = false
        pinnedRoute = null
    }

    fun dispose() {
        if (!alive) return
        alive = false
        unregister() // Cancels a pinned UiBackDispatcher gesture before detaching the input.
        cancelPredictive()
        navigationEventDispatcher.removeInput(input)
        navigationEventDispatcher.dispose()
    }
}

private fun UiBackEvent.frameworkEvent() = NavigationEvent(
    swipeEdge = if (fromLeft) NavigationEvent.EDGE_LEFT else NavigationEvent.EDGE_RIGHT,
    progress = progress
)
