package com.rchdliang.copymanga_neo.multiplatform.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiBackDispatcherTest {
    private val event = UiBackEvent(.5f, fromLeft = true)

    @Test fun predictiveCommitStaysWithTheHandlerSelectedAtStart() {
        val dispatcher = UiBackDispatcher()
        val events = mutableListOf<String>()
        var childEnabled = false
        dispatcher.register(onCancelled = { events += "cancel" }) { events += "parent"; true }
        dispatcher.register(enabled = { childEnabled }) { events += "child"; true }
        dispatcher.startGesture(event)
        childEnabled = true
        dispatcher.register { events += "new"; true }
        assertTrue(dispatcher.commitGesture())
        assertFalse(dispatcher.commitGesture())
        assertEquals(listOf("parent"), events)
    }

    @Test fun cancellationNeverRunsNavigationAndRestartCancelsPreviousGesture() {
        val dispatcher = UiBackDispatcher()
        val events = mutableListOf<String>()
        dispatcher.register(
            onStarted = { events += "start" }, onProgressed = { events += "progress" },
            onCancelled = { events += "cancel" }
        ) { events += "commit"; true }
        dispatcher.startGesture(event)
        dispatcher.progressGesture(event)
        dispatcher.startGesture(event)
        dispatcher.cancelGesture()
        dispatcher.cancelGesture()
        assertFalse(dispatcher.commitGesture())
        assertEquals(listOf("start", "progress", "cancel", "start", "cancel"), events)
    }

    @Test fun removingOrDisablingPinnedHandlerCannotPopItsParent() {
        val dispatcher = UiBackDispatcher()
        var parentCalls = 0
        var cancellations = 0
        dispatcher.register { parentCalls++; true }
        val remove = dispatcher.register(onCancelled = { cancellations++ }) { error("Removed handler") }
        dispatcher.startGesture(event)
        remove()
        assertTrue(dispatcher.commitGesture())
        var enabled = true
        dispatcher.register(enabled = { enabled }, onCancelled = { cancellations++ }) { error("Disabled handler") }
        dispatcher.startGesture(event)
        enabled = false
        assertTrue(dispatcher.commitGesture())
        assertEquals(0, parentCalls)
        assertEquals(2, cancellations)
    }

    @Test fun rootWithoutHandlersHasNoNavigationSideEffects() {
        val dispatcher = UiBackDispatcher()
        assertFalse(dispatcher.hasEnabledHandlers)
        assertFalse(dispatcher.dispatchBack())
        dispatcher.startGesture(event)
        dispatcher.progressGesture(event)
        dispatcher.cancelGesture()
        assertFalse(dispatcher.commitGesture())
        assertFalse(dispatcher.isGestureInProgress)
    }

    @Test fun newestEnabledHandlerWinsAndDisposalRestoresParent() {
        val dispatcher = UiBackDispatcher()
        val events = mutableListOf<String>()
        val removeParent = dispatcher.register { events += "parent"; true }
        var childEnabled = false
        val removeChild = dispatcher.register {
            if (childEnabled) { events += "child"; true } else false
        }
        assertTrue(dispatcher.dispatchBack())
        childEnabled = true
        assertTrue(dispatcher.dispatchBack())
        removeChild()
        assertTrue(dispatcher.dispatchBack())
        assertEquals(listOf("parent", "child", "parent"), events)
        removeParent()
        assertFalse(dispatcher.dispatchBack())
    }

    @Test fun handlersCanUnregisterDuringDispatchWithoutConcurrentModification() {
        val dispatcher = UiBackDispatcher()
        var remove: () -> Unit = {}
        remove = dispatcher.register { remove(); true }
        assertTrue(dispatcher.dispatchBack())
        remove()
        assertFalse(dispatcher.dispatchBack())
    }
}
