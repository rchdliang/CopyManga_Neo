package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalPredictiveBackEnabled
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalUiPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackEvent
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiPlatform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReaderBackMotionSceneTest {
    @Test fun shortLeftEdgeAndLongRightEdgeGesturesFadeFromTheirHeldPose() =
        runSkikoComposeUiTest(size = Size(320f, 480f)) {
            val back = UiBackDispatcher()
            val platform = object : UiPlatform {
                override val backDispatcher = back
                override suspend fun shareText(title: String, text: String) = error("unused")
                override fun formatDate(epochMilliseconds: Long) = ""
            }
            val owner = RouteLifecycleOwner().apply { update(Lifecycle.State.RESUMED, true) }
            val models = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
            val nav = NavHostNavigation(SavedNavigation(MainTab.HOME, emptyList()))
            setContent {
                CompositionLocalProvider(LocalUiPlatform provides platform, LocalPredictiveBackEnabled provides true,
                    LocalLifecycleOwner provides owner, LocalViewModelStoreOwner provides models) {
                    AppRouteNavHost(nav, back, onRetainRoutes = { nav.retain(it) }) { route ->
                        Box(Modifier.fillMaxSize().background(if (route.destination == "reader") Color.Red else Color.Blue))
                    }
                }
            }
            fun row(): List<Color> {
                val pixels = onRoot().captureToImage().toPixelMap()
                return List(pixels.width) { x -> pixels[x, pixels.height / 2] }
            }
            fun span(row: List<Color>) = row.indexOfFirst { it.red > .06f }..row.indexOfLast { it.red > .06f }
            try {
                waitForIdle()
                for ((progress, left) in listOf(.25f to true, .98f to false)) {
                    mainClock.autoAdvance = true
                    runOnIdle { nav.open(AppRoute("reader", id = "reader-$progress")) }
                    waitForIdle()
                    mainClock.autoAdvance = false
                    fun drag() {
                        runOnUiThread {
                            back.startGesture(UiBackEvent(0f, left))
                            back.progressGesture(UiBackEvent(progress, left))
                        }
                        mainClock.advanceTimeBy(64)
                    }
                    drag()
                    val held = row()
                    val bounds = span(held)
                    assertTrue(held.first().blue > .98f && held.last().blue > .98f, "previous page visible at both edges")
                    assertTrue(held[held.size / 2].red > .98f, "reader stays opaque during the drag")
                    assertTrue(abs(bounds.count() - held.size * (1f - .1f * progress)) <= 2)
                    val shift = (bounds.first + bounds.last - held.lastIndex) / 2f
                    assertTrue(if (left) shift > 0 else shift < 0, "reader follows the swipe direction")
                    runOnUiThread { back.cancelGesture() }
                    mainClock.advanceTimeBy(160)
                    assertTrue(row().all { it.red > .98f })
                    assertEquals("reader", nav.stack().last().destination)

                    drag()
                    runOnUiThread {
                        assertTrue(back.commitGesture())
                        assertEquals("reader", nav.stack().last().destination, "finish the visible fade before popping")
                        assertTrue(back.dispatchBack(), "another back during the fade is consumed")
                        back.startGesture(UiBackEvent(0f, left))
                        back.progressGesture(UiBackEvent(.5f, left))
                    }
                    val fades = (1..8).map {
                        mainClock.advanceTimeByFrame()
                        row()
                    }
                    assertTrue(fades.first()[held.size / 2].red < .94f, "the first commit frame must already be translucent")
                    runOnUiThread { back.commitGesture() }
                    val mixed = fades.filter { it[it.size / 2].red in .10f.. .90f }
                    assertTrue(mixed.isNotEmpty(), "release should show a fade after both short and nearly complete drags")
                    for (frame in mixed) {
                        val visible = span(frame)
                        assertTrue(abs(visible.first - bounds.first) <= 2 && abs(visible.last - bounds.last) <= 2,
                            "fading reader should retain its release geometry")
                    }
                    mainClock.advanceTimeBy(100)
                    assertTrue(row().all { it.blue > .98f })
                    assertTrue(nav.stack().isEmpty(), "gesture pops exactly the reader")
                }
                mainClock.autoAdvance = true
                runOnIdle { nav.open(AppRoute("reader", id = "replaced-reader")) }
                waitForIdle()
                mainClock.autoAdvance = false
                runOnUiThread {
                    back.startGesture(UiBackEvent(0f, true))
                    back.progressGesture(UiBackEvent(.6f, true))
                }
                mainClock.advanceTimeBy(48)
                runOnUiThread {
                    back.commitGesture()
                    nav.open(AppRoute("detail", id = "replacement"))
                }
                mainClock.advanceTimeBy(400)
                assertEquals(listOf("replaced-reader", "replacement"), nav.stack().map { it.id },
                    "finishing a stale gesture must not pop the replacement")
            } finally {
                runOnUiThread { owner.destroy(); models.viewModelStore.clear() }
            }
        }
}
