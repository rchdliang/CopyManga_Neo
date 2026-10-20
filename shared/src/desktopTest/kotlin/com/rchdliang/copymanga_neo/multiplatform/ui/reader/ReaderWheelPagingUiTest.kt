package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.launch

@OptIn(ExperimentalTestApi::class)
class ReaderWheelPagingUiTest {
    @Test fun wheelTurnsHorizontalComicPager() = runSkikoComposeUiTest(size = Size(400f, 400f)) {
        lateinit var pager: PagerState
        setContent {
            pager = rememberPagerState { 4 }
            val scope = rememberCoroutineScope()
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().testTag("pager")
                    .readerWheelPaging(
                        true,
                        { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
                    )
            ) { Box(Modifier.fillMaxSize()) }
        }
        onNodeWithTag("pager").performMouseInput { scroll(1f) }
        waitForIdle()
        assertEquals(1, pager.currentPage)
        onNodeWithTag("pager").performMouseInput {
            advanceEventTime(230)
            scroll(-1f)
        }
        waitForIdle()
        assertEquals(0, pager.currentPage)
    }

    @Test fun verticalWheelPagesOncePerBurstAndSupportsBothDirections() =
        runSkikoComposeUiTest(size = Size(400f, 400f)) {
            var forward = 0
            var backward = 0
            setContent {
                Box(
                    Modifier.fillMaxSize().testTag("reader")
                        .readerWheelPaging(true, { forward++ }, { backward++ })
                )
            }
            onNodeWithTag("reader").performMouseInput {
                moveTo(Offset(200f, 200f))
                scroll(1f)
                scroll(1f)
                scroll(-1f)
                advanceEventTime(230)
                scroll(-1f)
            }
            waitForIdle()
            assertEquals(1, forward)
            assertEquals(1, backward)
        }

    @Test fun disabledWheelDoesNotPage() = runSkikoComposeUiTest(size = Size(400f, 400f)) {
        var turns = 0
        setContent {
            Box(Modifier.fillMaxSize().testTag("reader")
                .readerWheelPaging(false, { turns++ }, { turns++ }))
        }
        onNodeWithTag("reader").performMouseInput { scroll(1f) }
        waitForIdle()
        assertEquals(0, turns)
    }
}
