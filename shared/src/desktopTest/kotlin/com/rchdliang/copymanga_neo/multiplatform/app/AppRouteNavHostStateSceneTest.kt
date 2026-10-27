package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.components.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class AppRouteNavHostStateSceneTest {
    @Test fun restoresOneStackAndRetainsPreviewPayloadUntilCommittedDisposal() = runTest(timeout = 30.seconds) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val sceneDispatcher = StandardTestDispatcher(testScheduler)
        val sceneJob = SupervisorJob(coroutineContext[Job])
        val owner = object : LifecycleOwner, ViewModelStoreOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
            override val viewModelStore = ViewModelStore()
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val back = UiBackDispatcher()
        val platform = object : UiPlatform {
            override val backDispatcher = back
            override suspend fun shareText(title: String, text: String) = error("No sharing in route test")
            override fun formatDate(epochMilliseconds: Long) = ""
        }
        val seed = SavedNavigation(MainTab.FAVORITES, listOf(
            AppRoute("detail", id = "detail-visit", path = "book"),
            AppRoute("reader", id = "reader-visit", path = "book", chapter = "chapter", page = 42)
        ))
        var registry = SaveableStateRegistry(null) { true }
        var navigation: NavHostNavigation? = null
        val routeState = mutableMapOf<String, MutableState<Int>>()
        val starts = mutableMapOf<String, Int>()
        val released = mutableSetOf<String>()
        val mountedRoutes = mutableSetOf<String>()
        var retained = emptySet<String>()
        val diagnostics = mutableListOf<RouteHostDiagnostics>()
        var scene: ImageComposeScene? = null
        fun createScene(): ImageComposeScene = ImageComposeScene(
            320, 480, Density(1f), coroutineContext = coroutineContext + sceneJob + sceneDispatcher
        ) {
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalViewModelStoreOwner provides owner,
                LocalSaveableStateRegistry provides registry,
                LocalUiPlatform provides platform
            ) {
                val nav = rememberSaveable(saver = NavHostNavigation.saver) { NavHostNavigation(seed) }
                SideEffect { navigation = nav }
                AppRouteNavHost(nav, back, onRetainRoutes = { ids ->
                    retained = ids
                    assertTrue(((nav.payloadIds - ids) intersect mountedRoutes).isEmpty(),
                        "Cleanup cannot release a still-composed outgoing route")
                    released += nav.retain(ids)
                }, onDiagnostics = { snapshot ->
                    diagnostics += snapshot
                    System.err.println("Route host: $snapshot")
                }) { route ->
                    DisposableEffect(route.id) {
                        mountedRoutes += route.id
                        onDispose { mountedRoutes -= route.id }
                    }
                    val local = rememberSaveable(route.id) { mutableStateOf(0) }
                    SideEffect { routeState[route.id] = local }
                    ActiveRouteLaunchedEffect(route.id) { starts[route.id] = (starts[route.id] ?: 0) + 1 }
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        fun frames(count: Int = 30) {
            repeat(count) {
                advanceTimeBy(16)
                runCurrent()
                checkNotNull(scene).render(testScheduler.currentTime * 1_000_000L).close()
                runCurrent()
            }
        }
        try {
            scene = createScene()
            frames()
            var nav = assertNotNull(navigation)
            assertEquals(seed, nav.snapshot(), "Seed must restore once with original visit IDs")
            assertEquals(setOf("detail-visit", "reader-visit"), retained)
            assertEquals(1, starts["reader-visit"])
            assertNull(starts["detail-visit"], "Restoring through prior routes must not run their effects")
            assertNotNull(routeState["reader-visit"]).value = 42
            frames(2)
            back.startGesture(UiBackEvent(0f, true))
            back.progressGesture(UiBackEvent(.6f, true))
            frames()
            assertEquals(seed, nav.snapshot())
            assertNull(starts["detail-visit"], "Previous-route preview must remain inactive")
            assertTrue(released.isEmpty())
            back.cancelGesture()
            frames()
            assertEquals(42, routeState["reader-visit"]?.value)
            assertEquals(1, starts["reader-visit"], "Cancel must not replay current-route startup")

            // Exercise both rememberNavController and private payload saver together. Framework
            // restoration wins; the SavedNavigation fallback must not append duplicate visits.
            val saved = registry.performSave()
            scene.close()
            scene = null
            runCurrent()
            registry = SaveableStateRegistry(saved) { true }
            scene = createScene()
            frames()
            nav = assertNotNull(navigation)
            assertEquals(seed, nav.snapshot())
            assertEquals(2, nav.stack().size)
            assertEquals(42, routeState["reader-visit"]?.value)
            assertTrue(released.isEmpty())

            back.startGesture(UiBackEvent(0f, true))
            back.progressGesture(UiBackEvent(.8f, true))
            back.commitGesture()
            assertTrue("reader-visit" in mountedRoutes)
            assertTrue("reader-visit" in nav.payloadIds,
                "An ordinary committed pop retains its outgoing payload until disposal")
            frames()
            assertEquals(listOf("detail-visit"), nav.stack().map { it.id })
            assertEquals(setOf("reader-visit"), released)
            assertEquals(setOf("detail-visit"), retained)
            assertEquals("book", nav.payload("detail-visit").path)

            // Same book, distinct visit IDs; open+pop before any composition/frame. The final
            // stack can equal its old value, so registry changes must independently wake cleanup.
            nav.open(AppRoute("detail", id = "transient-visit", path = "book"))
            assertEquals(listOf("detail-visit", "transient-visit"), nav.stack().map { it.id })
            assertTrue(nav.back())
            frames()
            assertEquals(listOf("detail-visit"), nav.stack().map { it.id })
            assertEquals(setOf("reader-visit", "transient-visit"), released,
                "Rapid-visit cleanup diagnostics: ${diagnostics.takeLast(8)}")
            assertTrue(diagnostics.any { "transient-visit" in it.completingUncomposed },
                "The real orphaned framework transition must complete, not merely lose its payload")
            assertEquals(setOf("detail-visit"), nav.payloadIds)
            assertEquals("book", nav.payload("detail-visit").path)
            nav.select(MainTab.DOWNLOADS)
            frames()
            assertTrue(nav.stack().isEmpty())
            assertEquals(MainTab.DOWNLOADS, nav.tab)
            assertEquals(setOf("detail-visit", "reader-visit", "transient-visit"), released)
            assertTrue(back.dispatchBack())
            frames()
            assertEquals(MainTab.HOME, nav.tab)
            assertFalse(back.dispatchBack())
        } finally {
            scene?.close()
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
            owner.viewModelStore.clear()
            sceneJob.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
