package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
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
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalUiPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackEvent
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class NestedNavigationBackTest {
    @Test fun inactiveNestedGraphCannotTakeOrRetargetBack() = runSkikoComposeUiTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val owner = object : LifecycleOwner, ViewModelStoreOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
            override val viewModelStore = ViewModelStore()
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val back = UiBackDispatcher()
        val platform = object : UiPlatform {
            override val backDispatcher = back
            override suspend fun shareText(title: String, text: String) = error("unused")
            override fun formatDate(epochMilliseconds: Long) = ""
        }
        var parentBacks = 0
        val removeParent = back.register { parentBacks++; true }
        var active by mutableStateOf(true)
        lateinit var controller: NavHostController
        try {
            setContent {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner, LocalViewModelStoreOwner provides owner,
                    LocalUiPlatform provides platform, LocalRouteActive provides active
                ) {
                    controller = rememberNavController()
                    PredictiveNavigationHost(controller) {
                        NavHost(controller, startDestination = "list") {
                            composable("list") { Text("List") }
                            composable("detail") { Text("Detail") }
                        }
                    }
                }
            }
            runOnIdle { controller.navigate("detail") }
            runOnIdle {
                back.startGesture(UiBackEvent(0f, true))
                back.progressGesture(UiBackEvent(.6f, true))
            }
            runOnIdle { active = false }
            runOnIdle { assertTrue(back.commitGesture()) }
            runOnIdle {
                assertEquals("detail", controller.currentDestination?.route)
                assertEquals(0, parentBacks, "Losing ownership must consume the pinned gesture")
                assertTrue(back.dispatchBack())
                assertEquals(1, parentBacks)
                active = true
            }
            runOnIdle { assertTrue(back.dispatchBack()) }
            runOnIdle {
                assertEquals("list", controller.currentDestination?.route)
                assertEquals(1, parentBacks)
            }
        } finally {
            removeParent()
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
            owner.viewModelStore.clear()
            Dispatchers.resetMain()
        }
    }
}
