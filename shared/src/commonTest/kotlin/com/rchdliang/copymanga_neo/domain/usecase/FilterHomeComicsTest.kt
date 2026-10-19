package com.rchdliang.copymanga_neo.domain.usecase

import com.rchdliang.copymanga_neo.data.remote.portable.CopyMangaClient
import com.rchdliang.copymanga_neo.data.remote.portable.PortableNetworkState
import com.rchdliang.copymanga_neo.data.repository.portable.PortableMangaRepository
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class FilterHomeComicsTest {
    private val blocked = listOf(Theme("Blocked", "blocked"))
    private val allowed = listOf(Theme("Allowed", "allowed"))
    private fun comic(id: String) = ComicSummary(id, id, null, null, null, null, null, null, null)
    private class Fixture {
        val http = HttpClient(MockEngine { error("unexpected transport request") })
        val client = CopyMangaClient(http, PortableNetworkState())
        val cache = MutableStateFlow<Map<String, CachedComicThemes>>(emptyMap())
        val calls = mutableListOf<String>()
        var lookup: suspend (String) -> Result<List<Theme>> = { Result.success(emptyList()) }
        val repository = object : MangaRepository by PortableMangaRepository(client) {
            override val cachedComicThemes = cache
            override suspend fun refreshComicThemes(pathWord: String): Result<List<Theme>> {
                calls += pathWord
                return lookup(pathWord).onSuccess { themes ->
                    cache.update { it + (pathWord to CachedComicThemes(themes, Long.MAX_VALUE)) }
                }
            }
        }
        val filter = FilterHomeComics(repository)
        fun close() { client.close(); http.close() }
    }

    @Test fun fastSectionBecomesReadyWhileOtherSectionIsStillFetching() = runTest {
        val fixture = Fixture()
        fixture.lookup = { path -> delay(if (path == "fast") 100 else 6000); Result.success(allowed) }
        var latest: FilteredHomeComics? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.filter.observe(HomeComicsData(recComics = listOf(comic("fast")), rankComics = listOf(comic("slow"))), blocked)
                .collect { latest = it }
        }
        try {
            advanceTimeBy(101); runCurrent()
            val partial = assertNotNull(latest)
            assertEquals(listOf("fast"), partial.comics.recComics.map { it.pathWord })
            assertTrue(HomeFilterSection.RECOMMENDED in partial.readySections)
            assertFalse(HomeFilterSection.RANKS in partial.readySections)
            assertFalse(partial.enrichmentComplete)
            advanceTimeBy(3000); runCurrent()
            assertTrue(latest!!.showUnresolvedWarning)
            assertEquals(listOf("slow"), latest!!.comics.rankComics.map { it.pathWord })
            assertTrue(HomeFilterSection.RANKS in latest!!.readySections)
        } finally { job.cancelAndJoin(); fixture.close() }
    }

    @Test fun duplicatesAcrossSectionsAreFetchedOnceAndEmptyTagsRemainVisible() = runTest {
        val fixture = Fixture()
        fixture.lookup = { delay(100); Result.success(emptyList()) }
        try {
            val duplicate = comic("same")
            val result = fixture.filter.filter(HomeComicsData(recComics = listOf(duplicate), rankComics = listOf(duplicate)), blocked)
            assertEquals(listOf("same"), fixture.calls)
            assertEquals(1, result.comics.recComics.size)
            assertEquals(1, result.comics.rankComics.size)
            assertEquals(0, result.unresolvedCount)
        } finally { fixture.close() }
    }

    @Test fun staleCacheFiltersImmediatelyWhileRefreshIsOutstanding() = runTest {
        val fixture = Fixture()
        fixture.cache.value = mapOf("allow" to CachedComicThemes(allowed, 0), "block" to CachedComicThemes(blocked, 0))
        fixture.lookup = { awaitCancellation() }
        var latest: FilteredHomeComics? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.filter.observe(HomeComicsData(recComics = listOf(comic("allow"), comic("block"))), blocked).collect { latest = it }
        }
        try {
            runCurrent()
            assertEquals(listOf("allow"), latest!!.comics.recComics.map { it.pathWord })
            assertTrue(HomeFilterSection.RECOMMENDED in latest!!.readySections)
            assertFalse(latest!!.enrichmentComplete)
        } finally { job.cancelAndJoin(); fixture.close() }
    }

    @Test fun consecutiveFailuresCancelOutstandingWorkInsteadOfDrainingWholePage() = runTest {
        val fixture = Fixture()
        var active = 0
        fixture.lookup = {
            active++
            try { delay(100); Result.failure(IllegalStateException("offline")) } finally { active-- }
        }
        var latest: FilteredHomeComics? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.filter.observe(HomeComicsData(recComics = (0..49).map { comic(it.toString()) }), blocked).collect { latest = it }
        }
        try {
            advanceTimeBy(1000); runCurrent()
            assertEquals(0, active)
            assertTrue(latest!!.enrichmentComplete)
            assertTrue(fixture.calls.size <= ThemeLoadingPolicy.MAX_CONCURRENCY + 2)
            val count = fixture.calls.size
            advanceTimeBy(4000); runCurrent()
            assertEquals(count, fixture.calls.size)
            assertEquals(50, latest!!.comics.recComics.size)
        } finally { job.cancelAndJoin(); fixture.close() }
    }

    @Test fun riskResponseCancelsOtherWorkersAndPropagates() = runTest {
        val fixture = Fixture()
        var active = 0
        fixture.lookup = { path ->
            active++
            try {
                if (path == "0") { delay(100); Result.failure(RiskControlException("blocked")) }
                else awaitCancellation()
            } finally { active-- }
        }
        var failure: Throwable? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.filter.observe(HomeComicsData(recComics = (0..20).map { comic(it.toString()) }), blocked)
                .catch { failure = it }.collect()
        }
        try {
            advanceTimeBy(101); runCurrent()
            assertIs<RiskControlException>(failure)
            assertEquals(0, active)
            val count = fixture.calls.size
            advanceTimeBy(5000); runCurrent()
            assertEquals(count, fixture.calls.size)
        } finally { job.cancelAndJoin(); fixture.close() }
    }

    @Test fun disabledBlacklistDoesNotRequestAnyTags() = runTest {
        val fixture = Fixture()
        try {
            val data = HomeComicsData(recComics = listOf(comic("a")))
            assertEquals(data, fixture.filter.observe(data, emptyList()).first().comics)
            assertTrue(fixture.calls.isEmpty())
        } finally { fixture.close() }
    }
}
