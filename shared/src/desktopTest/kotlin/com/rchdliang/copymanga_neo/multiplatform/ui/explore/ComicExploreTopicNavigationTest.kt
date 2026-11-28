package com.rchdliang.copymanga_neo.multiplatform.ui.explore

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.ComicDiscoveryRepository
import com.rchdliang.copymanga_neo.multiplatform.app.*
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ComicExploreTopicNavigationTest {
    private class Discovery : ComicDiscoveryRepository {
        val listOffsets = mutableListOf<Int>()
        val comicOffsets = mutableListOf<Int>()
        var listPage: suspend (Int) -> ComicTopicPage = { offset ->
            ComicTopicPage(List(20) { i -> topic(offset + i) }, if (offset == 0) 20 else null)
        }
        fun topic(i: Int) = ComicTopic("topic-$i", "专题$i", null, "简介$i")
        override suspend fun getTopics(offset: Int): Result<ComicTopicPage> {
            listOffsets += offset
            return Result.success(listPage(offset))
        }
        override suspend fun getTopic(pathWord: String) = Result.success(topic(pathWord.substringAfter('-').toInt()))
        override suspend fun getTopicComics(pathWord: String, offset: Int): Result<ComicPage> {
            comicOffsets += offset
            val comics = List(20) { i ->
                ComicSummary("漫画${offset + i}", "book-${offset + i}", null, null, null, null, null, null, null)
            }
            return Result.success(ComicPage(comics, if (offset == 0) 20 else null))
        }
        override suspend fun getOriginalWorkComics(pathWord: String, ordering: String, offset: Int) = error("Unexpected original work")
        override suspend fun getRankings(period: String, audience: String, offset: Int, limit: Int) = error("Unexpected rankings")
        override suspend fun getNewest(offset: Int, limit: Int) = error("Unexpected newest")
        override suspend fun getRecommendations(offset: Int, limit: Int) = error("Unexpected recommendations")
        override suspend fun getFinished(offset: Int, limit: Int) = error("Unexpected finished")
        override suspend fun getFilterOptions() = error("Unexpected filters")
        override suspend fun filterComics(theme: String?, top: String?, ordering: String, offset: Int) = error("Unexpected search")
    }

    @Test fun twoPagedGridsReturnToTheirOwnPositionAndDirectTopicReturnsHome() = runSkikoComposeUiTest(size = Size(560f, 620f)) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val discovery = Discovery()
        val navigation = NavHostNavigation()
        val back = UiBackDispatcher()
        val models = mutableMapOf<String, ComicExploreViewModel>()
        val owner = object : LifecycleOwner, ViewModelStoreOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
            override val viewModelStore = ViewModelStore()
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        try {
            setContent {
                CompositionLocalProvider(LocalInspectionMode provides true, LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner) {
                    val holder = rememberSaveableStateHolder()
                    MaterialTheme {
                        AppRouteNavHost(navigation, back, onRetainRoutes = { retained ->
                            navigation.retain(retained).forEach(holder::removeState)
                        }) { route ->
                            holder.SaveableStateProvider(route.id) {
                                when (route.destination) {
                                    "main" -> Text("首页")
                                    "detail" -> Text("漫画详情")
                                    else -> ComicExploreScreen(navigation::comic, { navigation.back() }, navigation::openTopic,
                                        models.getOrPut(route.id) {
                                            ComicExploreViewModel(discovery, SavedStateHandle(route.arguments), autoStart = false)
                                        })
                                }
                            }
                        }
                    }
                }
            }
            onNodeWithText("首页").assertIsDisplayed()
            runOnIdle { navigation.explore("topics") }
            onNode(hasScrollToIndexAction()).performScrollToIndex(19)
            waitUntil { discovery.listOffsets.contains(20) }
            onNode(hasScrollToIndexAction()).performScrollToIndex(31)
            val topic = onNodeWithText("专题31")
            val topicPosition = topic.getUnclippedBoundsInRoot().top
            topic.performClick()
            onNodeWithText("简介31").assertIsDisplayed()
            // Two full-width header items precede the comics.
            onNode(hasScrollToIndexAction()).performScrollToIndex(21)
            waitUntil { discovery.comicOffsets.contains(20) }
            onNode(hasScrollToIndexAction()).performScrollToIndex(33)
            val comic = onNodeWithText("漫画31")
            val comicPosition = comic.getUnclippedBoundsInRoot().top
            comic.performClick()
            onNodeWithText("漫画详情").assertIsDisplayed()
            runOnIdle { assertTrue(back.dispatchBack()) }
            assertEquals(comicPosition, comic.getUnclippedBoundsInRoot().top)
            onNodeWithContentDescription("返回").performClick()
            assertEquals(topicPosition, topic.getUnclippedBoundsInRoot().top)
            assertEquals(listOf(0, 20), discovery.listOffsets)
            assertEquals(listOf(0, 20), discovery.comicOffsets)

            runOnIdle { navigation.back() }
            onNodeWithText("首页").assertIsDisplayed()
            runOnIdle { navigation.openTopic(discovery.topic(3)) }
            onNodeWithText("简介3").assertIsDisplayed()
            runOnIdle { assertTrue(back.dispatchBack()) }
            onNodeWithText("首页").assertIsDisplayed()
            assertEquals(listOf(0, 20), discovery.listOffsets)
        } finally {
            models.values.forEach { it.viewModelScope.cancel() }
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
            owner.viewModelStore.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun refreshIgnoresAnOlderTopicPageEvenWhenTheRequestCompletesAfterCancellation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val discovery = Discovery()
        val model = ComicExploreViewModel(discovery, SavedStateHandle(mapOf("section" to "topics")), autoStart = false)
        val pending = CompletableDeferred<Unit>()
        try {
            model.start()
            runCurrent()
            discovery.listPage = { offset ->
                if (offset == 20) withContext(NonCancellable) { pending.await() }
                ComicTopicPage(listOf(discovery.topic(offset + 50)), null)
            }
            model.loadMore()
            runCurrent()
            model.pullToRefresh()
            runCurrent()
            pending.complete(Unit)
            runCurrent()
            assertEquals(listOf(discovery.topic(50)), model.uiState.value.topics)
            assertNull(model.uiState.value.nextOffset)
        } finally {
            pending.complete(Unit)
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
