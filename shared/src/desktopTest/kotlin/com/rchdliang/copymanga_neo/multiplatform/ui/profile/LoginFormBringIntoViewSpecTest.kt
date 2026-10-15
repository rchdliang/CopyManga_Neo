package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class LoginFormBringIntoViewSpecTest {
    private val spec = LoginFormBringIntoViewSpec(cursorMaxHeight = 36f, contextHeight = 56f)

    @Test fun cursorGetsDecorationRoomAndStopsAtItsTarget() {
        val distance = spec.calculateScrollDistance(90f, 20f, 120f)
        assertEquals(8f, distance)
        assertEquals(0f, spec.calculateScrollDistance(90f - distance, 20f, 120f))
        assertEquals(-14f, spec.calculateScrollDistance(4f, 20f, 120f))
    }

    @Test fun checkboxAndWholeFieldKeepNearestEdgeBehavior() {
        assertEquals(0f, spec.calculateScrollDistance(40f, 48f, 100f))
        assertEquals(18f, spec.calculateScrollDistance(70f, 48f, 100f))
        assertEquals(0f, spec.calculateScrollDistance(20f, 64f, 100f))
    }

    @Test fun tinyOrZeroViewportDoesNotAskForImpossiblePadding() {
        assertEquals(5f, spec.calculateScrollDistance(15f, 20f, 40f))
        assertEquals(0f, spec.calculateScrollDistance(10f, 20f, 40f))
        assertEquals(0f, spec.calculateScrollDistance(10f, 20f, 0f))
        assertEquals(0f, spec.calculateScrollDistance(10f, 0f, 40f))
        assertEquals(0f, spec.calculateScrollDistance(-20f, 180f, 100f))
    }

    @Test fun scaledCursorKeepsContextWithinViewport() {
        val large = LoginFormBringIntoViewSpec(cursorMaxHeight = 72f, contextHeight = 112f)
        val distance = large.calculateScrollDistance(150f, 48f, 180f)
        assertEquals(50f, distance)
        assertEquals(0f, large.calculateScrollDistance(150f - distance, 48f, 180f))
    }
}
