package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SharedChapterCatalogRepositoryTest {
    private class Fixture {
        val state = PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB)))
        var pageCalls = 0
        var htmlCalls = 0
        var failApi = false
        var failWeb = false
        private val identity = object : PortableOfficialIdentity {
            override val tapString = "catalog-test-only"
            override val loginDate = "2026-10-28"
            override fun dateHeader() = "2026.10.28"
            override fun resolve(settings: OfficialAppSettings) = settings.copy(
                pseudoId = "test-id", umString = "test-um", device = "test-device",
                deviceInfo = "test-model", buildTime = "test-build")
        }
        private val http = HttpClient(MockEngine { request ->
            val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
            when (request.url.encodedPath) {
                "/comic/book" -> {
                    htmlCalls++
                    if (failWeb) respondError(HttpStatusCode.ServiceUnavailable)
                    else respond("""<script>
                        var ccz = '0123456789abcdef';
                        </script><input id="dnt" value="1">""", headers = headersOf(HttpHeaders.ContentType, "text/html"))
                }
                "/comicdetail/book/chapters" -> respond("""{"code":200,"results":{"build":{"path_word":"book"},"groups":{
                    "default":{"path_word":"default","name":"默认","count":1,"chapters":[{"id":"a","name":"第一章","type":1}]}}}}""", headers = jsonHeaders)
                "/api/v3/comic2/book" -> if (failApi) respondError(HttpStatusCode.ServiceUnavailable) else respond("""
                    {"code":200,"results":{"is_banned":false,"is_lock":false,"is_login":true,"is_mobile_bind":false,"is_vip":false,
                    "popular":1,"groups":{"default":{"path_word":"default","count":1,"name":"默认"}},
                    "comic":{"uuid":"comic-id","b_404":false,"b_hidden":false,"ban":0,"name":"Book","path_word":"book",
                    "close_comment":false,"close_roast":false,"free_type":{"display":"","value":1},"restrict":{"display":"","value":0},
                    "reclass":{"display":"","value":0},"region":{"display":"","value":0},"status":{"display":"","value":0}}}}
                    """, headers = jsonHeaders)
                "/api/v3/comic/book/group/default/chapters" -> {
                    pageCalls++
                    respond("""{"code":200,"results":{"offset":0,"limit":100,"total":1,"list":[{
                        "index":0,"uuid":"a","count":1,"ordered":10,"size":39,"name":"第一章","comic_id":"comic-id",
                        "comic_path_word":"book","group_id":"group-id","group_path_word":"default","type":1,
                        "news":"success","datetime_created":"2026-10-28","prev":null,"next":null}]}}""", headers = jsonHeaders)
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        })
        private val client = CopyMangaClient(http, state, identity = identity)
        val repo = PortableMangaRepository(client)
        fun mode(value: RequestMode) { state.update { it.copy(settings = it.settings.copy(requestMode = value)) } }
        suspend fun chapter(refresh: Boolean = false) = assertNotNull(repo.getChapterCatalog("book", refresh).getOrNull())
            .chaptersByGroup.getValue("default").single()
        fun close() { client.close(); http.close() }
    }

    @Test fun switchingToAppEnrichesWebCacheAndWebRefreshCannotEraseIt() = runTest {
        val f = Fixture()
        try {
            assertNull(f.chapter().size)
            f.mode(RequestMode.OFFICIAL_APP)
            assertEquals(39, f.chapter().size)
            assertEquals(1, f.pageCalls)
            f.mode(RequestMode.WEB)
            val merged = f.chapter(refresh = true)
            assertEquals(39, merged.size)
            assertEquals("success", merged.news)
            assertEquals("2026-10-28", merged.datetimeCreated)
            assertEquals(10, merged.ordered)
            f.mode(RequestMode.OFFICIAL_APP)
            assertEquals(merged, f.chapter())
            assertEquals(1, f.pageCalls)
            assertEquals(2, f.htmlCalls)
        } finally { f.close() }
    }

    @Test fun failedApiEnrichmentIsNotReportedAsCompleteAndCanRetry() = runTest {
        val f = Fixture()
        try {
            f.chapter()
            f.mode(RequestMode.OFFICIAL_APP)
            f.failApi = true
            assertTrue(f.repo.getChapterCatalog("book", false).isFailure)
            f.failApi = false
            assertEquals(39, f.chapter().size)
            assertEquals(1, f.pageCalls)
        } finally { f.close() }
    }

    @Test fun failedWebRefreshDoesNotDiscardKnownApiMetadata() = runTest {
        val f = Fixture()
        try {
            f.mode(RequestMode.OFFICIAL_APP)
            val full = f.chapter()
            f.mode(RequestMode.WEB)
            f.failWeb = true
            assertTrue(f.repo.getChapterCatalog("book", true).isFailure)
            f.failWeb = false
            assertEquals(full, f.chapter(refresh = true))
            f.mode(RequestMode.OFFICIAL_APP)
            assertEquals(full, f.chapter())
            assertEquals(1, f.pageCalls)
        } finally { f.close() }
    }
}
