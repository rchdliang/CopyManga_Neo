package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.test.*

class RouteLifecycleOwnerTest {
    @Test fun outgoingRouteStopsWhileIncomingResumesAndHostStillResumed() {
        val outgoing = RouteLifecycleOwner()
        val incoming = RouteLifecycleOwner()
        val events = mutableListOf<Lifecycle.Event>()
        outgoing.lifecycle.addObserver(LifecycleEventObserver { _, event -> events += event })
        outgoing.update(Lifecycle.State.RESUMED, true)
        outgoing.update(Lifecycle.State.RESUMED, false)
        incoming.update(Lifecycle.State.RESUMED, true)
        assertEquals(Lifecycle.State.CREATED, outgoing.lifecycle.currentState)
        assertEquals(Lifecycle.State.RESUMED, incoming.lifecycle.currentState)
        assertEquals(listOf(Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME,
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP), events)
    }

    @Test fun inactiveRouteCannotResumeWhenHostReturnsAndCanReactivateAfterCancelledTransition() {
        val route = RouteLifecycleOwner()
        route.update(Lifecycle.State.RESUMED, true)
        route.update(Lifecycle.State.CREATED, false)
        route.update(Lifecycle.State.RESUMED, false)
        assertEquals(Lifecycle.State.CREATED, route.lifecycle.currentState)
        route.update(Lifecycle.State.RESUMED, true)
        assertEquals(Lifecycle.State.RESUMED, route.lifecycle.currentState)
        route.update(Lifecycle.State.STARTED, true)
        assertEquals(Lifecycle.State.STARTED, route.lifecycle.currentState)
    }

    @Test fun disposalIsTerminalEvenForNeverStartedRoute() {
        val route = RouteLifecycleOwner()
        route.destroy()
        route.update(Lifecycle.State.RESUMED, true)
        route.destroy()
        assertEquals(Lifecycle.State.DESTROYED, route.lifecycle.currentState)
    }
    @Test fun selectedNestedTabCannotOutliveInactiveOrDestroyedOuterRoute() {
        val parent = RouteLifecycleOwner()
        val child = RouteLifecycleOwner()
        parent.lifecycle.addObserver(LifecycleEventObserver { _, _ ->
            child.update(parent.lifecycle.currentState, active = true)
        })
        parent.update(Lifecycle.State.RESUMED, true)
        assertEquals(Lifecycle.State.RESUMED, child.lifecycle.currentState)
        parent.update(Lifecycle.State.RESUMED, false)
        assertEquals(Lifecycle.State.CREATED, child.lifecycle.currentState)
        parent.destroy()
        assertEquals(Lifecycle.State.DESTROYED, child.lifecycle.currentState)
        child.update(Lifecycle.State.RESUMED, true)
        assertEquals(Lifecycle.State.DESTROYED, child.lifecycle.currentState)
    }

    @Test fun pinnedOutgoingHandlerCancelsInsteadOfFallingThroughToNewRoute() {
        val dispatcher = com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher()
        var active = true
        var rootCalls = 0
        var routeCalls = 0
        var cancellations = 0
        dispatcher.register { rootCalls++; true }
        dispatcher.register(enabled = { active }, onCancelled = { cancellations++ }) { routeCalls++; true }
        dispatcher.startGesture(com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackEvent(.1f, true))
        active = false
        assertTrue(dispatcher.commitGesture())
        assertEquals(1, cancellations)
        assertEquals(0, routeCalls)
        assertEquals(0, rootCalls)
        assertTrue(dispatcher.dispatchBack())
        assertEquals(1, rootCalls)
    }

}
