package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.multiplatform.app.RouteLifecycleOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Exercises the production screen's restoration/reporting; preview images avoid network/services. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class ReaderSpreadPositionUiTest {
    @Test fun exactSecondLeafSurvivesModeDirectionWindowAndLocalRePairing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
        val pending = mutableStateOf<Int?>(4)
        var tools by mutableStateOf(ReaderToolsSettings(pageLayout = "DOUBLE", inlineComments = false))
        var direction by mutableStateOf("RIGHT_TO_LEFT")
        var width by mutableStateOf(1100.dp)
        val reported = mutableListOf<Int>()
        var scene: ImageComposeScene? = null
        try {
            val raster = ImageComposeScene(1200, 800, coroutineContext = coroutineContext) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalInspectionMode provides true) {
                    CopyManga_NeoTheme {
                        Box(Modifier.size(width, 700.dp)) {
                            ReaderSuccessContent(images = (0..8).map { "fixture-$it" }, chapterName = "Fixture",
                                readingDirection = direction, pageScale = "FIT_SCREEN", screenOrientation = "SYSTEM_DEFAULT",
                                onPrevChapter = {}, onNextChapter = {}, hasPrevChapter = false, hasNextChapter = false,
                                onRefresh = {}, onReadingDirectionChange = {}, onPageScaleChange = {}, onScreenOrientationChange = {},
                                pendingInitialPage = pending, tools = tools, onPosition = { _, page, _, _ -> reported += page })
                        }
                    }
                }
            }
            scene = raster
            var time = 0L
            fun settle() { repeat(8) { runCurrent(); time += 16_000_000; raster.render(time).close(); runCurrent() } }
            settle(); assertEquals(4, reported.last())
            tools = tools.copy(pageLayout = "SINGLE"); settle(); assertEquals(4, reported.last())
            tools = tools.copy(pageLayout = "DOUBLE"); settle(); assertEquals(4, reported.last())
            direction = "LEFT_TO_RIGHT"; settle(); assertEquals(4, reported.last())
            width = 480.dp; settle(); assertEquals(4, reported.last())
            width = 1100.dp; settle(); assertEquals(4, reported.last())
            tools = tools.copy(pairingRestarts = mapOf("preview" to listOf(2, 5)))
            settle(); assertEquals(4, reported.last())
            tools = tools.copy(pairingRestarts = emptyMap()); settle(); assertEquals(4, reported.last())
            pending.value = 6; settle(); assertEquals(6, reported.last())
            tools = tools.copy(pageLayout = "AUTO"); settle(); assertEquals(6, reported.last())
            assertTrue(reported.all { it == 4 || it == 6 }, "Reflow must not write a transient group index to history: $reported")
        } finally { scene?.close(); owner.destroy(); Dispatchers.resetMain() }
    }
}
