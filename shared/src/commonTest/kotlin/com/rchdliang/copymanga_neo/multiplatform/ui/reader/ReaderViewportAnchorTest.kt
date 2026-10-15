package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import com.rchdliang.copymanga_neo.domain.reading.ReadingPositionPolicy
import kotlin.test.*

class ReaderViewportAnchorTest {
    @Test fun widthChangeUsesOldExtentInsteadOfReflowedFraction() {
        val tracker = ReaderViewportAnchor()
        assertNull(tracker.observe(1000, ReaderViewportSample("image", 1500, 3000), true))
        val correction = assertNotNull(tracker.observe(500, ReaderViewportSample("image", 1500, 1500), true))
        assertEquals("image", correction.key)
        assertEquals(0.5, correction.fraction)
        assertEquals(750, ReadingPositionPolicy.offset(correction.fraction, 1500))
    }

    @Test fun normalImageMeasurementAtSameWidthNeverMovesReader() {
        val tracker = ReaderViewportAnchor()
        tracker.observe(1000, ReaderViewportSample("image", 100, 800), true)
        assertNull(tracker.observe(1000, ReaderViewportSample("image", 100, 4000), true))
        val correction = assertNotNull(tracker.observe(500, ReaderViewportSample("image", 100, 2000), true))
        assertEquals(0.025, correction.fraction)
    }

    @Test fun widthShrinkCanRestoreOldImageEvenIfLayoutMovedToNextItem() {
        val tracker = ReaderViewportAnchor()
        tracker.observe(1000, ReaderViewportSample("old-image", 1800, 2000), true)
        assertEquals(ReaderViewportCorrection("old-image", 0.9),
            tracker.observe(500, ReaderViewportSample("next-image", 800, 1000), true))
    }

    @Test fun scrollPendingJumpOrInactiveRouteInvalidatesOldAnchor() {
        val ineligible = listOf(
            readerViewportResizeAllowed(true, true, scrolling = true, pendingJump = false, interactionBusy = false),
            readerViewportResizeAllowed(true, true, scrolling = false, pendingJump = true, interactionBusy = false),
            readerViewportResizeAllowed(true, false, scrolling = false, pendingJump = false, interactionBusy = false),
            readerViewportResizeAllowed(true, true, scrolling = false, pendingJump = false, interactionBusy = true)
        )
        ineligible.forEach { allowed ->
            assertFalse(allowed)
            val tracker = ReaderViewportAnchor()
            tracker.observe(1000, ReaderViewportSample("old", 1000, 2000), true)
            assertNull(tracker.observe(800, ReaderViewportSample("new", 0, 1000), allowed))
            assertNull(tracker.observe(500, ReaderViewportSample("new", 0, 1000), true))
        }
    }

    @Test fun explicitInteractionOrModeChangeDiscardsPreviousSample() {
        val tracker = ReaderViewportAnchor()
        tracker.observe(1000, ReaderViewportSample("old", 1000, 2000), true)
        tracker.invalidate()
        assertNull(tracker.observe(500, ReaderViewportSample("new", 0, 1000), true))
    }

    @Test fun unreadyImageDoesNotSeedFractionalResizeRestore() {
        val tracker = ReaderViewportAnchor()
        tracker.observe(1000, null, true)
        assertNull(tracker.observe(500, ReaderViewportSample("image", 0, 1000), true))
    }
}
