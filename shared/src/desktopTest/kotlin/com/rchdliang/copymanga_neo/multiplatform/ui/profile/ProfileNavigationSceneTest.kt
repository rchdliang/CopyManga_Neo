package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
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
class ProfileNavigationSceneTest {
    @Test fun cloudHistoryPreviewsTheLibraryAndOnlyCommitsOneStep() = runTest(timeout = 30.seconds) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val job = SupervisorJob(coroutineContext[Job])
        val owner = object : LifecycleOwner, ViewModelStoreOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
            override val viewModelStore = ViewModelStore()
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val platform = object : UiPlatform {
            override val backDispatcher = UiBackDispatcher()
            override val supportsPredictiveBack = true
            override suspend fun shareText(title: String, text: String) = error("No sharing in test")
            override fun formatDate(epochMilliseconds: Long) = ""
        }
        val dispatcher = platform.backDispatcher
        var outerReturns = 0
        val removeOuter = dispatcher.register { outerReturns++; true }
        var enabled by mutableStateOf(true)
        val mounted = mutableSetOf<ProfilePage>()
        var active: ProfilePage? = null
        lateinit var open: (ProfilePage) -> Unit
        lateinit var library: LazyListState
        val scene = ImageComposeScene(420, 700, Density(1f),
            coroutineContext = coroutineContext + job + StandardTestDispatcher(testScheduler)) {
            CompositionLocalProvider(LocalUiPlatform provides platform, LocalPredictiveBackEnabled provides enabled,
                LocalLifecycleOwner provides owner, LocalViewModelStoreOwner provides owner) {
                ProfileNavigationHost { page, openPage, _ ->
                    DisposableEffect(page) { mounted += page; onDispose { mounted -= page } }
                    if (LocalRouteActive.current) SideEffect { active = page; open = openPage }
                    if (page == ProfilePage.LOCAL_LIBRARY) {
                        val state = rememberLazyListState()
                        SideEffect { library = state }
                        LazyColumn(state = state) { items(100) { Text("记录 $it") } }
                    } else Text(page.name)
                }
            }
        }
        fun frames() {
            repeat(26) {
                advanceTimeBy(16); runCurrent()
                scene.render(testScheduler.currentTime * 1_000_000L).close()
                runCurrent()
            }
        }
        fun preview() {
            dispatcher.startGesture(UiBackEvent(0f, true))
            dispatcher.progressGesture(UiBackEvent(.65f, true))
            frames()
        }
        try {
            frames()
            open(ProfilePage.LOCAL_LIBRARY); frames()
            library.requestScrollToItem(35, 7); frames()
            val position = library.firstVisibleItemIndex to library.firstVisibleItemScrollOffset
            assertEquals(35 to 7, position)
            open(ProfilePage.HISTORY); frames()
            assertEquals(setOf(ProfilePage.HISTORY), mounted)
            preview()
            assertEquals(setOf(ProfilePage.LOCAL_LIBRARY, ProfilePage.HISTORY), mounted)
            assertEquals(ProfilePage.HISTORY, active)
            dispatcher.cancelGesture(); frames()
            assertEquals(setOf(ProfilePage.HISTORY), mounted)
            enabled = false; frames()
            preview()
            assertEquals(setOf(ProfilePage.HISTORY), mounted)
            assertTrue(dispatcher.commitGesture()); frames()
            assertEquals(ProfilePage.LOCAL_LIBRARY, active)
            assertEquals(position, library.firstVisibleItemIndex to library.firstVisibleItemScrollOffset)
            assertEquals(0, outerReturns)
            assertTrue(dispatcher.dispatchBack()); frames()
            assertEquals(ProfilePage.MAIN, active)
            assertTrue(dispatcher.dispatchBack())
            assertEquals(1, outerReturns)
        } finally {
            scene.close()
            removeOuter()
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
            owner.viewModelStore.clear()
            job.cancel(); runCurrent()
            Dispatchers.resetMain()
        }
    }
}
