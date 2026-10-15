package com.rchdliang.copymanga_neo.multiplatform.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeBackBridgeTest {
    @Test fun unconnectedAndRootBridgesDoNotClaimInput() {
        val bridge = NativeBackBridge()
        assertFalse(bridge.beginGesture(0f))
        assertFalse(bridge.performBack())
        bridge.connect(UiBackDispatcher())
        assertFalse(bridge.canHandleBack)
        assertFalse(bridge.beginGesture(.5f))
    }

    @Test fun cancelAndShortSwipeDoNotNavigateButCompletedSwipeDoesOnce() {
        val bridge = NativeBackBridge()
        val dispatcher = UiBackDispatcher()
        var commits = 0
        var cancels = 0
        dispatcher.register(onCancelled = { cancels++ }) { commits++; true }
        bridge.connect(dispatcher)
        assertTrue(bridge.beginGesture(0f))
        bridge.updateGesture(.7f)
        assertEquals(0, commits)
        bridge.cancelGesture()
        assertEquals(1, cancels)
        bridge.beginGesture(0f)
        bridge.finishGesture(.1f, .1f)
        assertEquals(2, cancels)
        bridge.beginGesture(0f)
        bridge.finishGesture(.6f, .1f)
        bridge.finishGesture(.6f, .1f)
        assertEquals(1, commits)
    }

    @Test fun disconnectAndReconnectCannotCommitOldGestureToNewDispatcher() {
        val bridge = NativeBackBridge()
        val first = UiBackDispatcher()
        val second = UiBackDispatcher()
        var cancels = 0
        var commits = 0
        first.register(onCancelled = { cancels++ }) { error("Disconnected owner") }
        second.register { commits++; true }
        bridge.connect(first)
        bridge.beginGesture(0f)
        bridge.connect(second)
        bridge.finishGesture(.9f, 1f)
        assertEquals(1, cancels)
        assertEquals(0, commits)
        assertTrue(bridge.performBack())
        assertEquals(1, commits)
        bridge.disconnect()
        assertFalse(bridge.performBack())
    }

    @Test fun rightEdgeIsRetainedForProgressAndNextGestureCanUseLeftEdge() {
        val bridge = NativeBackBridge()
        val dispatcher = UiBackDispatcher()
        val events = mutableListOf<UiBackEvent>()
        var commits = 0
        dispatcher.register(onStarted = { events += it }, onProgressed = { events += it }) {
            commits++; true
        }
        bridge.connect(dispatcher)
        assertTrue(bridge.beginGesture(0f, fromLeft = false))
        bridge.updateGesture(.4f)
        assertEquals(listOf(false, false), events.map { it.fromLeft })
        bridge.finishGesture(.4f, .2f)
        assertEquals(1, commits)
        bridge.beginGesture(0f, fromLeft = true)
        bridge.updateGesture(.2f)
        assertEquals(listOf(false, false, true, true), events.map { it.fromLeft })
        bridge.cancelGesture()
        assertEquals(1, commits)
    }

    @Test fun gesturePolicyRejectsInvalidAndReverseMotion() {
        assertEquals(0f, normalizedBackProgress(Float.NaN))
        assertEquals(1f, normalizedBackProgress(2f))
        assertFalse(shouldCommitNativeBack(.9f, -.1f))
        assertFalse(shouldCommitNativeBack(0f, 2f))
        assertFalse(shouldCommitNativeBack(.5f, Float.NaN))
        assertTrue(shouldCommitNativeBack(.3f, 0f))
        assertTrue(shouldCommitNativeBack(.1f, 1f))
    }
}
