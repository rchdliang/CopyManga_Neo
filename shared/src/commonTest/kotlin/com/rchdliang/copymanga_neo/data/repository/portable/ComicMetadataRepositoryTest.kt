package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ComicMetadataRepositoryTest {
    private class Storage : DocumentStorage {
        val documents = mutableMapOf<String, String>()
        override suspend fun read(key: String) = documents[key]
        override suspend fun write(key: String, value: String) { documents[key] = value }
        override suspend fun remove(key: String) { documents.remove(key) }
    }
    private val response = """
        {"code":200,"results":{"is_banned":false,"is_lock":false,"is_login":true,"is_mobile_bind":false,"is_vip":false,
        "popular":100,"groups":{},"comic":{"uuid":"id","b_404":false,"b_hidden":false,"ban":0,"name":"Book",
        "path_word":"book","close_comment":false,"close_roast":false,"brief":"public summary",
        "parodies":[{"name":"Love Live!","path_word":"love_live"}],
        "free_type":{"display":"","value":0},"restrict":{"display":"","value":0},"reclass":{"display":"","value":0},
        "region":{"display":"","value":0},"status":{"display":"","value":0},"theme":[]}}}
    """.trimIndent()

    private inner class Fixture(val test: TestScope, val storage: Storage = Storage()) {
        val state = PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(themeFetchConcurrency = 1, themeFetchIntervalMillis = 0)))
        var calls = 0
        var fail = false
        var risk = false
        var wait: CompletableDeferred<Unit>? = null
        val http = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(test.testScheduler)
            addHandler {
                calls++
                wait?.await()
                when {
                    risk -> respond("""{"code":210,"message":"blocked"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    fail -> respondError(HttpStatusCode.ServiceUnavailable)
                    else -> respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }))
        val client = CopyMangaClient(http, state)
        fun repository() = PortableComicMetadataRepository(client, PortableComicCardCache(storage),
            scope = test.backgroundScope, now = { 10_000 + test.testScheduler.currentTime })
        val repo = repository()
        fun close() { client.close(); http.close() }
    }

    @Test fun prefetchAndDetailShareResponseAndPersistOnlyPublicFields() = runTest {
        val f = Fixture(this)
        f.wait = CompletableDeferred()
        try {
            f.repo.preload(listOf("book"))
            runCurrent()
            assertEquals(1, f.calls)
            val detail = async { f.repo.detail("book") }
            runCurrent()
            f.wait!!.complete(Unit)
            assertTrue(detail.await().isSuccess)
            assertEquals(1, f.calls)
            assertEquals("public summary", f.repo.metadata.value["book"]?.brief)
            assertFalse(f.storage.documents.values.single().contains("is_login"))
            val restored = f.repository()
            restored.preload(listOf("book"))
            runCurrent()
            assertEquals("public summary", restored.metadata.value["book"]?.brief)
            assertEquals(1, f.calls)
        } finally { f.close() }
    }

    @Test fun failuresStopQueuedRequestsAndManualRetryRecovers() = runTest {
        val f = Fixture(this)
        f.fail = true
        try {
            f.repo.preload((0..19).map { "book$it" })
            runCurrent()
            assertEquals(3, f.calls)
            assertTrue(f.repo.failures.value.containsKey("book19"))
            f.fail = false
            f.repo.retry("book19")
            runCurrent()
            assertNotNull(f.repo.metadata.value["book19"])
        } finally { f.close() }
    }

    @Test fun changedSourceDoesNotReusePublicCacheOrReturnQueuedOldDetail() = runTest {
        val f = Fixture(this)
        f.wait = CompletableDeferred()
        try {
            val old = async { runCatching { f.repo.detail("book") } }
            runCurrent()
            val queued = async { runCatching { f.repo.detail("book") } }
            runCurrent()
            f.state.update { it.copy(settings = it.settings.copy(apiHost = "another.example")) }
            f.wait!!.complete(Unit)
            assertFalse(old.await().getOrNull()?.isSuccess == true)
            assertFalse(queued.await().getOrNull()?.isSuccess == true)
            f.wait = null
            assertTrue(f.repo.detail("book").isSuccess)
            assertEquals(2, f.calls)
        } finally { f.close() }
    }

    @Test fun workersSurviveNetworkGenerationCancellation() = runTest {
        val f = Fixture(this)
        f.wait = CompletableDeferred()
        try {
            f.repo.preload(listOf("old"))
            runCurrent()
            f.state.update { it.copy(settings = it.settings.copy(apiHost = "another.example")) }
            runCurrent()
            f.wait = null
            f.repo.preload(listOf("new"))
            runCurrent()
            assertNotNull(f.repo.metadata.value["new"])
            assertNull(f.repo.metadata.value["old"])
        } finally { f.close() }
    }

    @Test fun riskStopsTransportAndExplicitResumeCanRetry() = runTest {
        val f = Fixture(this)
        f.risk = true
        try {
            f.repo.preload(listOf("a", "b", "c"))
            runCurrent()
            assertNotNull(f.client.risk.notice.value)
            assertEquals(1, f.calls)
            f.risk = false
            f.client.risk.resumeByUser()
            f.repo.retry("a")
            runCurrent()
            assertNotNull(f.repo.metadata.value["a"])
        } finally { f.close() }
    }
}
