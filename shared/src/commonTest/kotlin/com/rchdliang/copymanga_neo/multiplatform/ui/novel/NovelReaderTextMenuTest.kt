package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import androidx.compose.foundation.text.contextmenu.data.TextContextMenuData
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

class NovelReaderTextMenuTest {
    @Test
    fun menuAppearsAboveSelection() {
        assertEquals(
            IntOffset(100, 234), novelTextMenuPosition(
                Rect(180f, 300f, 280f, 340f), IntSize(500, 800), IntSize(260, 54), 12
            )
        )
    }

    @Test
    fun topSelectionUsesSpaceBelow() {
        assertEquals(
            IntOffset(20, 52), novelTextMenuPosition(
                Rect(60f, 12f, 240f, 40f), IntSize(412, 800), IntSize(260, 54), 12
            )
        )
    }

    @Test
    fun tabletRightPageKeepsMenuInsideScreen() {
        assertEquals(
            IntOffset(1008, 454), novelTextMenuPosition(
                Rect(1230f, 520f, 1278f, 560f), IntSize(1280, 800), IntSize(260, 54), 12
            )
        )
    }

    @Test
    fun offscreenSelectionKeepsMenuInsideViewport() {
        assertEquals(
            IntOffset(12, 12), novelTextMenuPosition(
                Rect(-100f, -20f, -20f, 1000f), IntSize(412, 800), IntSize(260, 54), 12
            )
        )
    }

    @Test
    fun tinyViewportDoesNotProduceNegativeCoordinates() {
        assertEquals(
            IntOffset.Zero, novelTextMenuPosition(
                Rect(10f, 2f, 30f, 20f), IntSize(50, 40), IntSize(50, 40), 12
            )
        )
    }

    @Test
    fun closingSessionCompletesAndRemovesMenu() = runTest {
        val state = NovelReaderTextMenuState()
        val show =
            async(start = CoroutineStart.UNDISPATCHED) { state.showTextContextMenu(provider) }
        assertTrue(state.visible)
        state.request!!.close()
        show.await()
        assertFalse(state.active)
    }

    @Test
    fun replacingSessionDoesNotLetOldFinallyClearNewMenu() = runTest {
        val state = NovelReaderTextMenuState()
        val first =
            async(start = CoroutineStart.UNDISPATCHED) { state.showTextContextMenu(provider) }
        val firstRequest = state.request
        val second =
            async(start = CoroutineStart.UNDISPATCHED) { state.showTextContextMenu(provider) }
        val secondRequest = state.request
        assertNotSame(firstRequest, secondRequest)
        first.await()
        assertSame(secondRequest, state.request)
        state.dismiss()
        second.await()
        assertFalse(state.visible)
    }

    @Test
    fun cancelledShowRemovesMenuWithoutLeavingSuspendedSession() = runTest {
        val state = NovelReaderTextMenuState()
        val show =
            async(start = CoroutineStart.UNDISPATCHED) { state.showTextContextMenu(provider) }
        show.cancel()
        yield()
        assertFalse(state.visible)
    }

    private val provider = object : TextContextMenuDataProvider {
        override fun position(destinationCoordinates: LayoutCoordinates) = Offset.Zero
        override fun contentBounds(destinationCoordinates: LayoutCoordinates) = Rect.Zero
        override fun data() = TextContextMenuData.Empty
    }
}
