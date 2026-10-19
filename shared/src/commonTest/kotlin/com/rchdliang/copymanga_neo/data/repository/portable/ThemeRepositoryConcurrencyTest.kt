package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ThemeRepositoryConcurrencyTest {
    private val response = """
        {"code":200,"results":{"is_banned":false,"is_lock":false,"is_login":true,"is_mobile_bind":false,"is_vip":false,
        "popular":0,"groups":{},"comic":{"uuid":"id","b_404":false,"b_hidden":false,"ban":0,"name":"Book",
        "path_word":"book","close_comment":false,"close_roast":false,
        "free_type":{"display":"","value":0},"restrict":{"display":"","value":0},"reclass":{"display":"","value":0},
        "region":{"display":"","value":0},"status":{"display":"","value":0},"theme":[{"name":"Allowed","path_word":"allowed"}]}}}
    """.trimIndent()

    @Test fun realRepositorySharesLimitAndDeduplicatesSameComic() = runTest {
        val state = PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(themeFetchConcurrency = 2, themeFetchIntervalMillis = 0)))
        var active = 0
        var peak = 0
        var requests = 0
        val http = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                requests++; active++; peak = maxOf(peak, active)
                try { delay(100); respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json")) }
                finally { active-- }
            }
        }))
        val client = CopyMangaClient(http, state)
        val cache = MemoryComicThemeCache()
        val repo = PortableMangaRepository(client, cache)
        try {
            listOf("a", "a", "b", "c", "d", "b").map { async { repo.refreshComicThemes(it).getOrThrow() } }.awaitAll()
            assertEquals(2, peak)
            assertEquals(4, requests)
            assertEquals(setOf("a", "b", "c", "d"), cache.entries.value.keys)
            repo.refreshComicThemes("a").getOrThrow()
            assertEquals(4, requests)
        } finally { client.close(); http.close() }
    }

    @Test fun failureCooldownKeepsStaleTagsAndRiskGateStopsFurtherTransport() = runTest {
        val state = PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(themeFetchIntervalMillis = 0)))
        var requests = 0
        var risk = false
        val http = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                requests++
                if (risk) respond("""{"code":210,"message":"blocked"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else respondError(HttpStatusCode.ServiceUnavailable)
            }
        }))
        val client = CopyMangaClient(http, state)
        val cache = MemoryComicThemeCache()
        val repo = PortableMangaRepository(client, cache)
        val old = listOf(Theme("Old", "old"))
        cache.save("cached", CachedComicThemes(old, 0))
        try {
            assertEquals(old, repo.getComicThemes("cached").getOrThrow())
            assertEquals(0, requests)
            assertTrue(repo.refreshComicThemes("cached").isFailure)
            assertTrue(repo.refreshComicThemes("cached").isFailure)
            assertEquals(1, requests)
            assertEquals(old, repo.getComicThemes("cached").getOrThrow())
            risk = true
            assertIs<RiskControlException>(repo.refreshComicThemes("risk").exceptionOrNull())
            assertNotNull(client.risk.notice.value)
            val before = requests
            assertTrue(repo.refreshComicThemes("another").isFailure)
            assertEquals(before, requests)
        } finally { client.close(); http.close() }
    }
}
