package com.rchdliang.copymanga_neo.multiplatform.ui.components

import kotlin.test.*

class RouteProgressFlushGateTest {
    @Test fun previewOnlyDisposalNeverFlushes() {
        val gate = RouteProgressFlushGate()
        assertFalse(gate.leave())
        assertFalse(gate.leave())
    }

    @Test fun departureAndLifecyclePauseShareOneFlush() {
        val gate = RouteProgressFlushGate()
        gate.resume()
        assertTrue(gate.leave())
        assertFalse(gate.leave())
        gate.resume()
        assertTrue(gate.leave())
        assertFalse(gate.leave())
    }

    @Test fun canceledGestureDoesNotEndActiveTenure() {
        val gate = RouteProgressFlushGate()
        gate.resume()
        // Predictive progress/cancel does not change committed activity or invoke leave.
        gate.resume()
        assertTrue(gate.leave())
        assertFalse(gate.leave())
    }
    @Test fun pausedReaderCannotRecordAfterFlushAndResumeAllowsNewProgress() {
        val gate = RouteProgressFlushGate()
        gate.resume()
        assertTrue(routeProgressAllowed(true, androidx.lifecycle.Lifecycle.State.RESUMED))
        assertTrue(gate.leave())
        for (state in androidx.lifecycle.Lifecycle.State.entries) {
            if (state != androidx.lifecycle.Lifecycle.State.RESUMED) assertFalse(routeProgressAllowed(true, state))
            assertFalse(routeProgressAllowed(false, state))
        }
        gate.resume()
        assertTrue(routeProgressAllowed(true, androidx.lifecycle.Lifecycle.State.RESUMED))
        assertTrue(gate.leave())
    }

}
