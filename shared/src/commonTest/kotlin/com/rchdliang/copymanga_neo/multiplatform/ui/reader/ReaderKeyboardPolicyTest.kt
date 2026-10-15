package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import kotlin.test.*

class ReaderKeyboardPolicyTest {
    private fun command(key: ReaderNavigationKey, direction: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
                        shift: Boolean = false) = readerKeyboardCommand(key, direction, true, false, shift)
    @Test fun arrowsRespectPhysicalDirectionInBothPagedModes() {
        for (direction in listOf(ReadingDirection.LEFT_TO_RIGHT, ReadingDirection.RIGHT_TO_LEFT)) {
            assertEquals(direction == ReadingDirection.LEFT_TO_RIGHT, command(ReaderNavigationKey.RIGHT, direction)?.forward)
            assertEquals(direction == ReadingDirection.RIGHT_TO_LEFT, command(ReaderNavigationKey.LEFT, direction)?.forward)
            assertEquals(ReaderKeyboardMovement.PAGE, command(ReaderNavigationKey.LEFT, direction)?.movement)
            assertNull(command(ReaderNavigationKey.UP, direction))
            assertNull(command(ReaderNavigationKey.DOWN, direction))
        }
    }
    @Test fun pageKeysAndSpaceFollowLogicalOrderIncludingRtl() {
        for (direction in ReadingDirection.entries) {
            assertEquals(true, command(ReaderNavigationKey.PAGE_DOWN, direction)?.forward)
            assertEquals(false, command(ReaderNavigationKey.PAGE_UP, direction)?.forward)
            assertEquals(true, command(ReaderNavigationKey.SPACE, direction)?.forward)
            assertEquals(false, command(ReaderNavigationKey.SPACE, direction, shift = true)?.forward)
        }
    }
    @Test fun longImageModeScrollsInsteadOfSkippingAnImage() {
        val direction = ReadingDirection.TOP_TO_BOTTOM
        assertEquals(ReaderKeyboardCommand(true, ReaderKeyboardMovement.SMALL_SCROLL), command(ReaderNavigationKey.DOWN, direction))
        assertEquals(ReaderKeyboardCommand(false, ReaderKeyboardMovement.SMALL_SCROLL), command(ReaderNavigationKey.UP, direction))
        assertEquals(ReaderKeyboardCommand(true, ReaderKeyboardMovement.VIEWPORT_SCROLL), command(ReaderNavigationKey.PAGE_DOWN, direction))
        assertNull(command(ReaderNavigationKey.LEFT, direction))
        assertNull(command(ReaderNavigationKey.RIGHT, direction))
    }
    @Test fun editorsModalsAndOtherFocusedControlsKeepTheirKeys() {
        for (key in ReaderNavigationKey.entries) {
            assertNull(readerKeyboardCommand(key, ReadingDirection.RIGHT_TO_LEFT, false, false))
            assertNull(readerKeyboardCommand(key, ReadingDirection.RIGHT_TO_LEFT, true, true))
        }
    }
    @Test fun systemAndSelectionShortcutsAreNotConsumed() {
        for (key in ReaderNavigationKey.entries) {
            assertNull(readerKeyboardCommand(key, ReadingDirection.RIGHT_TO_LEFT, true, false, control = true))
            assertNull(readerKeyboardCommand(key, ReadingDirection.RIGHT_TO_LEFT, true, false, alt = true))
            assertNull(readerKeyboardCommand(key, ReadingDirection.RIGHT_TO_LEFT, true, false, meta = true))
            if (key != ReaderNavigationKey.SPACE) assertNull(command(key, shift = true))
        }
        assertNull(command(ReaderNavigationKey.OTHER))
    }
}
