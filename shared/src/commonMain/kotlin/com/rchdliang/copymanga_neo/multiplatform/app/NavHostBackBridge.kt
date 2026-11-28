package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavBackStackEntryInfo
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
    private val isActive: () -> Boolean = { true },
    private val readerMotion: ReaderBackMotion? = null,
    private val currentRouteId: () -> String?
) : NavigationEventDispatcherOwner {
    override val navigationEventDispatcher = NavigationEventDispatcher()
    private var frameworkEnabled by mutableStateOf(false)
    private var alive = true
    private data class Gesture(val routeId: String?, val preview: Boolean, val reader: Boolean)
    private var gesture: Gesture? = null
    private var swallowedGesture = false
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
            enabled = { alive && frameworkEnabled && isActive() },
            onStarted = { event ->
                swallowedGesture = readerMotion?.isCommitting == true
                if (!swallowedGesture) startGesture(event)
            },
            onProgressed = { event ->
                if (gesture?.reader == true) readerMotion?.update(event)
                else if (gesture?.preview == true) input.progress(event)
            },
            onCancelled = { swallowedGesture = false; cancelGesture() }
        ) {
            val started = gesture
            readerMotion?.finishReturn()
            if (swallowedGesture || readerMotion?.isCommitting == true) {
                swallowedGesture = false
            } else if (started != null && started.routeId != currentRouteId()) {
                // A programmatic replacement during the gesture must never pop its new route.
                cancelGesture(animate = false)
            } else if (started?.reader == true && readerMotion != null) {
                gesture = null
                readerMotion.fadeOnCommit { finished ->
                    if (finished && alive && isActive() && started.routeId == currentRouteId()) {
                        input.complete()
                    } else {
                        input.cancel()
                        if (finished) readerMotion.forget(started.routeId)
                    }
                }
            } else {
                input.complete()
                gesture = null
            }
            true
        }
    }

    private fun startGesture(event: UiBackEvent) {
        readerMotion?.finishReturn()
        val routeId = currentRouteId()
        val preview = previewEnabled()
        val history = navigationEventDispatcher.history.value
        val reader = preview && readerMotion != null &&
            history.mergedHistory.getOrNull(history.currentIndex) is NavBackStackEntryInfo &&
            readerMotion.start(routeId, event)
        gesture = Gesture(routeId, preview, reader)
        if (preview) input.start(if (reader) event.copy(progress = 0f) else event)
    }

    private fun cancelGesture(animate: Boolean = true) {
        // NavigationEventInput.cancel() without a start synthesizes one; avoid that side effect.
        val started = gesture
        gesture = null
        if (started?.preview != true) return
        if (started.reader && animate && alive && started.routeId == currentRouteId()) {
            readerMotion!!.returnToReader(input::cancel)
        } else {
            if (started.reader) readerMotion?.forget(started.routeId)
            input.cancel()
        }
    }

    fun dispose() {
        if (!alive) return
        alive = false
        unregister() // Cancels a pinned UiBackDispatcher gesture before detaching the input.
        cancelGesture(animate = false)
        readerMotion?.let { it.forget(it.entryId) }
        navigationEventDispatcher.removeInput(input)
        navigationEventDispatcher.dispose()
    }
}

private fun UiBackEvent.frameworkEvent() = NavigationEvent(
    swipeEdge = if (fromLeft) NavigationEvent.EDGE_LEFT else NavigationEvent.EDGE_RIGHT,
    progress = progress
)
