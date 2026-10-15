package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ComicSharedTransitionProvider
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackEvent
import com.rchdliang.copymanga_neo.multiplatform.ui.components.comicCoverSharedElement
import com.rchdliang.copymanga_neo.multiplatform.ui.components.rememberComicSharedTransitionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Real NavHost/sharedElement raster integration; no app, window, image request or native gesture. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class NavHostSharedCoverSceneTest {
    @Test fun sharedCoverSeeksCancelsAndCommitsAtRealEndpointBounds() = runTest(timeout = 30.seconds) {
        // Lifecycle discovers Main.immediate synchronously. Scene work remains queued on the
        // same scheduler, as in NavHostBackBridgeSceneTest, without deadlocking construction.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val sceneDispatcher = StandardTestDispatcher(testScheduler)
        val owner = object : LifecycleOwner, ViewModelStoreOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
            override val viewModelStore = ViewModelStore()
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val sceneJob = SupervisorJob(coroutineContext[Job])
        val back = UiBackDispatcher()
        var controller: NavHostController? = null
        var sharedTransitionActive = false
        var scene: ImageComposeScene? = null
        try {
            val raster = ImageComposeScene(
                width = WIDTH,
                height = HEIGHT,
                density = Density(1f),
                coroutineContext = coroutineContext + sceneJob + sceneDispatcher
            ) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner
                ) {
                    SharedCoverHost(back, { controller = it }, { sharedTransitionActive = it })
                }
            }
            scene = raster
            fun pump(frames: Int = 64) {
                // Advance frame timestamps and coroutine delays together. Never use
                // advanceUntilIdle on a live scene; each stage has a fixed frame budget.
                repeat(frames) {
                    advanceTimeBy(16)
                    runCurrent()
                    raster.render(testScheduler.currentTime * 1_000_000L).close()
                    runCurrent()
                }
            }
            fun pixels(): IntArray {
                val image = raster.render(testScheduler.currentTime * 1_000_000L)
                try {
                    val bitmap = image.toComposeImageBitmap()
                    try {
                        return IntArray(WIDTH * HEIGHT).also { bitmap.readPixels(it) }
                    } finally {
                        bitmap.asSkiaBitmap().close()
                    }
                } finally {
                    image.close()
                }
            }
            fun seek(progress: Float, fromLeft: Boolean): CoverBounds {
                back.progressGesture(UiBackEvent(progress, fromLeft))
                pump(12)
                assertTrue(sharedTransitionActive, "A held predictive seek must keep the matched cover active")
                return assertCover(pixels(), "seek $progress fromLeft=$fromLeft")
            }

            pump()
            val nav = assertNotNull(controller)
            val mainId = assertNotNull(nav.currentBackStackEntry).id
            val mainPixels = pixels()
            assertEquals(MAIN_COVER, assertCover(mainPixels, "main at rest"))
            assertFalse(sharedTransitionActive)
            assertFalse(back.hasEnabledHandlers)

            nav.navigate(DETAIL_ROUTE)
            pump()
            val detailId = assertNotNull(nav.currentBackStackEntry).id
            val detailPixels = pixels()
            assertEquals(DETAIL_COVER, assertCover(detailPixels, "detail at rest"))
            assertFalse(sharedTransitionActive)
            assertTrue(back.hasEnabledHandlers)

            // Exercise both native edge values and reuse the same entry after cancellation.
            for (fromLeft in listOf(true, false)) {
                back.startGesture(UiBackEvent(0f, fromLeft))
                pump(4) // Allow NavHost to compose and measure the real previous endpoint.
                val early = seek(0.12f, fromLeft)
                val later = seek(0.35f, fromLeft)
                assertIntermediate(early, "early")
                assertIntermediate(later, "later")
                assertTrue(later.left < early.left && later.top < early.top,
                    "Progress must move the rendered cover toward main: $early -> $later")
                assertTrue(later.width < early.width && later.height < early.height,
                    "Progress must resize the cover, not translate a frozen detail image: $early -> $later")
                assertEquals(detailId, nav.currentBackStackEntry?.id, "Seeking must not pop detail")
                assertEquals(mainId, nav.previousBackStackEntry?.id)

                val reversed = seek(0.12f, fromLeft)
                assertEquals(early, reversed, "Reversing a gesture must return to the same cover geometry")
                back.cancelGesture()
                pump()
                assertEquals(detailId, nav.currentBackStackEntry?.id, "Cancel must preserve entry identity")
                assertEquals(mainId, nav.previousBackStackEntry?.id)
                assertFalse(sharedTransitionActive, "Cancel must release the shared overlay")
                val cancelledPixels = pixels()
                assertEquals(DETAIL_COVER, assertCover(cancelledPixels, "cancelled detail"))
                assertContentEquals(detailPixels, cancelledPixels,
                    "Cancel must restore every pixel, including background and the absence of a ghost cover")
            }

            back.startGesture(UiBackEvent(0f, true))
            pump(4)
            assertIntermediate(seek(0.35f, true), "before commit")
            assertTrue(back.commitGesture())
            pump()
            assertEquals(MAIN_ROUTE, nav.currentDestination?.route)
            assertEquals(mainId, nav.currentBackStackEntry?.id)
            assertFalse(sharedTransitionActive, "Commit must finish the cover at the main endpoint")
            val committedPixels = pixels()
            assertEquals(MAIN_COVER, assertCover(committedPixels, "committed main"))
            assertContentEquals(mainPixels, committedPixels, "Commit must restore the complete main image")
            assertFalse(back.commitGesture(), "A duplicate native commit must not pop the root")
            assertFalse(back.hasEnabledHandlers)
        } finally {
            try {
                scene?.close()
                owner.lifecycle.currentState = Lifecycle.State.DESTROYED
                owner.viewModelStore.clear()
                sceneJob.cancel()
                runCurrent()
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    @Composable
    private fun SharedCoverHost(
        back: UiBackDispatcher,
        onController: (NavHostController) -> Unit,
        onTransitionActive: (Boolean) -> Unit
    ) {
        val nav = rememberNavController()
        val bridge = remember(nav, back) { NavHostBackBridge(back) { nav.currentBackStackEntry?.id } }
        DisposableEffect(bridge) { onDispose { bridge.dispose() } }
        SideEffect { onController(nav) }
        val covers = rememberComicSharedTransitionState()
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides bridge) {
            SharedTransitionLayout(Modifier.fillMaxSize().background(Color.Black)) {
                val sharedScope = this
                val active = isTransitionActive
                SideEffect { onTransitionActive(active) }
                NavHost(
                    navController = nav,
                    startDestination = MAIN_ROUTE,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(150)) },
                    exitTransition = { fadeOut(tween(100)) },
                    popEnterTransition = { fadeIn(tween(150)) },
                    popExitTransition = { fadeOut(tween(0)) },
                    predictivePopEnterTransition = { fadeIn(tween(150)) },
                    predictivePopExitTransition = { fadeOut(tween(0)) }
                ) {
                    composable(MAIN_ROUTE) {
                        ComicSharedTransitionProvider(sharedScope, this, covers) {
                            CoverEndpoint(MAIN_COVER, Color(0xFF142030))
                        }
                    }
                    composable(DETAIL_ROUTE) {
                        ComicSharedTransitionProvider(sharedScope, this, covers) {
                            CoverEndpoint(DETAIL_COVER, Color(0xFF303018))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CoverEndpoint(bounds: CoverBounds, background: Color) {
        Box(Modifier.fillMaxSize().background(background)) {
            // Use the production modifier and its actual default bounds animation. Only the
            // image content is synthetic, eliminating networking/decoding from geometry checks.
            Canvas(Modifier.offset(bounds.left.dp, bounds.top.dp)
                .comicCoverSharedElement("synthetic-cover")
                .size(bounds.width.dp, bounds.height.dp)) {
                drawRect(Color.Magenta)
                drawRect(Color.Cyan, topLeft = Offset(size.width / 2, 0f),
                    size = Size(size.width / 2, size.height))
            }
        }
    }

    private fun assertCover(pixels: IntArray, label: String): CoverBounds {
        var left = WIDTH
        var top = HEIGHT
        var right = -1
        var bottom = -1
        var opaqueCoverPixels = 0
        pixels.forEachIndexed { index, color ->
            if (color == MAGENTA || color == CYAN) {
                val x = index % WIDTH
                val y = index / WIDTH
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
                opaqueCoverPixels++
            }
        }
        assertTrue(opaqueCoverPixels > 0, "$label: the shared cover must remain visible above the exiting route")
        val bounds = CoverBounds(left, top, right - left + 1, bottom - top + 1)
        // Permit antialiased boundary pixels, but reject two endpoint ghosts or a hollow union.
        assertTrue(opaqueCoverPixels >= (bounds.width - 2) * (bounds.height - 2),
            "$label: expected one solid shared cover, found $opaqueCoverPixels pixels in $bounds")
        val centerY = top + bounds.height / 2
        assertEquals(MAGENTA, pixels[centerY * WIDTH + left + bounds.width / 4], "$label: left cover content")
        assertEquals(CYAN, pixels[centerY * WIDTH + left + bounds.width * 3 / 4], "$label: right cover content")
        return bounds
    }

    private fun assertIntermediate(bounds: CoverBounds, label: String) {
        // The production modifier uses a spring, so progress need not mean linear interpolation.
        // Nevertheless both measured position and size must lie strictly between the endpoints.
        assertTrue(bounds.left > MAIN_COVER.left && bounds.left < DETAIL_COVER.left, "$label: $bounds")
        assertTrue(bounds.top > MAIN_COVER.top && bounds.top < DETAIL_COVER.top, "$label: $bounds")
        assertTrue(bounds.width > MAIN_COVER.width && bounds.width < DETAIL_COVER.width, "$label: $bounds")
        assertTrue(bounds.height > MAIN_COVER.height && bounds.height < DETAIL_COVER.height, "$label: $bounds")
    }

    private data class CoverBounds(val left: Int, val top: Int, val width: Int, val height: Int)

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 480
        const val MAIN_ROUTE = "cover/main"
        const val DETAIL_ROUTE = "cover/detail"
        val MAIN_COVER = CoverBounds(24, 36, 72, 108)
        val DETAIL_COVER = CoverBounds(136, 204, 144, 216)
        val MAGENTA = Color.Magenta.toArgb()
        val CYAN = Color.Cyan.toArgb()
    }
}
