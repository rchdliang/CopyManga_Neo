package com.rchdliang.copymanga_neo.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridLoadMoreEffectTest {
    @Test
    fun missingLayoutDoesNotRequestPages() {
        assertFalse(isGridNearEnd(0, -1))
        assertFalse(isGridNearEnd(1, -1))
    }

    @Test
    fun searchAndNovelShareLastThreeItemsThreshold() {
        assertFalse(isGridNearEnd(22, 18))
        assertTrue(isGridNearEnd(22, 19))
        assertTrue(isGridNearEnd(22, 21))
        assertFalse(isGridNearEnd(42, 21))
        assertTrue(isGridNearEnd(2, 1))
    }
}
