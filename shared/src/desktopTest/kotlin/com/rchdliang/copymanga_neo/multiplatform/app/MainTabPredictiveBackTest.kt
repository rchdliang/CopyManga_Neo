package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class MainTabPredictiveBackTest {
    @Test fun previewsHomeWithoutSwitchingTabsUntilCommit() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val sceneJob = SupervisorJob(coroutineContext[Job])
        val owner = object : LifecycleOwner, ViewModelStoreOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
            override val viewModelStore = ViewModelStore()
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val back = UiBackDispatcher()
        val platform = object : UiPlatform {
            override val backDispatcher = back
            override suspend fun shareText(title: String, text: String) = Result.success(Unit)
            override fun formatDate(epochMilliseconds: Long) = ""
        }
        val nav = NavHostNavigation()
        var previewEnabled by mutableStateOf(true)
        var layoutDirection by mutableStateOf(LayoutDirection.Ltr)
        var nested by mutableStateOf(false)
        val mounted = mutableSetOf<MainTab>()
        val positions = mutableMapOf<MainTab, Float>()
        val activeStarts = mutableMapOf<MainTab, Int>()
        val savedValues = mutableMapOf<MainTab, MutableIntState>()
        val scene = ImageComposeScene(320, 480, Density(1f),
            coroutineContext = coroutineContext + sceneJob + StandardTestDispatcher(testScheduler)) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalViewModelStoreOwner provides owner,
                LocalUiPlatform provides platform, LocalPredictiveBackEnabled provides previewEnabled,
                LocalLayoutDirection provides layoutDirection) {
                val holder = rememberSaveableStateHolder()
                AppRouteNavHost(nav, back, onRetainRoutes = {}) { route ->
                    if (route.destination == "main") MainTabContent(nav) { tab ->
                        holder.SaveableStateProvider(tab.name) {
                            val value = rememberSaveable { mutableIntStateOf(0) }
                            SideEffect { savedValues[tab] = value }
                            DisposableEffect(tab) {
                                assertTrue(mounted.add(tab))
                                onDispose { mounted.remove(tab) }
                            }
                            ActiveRouteLaunchedEffect(tab) { activeStarts[tab] = (activeStarts[tab] ?: 0) + 1 }
                            BackHandler(enabled = tab == MainTab.PROFILE && nested) { nested = false }
                            Box(Modifier.fillMaxSize().onGloballyPositioned { positions[tab] = it.positionInRoot().x })
                        }
                    }
                }
            }
        }
        fun frames(n: Int = 30) {
            repeat(n) {
                advanceTimeBy(16)
                runCurrent()
                scene.render(testScheduler.currentTime * 1_000_000L).close()
                runCurrent()
            }
        }
        fun drag(fromLeft: Boolean = true) {
            back.startGesture(UiBackEvent(0f, fromLeft))
            back.progressGesture(UiBackEvent(.8f, fromLeft))
            frames(6)
        }
        try {
            frames()
            savedValues.getValue(MainTab.HOME).intValue = 42
            for (tab in listOf(MainTab.DOWNLOADS, MainTab.FAVORITES, MainTab.PROFILE)) {
                val previous = nav.tab
                nav.select(tab)
                frames(7)
                assertTrue(positions.getValue(previous) < 0f, "A regular forward tab selection still slides left")
                frames()
                val homeStarts = activeStarts[MainTab.HOME]
                for (fromLeft in listOf(true, false)) {
                    layoutDirection = if (tab == MainTab.DOWNLOADS) LayoutDirection.Ltr else LayoutDirection.Rtl
                    frames(2)
                    drag(fromLeft)
                    assertEquals(tab, nav.tab)
                    assertEquals(setOf(tab, MainTab.HOME), mounted)
                    val physicalSign = if (fromLeft) 1 else -1
                    assertTrue(positions.getValue(tab) * physicalSign > 0f)
                    assertEquals(homeStarts, activeStarts[MainTab.HOME])
                    back.cancelGesture()
                    frames(4)
                    assertTrue(positions.getValue(tab) * physicalSign >= 0f, "Cancel cannot jump across the other edge")
                    frames()
                    assertEquals(setOf(tab), mounted)
                    assertEquals(tab, nav.tab)
                }
            }

            nested = true
            frames(2)
            drag()
            assertEquals(setOf(MainTab.PROFILE), mounted)
            back.commitGesture()
            assertFalse(nested)
            assertEquals(MainTab.PROFILE, nav.tab)

            previewEnabled = false
            frames(2)
            drag()
            assertEquals(setOf(MainTab.PROFILE), mounted)
            back.cancelGesture()
            previewEnabled = true
            frames(2)
            drag(fromLeft = false)
            back.commitGesture()
            frames(12) // 192 ms is less than a fresh 300 ms tab transition.
            assertEquals(setOf(MainTab.HOME), mounted)
            assertEquals(MainTab.HOME, nav.tab)
            assertEquals(42, savedValues.getValue(MainTab.HOME).intValue)
            assertFalse(back.hasEnabledHandlers)
        } finally {
            scene.close()
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
            owner.viewModelStore.clear()
            sceneJob.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
