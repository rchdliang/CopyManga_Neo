package com.rchdliang.copymanga_neo.multiplatform.platform

import androidx.compose.runtime.*
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import kotlin.test.*

class ComposeDesktopWindowPolicyTest {
    @Test fun restoresEachOriginalPlacement() {
        WindowPlacement.entries.forEach { original ->
            val state = WindowState(placement = original)
            val policy = ComposeDesktopWindowPolicy(state)
            val lease = policy.acquireFullscreen().getOrThrow()
            assertEquals(WindowPlacement.Fullscreen, state.placement)
            lease.close()
            assertEquals(original, state.placement)
        }
    }

    @Test fun overlappingReaderEffectsDoNotRestorePrematurely() {
        val state = WindowState(placement = WindowPlacement.Maximized)
        val policy = ComposeDesktopWindowPolicy(state)
        val orientation = policy.acquireFullscreen().getOrThrow()
        val controls = policy.acquireFullscreen().getOrThrow()
        orientation.close()
        orientation.close() // disposal must be idempotent
        assertEquals(WindowPlacement.Fullscreen, state.placement)
        controls.close()
        assertEquals(WindowPlacement.Maximized, state.placement)
        controls.close()
        assertEquals(WindowPlacement.Maximized, state.placement)
    }

    @Test fun freshReaderSessionCapturesLatestPlacement() {
        val state = WindowState(placement = WindowPlacement.Floating)
        val policy = ComposeDesktopWindowPolicy(state)
        repeat(20) {
            val original = if (it % 2 == 0) WindowPlacement.Floating else WindowPlacement.Maximized
            state.placement = original
            policy.acquireFullscreen().getOrThrow().close()
            assertEquals(original, state.placement)
        }
    }

    @Test fun doesNotUndoExplicitHostPlacementChange() {
        val state = WindowState(placement = WindowPlacement.Floating)
        val policy = ComposeDesktopWindowPolicy(state)
        val lease = policy.acquireFullscreen().getOrThrow()
        state.placement = WindowPlacement.Maximized
        lease.close()
        assertEquals(WindowPlacement.Maximized, state.placement)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun readerCompositionEntryAndDisposalUseObservablePlacementOnly() = runSkikoComposeUiTest {
        val state = WindowState(placement = WindowPlacement.Maximized)
        val policy = ComposeDesktopWindowPolicy(state)
        var reading by mutableStateOf(false)
        setContent {
            if (reading) DisposableEffect(policy) {
                val lease = policy.acquireFullscreen().getOrThrow()
                onDispose { lease.close() }
            }
        }
        repeat(3) {
            runOnIdle { reading = true }
            waitForIdle()
            runOnIdle { assertEquals(WindowPlacement.Fullscreen, state.placement) }
            runOnIdle { reading = false }
            waitForIdle()
            runOnIdle { assertEquals(WindowPlacement.Maximized, state.placement) }
        }
    }

    @Test fun keepAwakeRetainsIndependentLease() {
        val state = WindowState()
        var closed = false
        val policy = ComposeDesktopWindowPolicy(state) { Result.success(AutoCloseable { closed = true }) }
        policy.acquireKeepAwake().getOrThrow().close()
        assertTrue(closed)
        assertEquals(WindowPlacement.Floating, state.placement)
    }
}
