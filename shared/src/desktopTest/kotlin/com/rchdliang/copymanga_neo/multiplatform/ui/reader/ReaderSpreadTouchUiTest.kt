package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import coil3.ImageLoader
import coil3.PlatformContext
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ReaderSpreadTouchUiTest {
    @Test fun doubleTapPinchAndPanDoNotTurnPagesUntilReset() = runSkikoComposeUiTest(size = Size(900f, 600f)) {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        val pages = (0..6).map { ReaderPageEntry("c:$it", "c", "Fixture", "fixture-$it", it, 7) }
        lateinit var pager: PagerState
        lateinit var zoom: ReaderZoomState
        var edgeTurns = 0
        var menus = 0
        try {
            setContent {
                pager = rememberPagerState(initialPage = 1) { 4 }
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Box(Modifier.fillMaxSize().testTag("reader")) {
                        ReaderSpreadContent(pages, readerSpreads(pages, true), pager, true, ContentScale.Fit,
                            loader, ReaderToolsSettings(), { edgeTurns++ }, { edgeTurns-- }, { menus++ }, {}, {}, {},
                            { _, _, _ -> }, { zoom = it }, {})
                    }
                }
            }
            onNodeWithTag("reader").performTouchInput { doubleClick(Offset(600f, 240f)) }
            waitForIdle(); assertTrue(zoom.zoomed); assertEquals(1, pager.currentPage)
            onNodeWithTag("reader").performTouchInput { swipe(Offset(400f, 300f), Offset(600f, 320f), 500) }
            waitForIdle(); assertEquals(1, pager.currentPage); assertEquals(0, edgeTurns)
            onNodeWithTag("reader").performTouchInput { click(Offset(30f, 300f)) }
            mainClock.advanceTimeBy(400); waitForIdle()
            assertEquals(0, edgeTurns); assertTrue(menus > 0)
            runOnIdle { zoom.reset() }
            onNodeWithTag("reader").performTouchInput {
                pinch(start0 = Offset(350f, 300f), end0 = Offset(200f, 300f),
                    start1 = Offset(550f, 300f), end1 = Offset(700f, 300f), durationMillis = 500)
            }
            waitForIdle(); assertTrue(zoom.zoomed); assertEquals(1, pager.currentPage)
            assertEquals(0f, zoom.offset.x, 1f)
            runOnIdle { zoom.reset() }
            onNodeWithTag("reader").performTouchInput { swipeRight(durationMillis = 500) }
            waitForIdle(); assertEquals(2, pager.currentPage, "RTL right swipe advances to the next original spread")
        } finally { loader.shutdown() }
    }
}
