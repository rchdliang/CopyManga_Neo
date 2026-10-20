package com.rchdliang.copymanga_neo.multiplatform.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.local.portable.*
import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.data.repository.portable.PortableComicDiscoveryRepository
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class OriginalWorkViewModelTest {
    private class Documents : DocumentStorage {
        private val values = mutableMapOf<String, String>()
        override suspend fun read(key: String) = values[key]
        override suspend fun write(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
    private class Network : NetworkSettingsRepository {
        override val settings = MutableStateFlow(NetworkSettings(requestMode = RequestMode.WEB))
        override val officialAppDefaults = OfficialAppSettings()
        override fun generatePseudoId() = ""
        override fun generateUmString() = ""
        override suspend fun diagnoseOfficialApp() = Result.success("")
        override suspend fun save(settings: NetworkSettings): Result<Unit> { this.settings.value = settings; return Result.success(Unit) }
        override suspend fun reset() = Result.success(Unit)
        override suspend fun rollback() = Result.success(Unit)
        override suspend fun discover() = Result.success(Unit)
        override suspend fun test(settings: NetworkSettings) = Result.success(emptyList<NetworkProbe>())
        override suspend fun refreshIfNeeded() = Unit
    }
    private fun comic(path: String) = ComicSummary(path, path, null, null, null, null, null, null, null)
    private inner class Fixture {
        val network = Network()
        val app = PortableAppSettings(Documents())
        val http = HttpClient(MockEngine { error("No transport expected") })
        val client = CopyMangaClient(http, PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB))))
        val calls = mutableListOf<Triple<String, String, Int>>()
        var reply: suspend (String, Int) -> Result<ComicPage> = { order, offset ->
            Result.success(ComicPage(listOf(comic("$order:$offset")), if (offset == 0) 21 else null, 42))
        }
        val repo = object : ComicDiscoveryRepository by PortableComicDiscoveryRepository(client) {
            override suspend fun getOriginalWorkComics(pathWord: String, ordering: String, offset: Int): Result<ComicPage> {
                calls += Triple(pathWord, ordering, offset)
                return reply(ordering, offset)
            }
        }
        val model = OriginalWorkViewModel(repo, network, app,
            SavedStateHandle(mapOf("originalPath" to "LoveLive", "originalName" to "Love Live!")))
        fun close() { model.viewModelScope.cancel(); client.close(); http.close() }
    }

    @Test fun pagingRetryAndSortCacheRemainIndependentOfOrdinarySearch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        try {
            f.model.start(); runCurrent()
            assertEquals("Love Live!", f.model.title)
            assertEquals("LoveLive", f.calls.single().first)
            f.reply = { _, _ -> Result.failure(Exception("offline")) }
            f.model.loadMore(); runCurrent()
            assertEquals(1, f.model.uiState.value.searchResults.size)
            assertEquals("offline", f.model.uiState.value.loadMoreError)
            f.reply = { _, offset -> Result.success(ComicPage(listOf(comic("more:$offset")), null, 42)) }
            f.model.loadMore(); runCurrent()
            assertEquals(listOf(0, 21, 21), f.calls.map { it.third })
            assertEquals(2, f.model.uiState.value.searchResults.size)
            f.model.selectOrdering("-popular"); runCurrent()
            val requests = f.calls.size
            f.model.selectOrdering("-datetime_updated"); runCurrent()
            assertEquals(requests, f.calls.size)
            assertEquals(2, f.model.uiState.value.searchResults.size)
            f.model.toggleLayout()
            assertTrue(f.model.uiState.value.compactLayout)
            assertFalse(f.app.getSearchCompactLayout(false).getOrThrow())
            f.model.start(); runCurrent()
            assertEquals(requests, f.calls.size)
        } finally { f.close(); Dispatchers.resetMain() }
    }

    @Test fun switchingRequestModeInvalidatesCachedPages() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        try {
            f.reply = { _, _ -> Result.success(ComicPage(listOf(comic(f.network.settings.value.requestMode.name)), null)) }
            f.model.start(); runCurrent()
            val previous = f.model.uiState.value.searchResults.single().pathWord
            f.network.settings.value = f.network.settings.value.copy(requestMode = RequestMode.OFFICIAL_APP)
            runCurrent()
            assertNotEquals(previous, f.model.uiState.value.searchResults.single().pathWord)
            assertEquals(RequestMode.OFFICIAL_APP.name, f.model.uiState.value.searchResults.single().pathWord)
            assertEquals(2, f.calls.size)
        } finally { f.close(); Dispatchers.resetMain() }
    }

    @Test fun lateCancelledResponseCannotOverwriteNewOrdering() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        val release = CompletableDeferred<Unit>()
        try {
            f.reply = { order, _ ->
                if (order == "-datetime_updated") withContext(NonCancellable) { release.await() }
                Result.success(ComicPage(listOf(comic(order)), null))
            }
            f.model.start(); runCurrent()
            f.model.selectOrdering("-popular"); runCurrent()
            release.complete(Unit); runCurrent()
            assertEquals("-popular", f.model.uiState.value.searchResults.single().pathWord)
        } finally { release.complete(Unit); f.close(); Dispatchers.resetMain() }
    }
}
