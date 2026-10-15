package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavHostController
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Offscreen framework integration only: no application host, repositories or native gestures. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class NavHostBackBridgeSceneTest {
    @Test fun frameworkSeekingKeepsCancelAndNestedBackOwnership() = runTest(timeout = 30.seconds) {
        // Lifecycle's desktop main-thread checker synchronously runBlocking(Main.immediate).
        // A queued StandardTestDispatcher here deadlocks before scene construction returns.
        // Keep scene work queued separately; only main-thread discovery needs eager dispatch.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val sceneDispatcher = StandardTestDispatcher(testScheduler)
        val owner = object : LifecycleOwner, ViewModelStoreOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
            override val viewModelStore = ViewModelStore()
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val sceneJob = SupervisorJob(coroutineContext[Job])
        fun stage(message: String) {
            System.err.println("NavHost probe: $message")
            System.err.flush()
        }
        val back = UiBackDispatcher()
        var controller: NavHostController? = null
        var scene: ImageComposeScene? = null
        try {
            stage("construct scene")
            val raster = ImageComposeScene(320, 480, Density(1f), coroutineContext = coroutineContext + sceneJob + sceneDispatcher) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner
                ) {
                    SharedNavHostProbe(back) { controller = it }
                }
            }
            scene = raster
            fun settle(label: String) {
                stage("$label: begin")
                // Keep coroutine delays and animation frame timestamps on the SAME virtual clock.
                // Fixed frame count bounds the pump; never advanceUntilIdle on an active scene.
                repeat(30) { frame ->
                    stage("$label frame $frame: advance scheduler")
                    advanceTimeBy(16)
                    runCurrent()
                    stage("$label frame $frame: render")
                    raster.render(testScheduler.currentTime * 1_000_000L).close()
                    stage("$label frame $frame: drain frame work")
                    runCurrent()
                }
                stage("$label: done")
            }
            settle("initial root")
            val nav = assertNotNull(controller)
            assertEquals("probe/main", nav.currentDestination?.route)
            assertFalse(back.hasEnabledHandlers, "A root NavHost must not swallow application exit")
            nav.navigate("probe/detail")
            settle("navigate detail")
            val detailId = assertNotNull(nav.currentBackStackEntry).id
            assertTrue(back.hasEnabledHandlers)

            back.startGesture(UiBackEvent(0f, true))
            back.progressGesture(UiBackEvent(0.6f, true))
            settle("predictive progress")
            assertEquals(detailId, nav.currentBackStackEntry?.id, "Seeking must not pop the stack")
            back.cancelGesture()
            settle("predictive cancel")
            assertEquals(detailId, nav.currentBackStackEntry?.id)

            var nestedBacks = 0
            val removeNested = back.register { nestedBacks++; true }
            back.startGesture(UiBackEvent(0f, false))
            back.progressGesture(UiBackEvent(0.8f, false))
            back.commitGesture()
            settle("nested modal commit")
            assertEquals(1, nestedBacks)
            assertEquals(detailId, nav.currentBackStackEntry?.id, "Modal back must not reach NavHost")
            removeNested()

            back.startGesture(UiBackEvent(0f, true))
            back.progressGesture(UiBackEvent(0.8f, true))
            back.commitGesture()
            settle("predictive commit")
            assertEquals("probe/main", nav.currentDestination?.route)
            assertFalse(back.commitGesture(), "A duplicate native commit must not pop another entry")
            assertFalse(back.hasEnabledHandlers)

            nav.navigate("probe/detail")
            settle("ordinary detail")
            assertTrue(back.dispatchBack(), "Non-predictive desktop/browser back uses the same framework input")
            settle("ordinary back")
            assertEquals("probe/main", nav.currentDestination?.route)

            nav.navigate("probe/detail")
            settle("replacement detail")
            back.startGesture(UiBackEvent(0f, true))
            back.progressGesture(UiBackEvent(0.4f, true))
            nav.navigate("probe/reader")
            settle("replace during gesture")
            val replacementId = nav.currentBackStackEntry?.id
            back.commitGesture()
            settle("replacement commit")
            assertEquals(replacementId, nav.currentBackStackEntry?.id,
                "A route introduced during a pinned gesture must not be popped by its commit")

            back.startGesture(UiBackEvent(0f, true))
            stage("dispose during gesture")
            raster.close()
            scene = null
            runCurrent()
            assertTrue(back.commitGesture(), "Disposed gesture stays consumed without falling through")
            assertFalse(back.hasEnabledHandlers)
        } finally {
            stage("cleanup: close scene")
            try {
                scene?.close()
                owner.lifecycle.currentState = Lifecycle.State.DESTROYED
                owner.viewModelStore.clear()
                sceneJob.cancel()
                stage("cleanup: drain cancellation")
                runCurrent()
            } finally {
                Dispatchers.resetMain()
                stage("cleanup: complete")
            }
        }
    }
}
