package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive

/** AnimatedContent keeps outgoing content alive; it must not keep that route resumed. */
internal class RouteLifecycleOwner : LifecycleOwner {
    // All production updates happen on the composition/host lifecycle thread. Unsafe also allows
    // deterministic JVM lifecycle tests without manufacturing an Android Main dispatcher.
    private val registry = LifecycleRegistry.createUnsafe(this)
    override val lifecycle: Lifecycle get() = registry
    private var destroyed = false

    fun update(parent: Lifecycle.State, active: Boolean) {
        if (destroyed) return
        if (parent == Lifecycle.State.DESTROYED) { destroy(); return }
        val target = if (active) parent else minOf(parent, Lifecycle.State.CREATED)
        // Never move an already-created owner backwards to INITIALIZED.
        if (target == Lifecycle.State.INITIALIZED && registry.currentState >= Lifecycle.State.CREATED) return
        registry.currentState = target
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        if (registry.currentState == Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.CREATED
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

@Composable
internal fun RouteLifecycleScope(active: Boolean, content: @Composable () -> Unit) {
    val parent = LocalLifecycleOwner.current
    val eligible = active && LocalRouteActive.current
    val currentEligible by rememberUpdatedState(eligible)
    val owner = remember(parent) { RouteLifecycleOwner() }
    DisposableEffect(parent, owner) {
        val observer = LifecycleEventObserver { _, _ -> owner.update(parent.lifecycle.currentState, currentEligible) }
        parent.lifecycle.addObserver(observer)
        owner.update(parent.lifecycle.currentState, currentEligible)
        onDispose { parent.lifecycle.removeObserver(observer); owner.destroy() }
    }
    SideEffect { owner.update(parent.lifecycle.currentState, eligible) }
    CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalRouteActive provides eligible, content = content)
}
