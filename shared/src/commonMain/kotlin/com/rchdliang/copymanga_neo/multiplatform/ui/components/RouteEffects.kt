package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope

/** Composition includes predictive previews and outgoing animations, not just committed routes. */
@Composable
internal fun ActiveRouteLaunchedEffect(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val active = LocalRouteActive.current
    LaunchedEffect(active, *keys) { if (active) block() }
}

/** One flush per active/background tenure; a never-active preview cannot flush shared storage. */
internal class RouteProgressFlushGate {
    private var pending = false
    fun resume() { pending = true }
    fun leave(): Boolean = pending.also { pending = false }
}

@Composable
internal fun ActiveRouteProgressFlushEffect(key: Any, flush: () -> Unit) {
    val active = LocalRouteActive.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestFlush by rememberUpdatedState(flush)
    DisposableEffect(key, active, lifecycle) {
        val gate = RouteProgressFlushGate()
        if (active) gate.resume()
        fun flushOnce() { if (gate.leave()) latestFlush() }
        val observer = LifecycleEventObserver { _, event ->
            if (active) when (event) {
                Lifecycle.Event.ON_RESUME -> gate.resume()
                Lifecycle.Event.ON_PAUSE -> flushOnce()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); flushOnce() }
    }
}

/** Writes are only eligible while actually resumed, including during host lifecycle changes. */
internal fun routeProgressAllowed(active: Boolean, lifecycle: Lifecycle.State): Boolean =
    active && lifecycle.isAtLeast(Lifecycle.State.RESUMED)

@Composable
internal fun ActiveRouteReportingEffect(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val active = LocalRouteActive.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(active, state, *keys) {
        if (routeProgressAllowed(active, lifecycle.currentState)) block()
    }
}
