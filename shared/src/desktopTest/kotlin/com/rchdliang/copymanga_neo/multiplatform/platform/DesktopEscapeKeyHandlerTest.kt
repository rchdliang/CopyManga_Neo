package com.rchdliang.copymanga_neo.multiplatform.platform

import kotlin.test.*

class DesktopEscapeKeyHandlerTest {
    private fun DesktopEscapeKeyHandler.dispatch(event: DesktopEscapeEvent, modified: Boolean, back: () -> Boolean): Boolean {
        observe(event)
        return handle(event, modified, back)
    }

    @Test fun childConsumedInitialPressCannotBecomeNavigationOnAutoRepeat() {
        val keys = DesktopEscapeKeyHandler()
        keys.observe(DesktopEscapeEvent.DOWN) // Popup consumes the initial down; final handler never runs.
        repeat(4) {
            assertFalse(keys.dispatch(DesktopEscapeEvent.DOWN, false) { fail("Repeat must not pop underlying route") })
        }
        assertFalse(keys.dispatch(DesktopEscapeEvent.UP, false) { fail("Unowned release") })
        assertTrue(keys.dispatch(DesktopEscapeEvent.DOWN, false) { true })
    }

    @Test fun popupOwnedPressCannotPopUnderlyingRouteOnRelease() {
        val keys = DesktopEscapeKeyHandler()
        assertFalse(keys.dispatch(DesktopEscapeEvent.UP, false) { fail("Orphan release must not navigate") })
    }

    @Test fun onePhysicalPressNavigatesOnlyOnceAndConsumesOwnedRelease() {
        val keys = DesktopEscapeKeyHandler()
        var backs = 0
        repeat(5) { assertTrue(keys.dispatch(DesktopEscapeEvent.DOWN, false) { backs++; true }) }
        assertEquals(1, backs)
        assertTrue(keys.dispatch(DesktopEscapeEvent.UP, false) { fail("Release must not navigate") })
        assertTrue(keys.dispatch(DesktopEscapeEvent.DOWN, false) { backs++; true })
        assertEquals(2, backs)
    }

    @Test fun modifiersAndUnhandledPressCannotAcquireNavigationDuringRepeat() {
        val keys = DesktopEscapeKeyHandler()
        assertFalse(keys.dispatch(DesktopEscapeEvent.DOWN, true) { fail("Modified shortcuts belong elsewhere") })
        assertFalse(keys.dispatch(DesktopEscapeEvent.DOWN, false) { fail("Modifier release is not a new key press") })
        assertFalse(keys.dispatch(DesktopEscapeEvent.UP, false) { fail("Not owned") })
        assertFalse(keys.dispatch(DesktopEscapeEvent.DOWN, false) { false })
        assertFalse(keys.dispatch(DesktopEscapeEvent.DOWN, false) { fail("New handler must await another press") })
        assertFalse(keys.dispatch(DesktopEscapeEvent.UP, false) { fail("Not owned") })
    }

    @Test fun focusLossClearsOwnershipWithoutNavigatingOnReturn() {
        val keys = DesktopEscapeKeyHandler()
        assertTrue(keys.dispatch(DesktopEscapeEvent.DOWN, false) { true })
        keys.reset()
        assertFalse(keys.dispatch(DesktopEscapeEvent.UP, false) { fail("Stale release") })
        assertFalse(keys.dispatch(DesktopEscapeEvent.OTHER, false) { fail("Unknown event") })
        assertTrue(keys.dispatch(DesktopEscapeEvent.DOWN, false) { true })
    }
}
