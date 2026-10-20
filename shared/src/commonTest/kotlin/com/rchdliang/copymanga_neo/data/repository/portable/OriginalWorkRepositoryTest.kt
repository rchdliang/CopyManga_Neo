package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class OriginalWorkRepositoryTest {
    private val identity = object : PortableOfficialIdentity {
        override val tapString = "synthetic-original-test"
        override val loginDate = "2026-10-19"
        override fun dateHeader() = "2026.10.19"
        override fun resolve(settings: OfficialAppSettings) = settings.copy(
            pseudoId = "testProfileId000", umString = "0123456789abcdef0123456789abcdef",
            device = "test-device", deviceInfo = "test-model", buildTime = "test-build")
    }

    @Test fun bothModesUseCapturedParametersAndServerPaginationWindow() = runTest {
        for (mode in RequestMode.entries) {
            val path = "LoveLive & / 原作"
            val offsets = mutableListOf<Int>()
            val http = HttpClient(MockEngine { request ->
                assertEquals("/api/v3/comics", request.url.encodedPath)
                assertEquals(path, request.url.parameters["parody"])
                assertEquals("21", request.url.parameters["limit"])
                assertEquals("1", request.url.parameters["free_type"])
                assertEquals("-datetime_updated", request.url.parameters["ordering"])
                assertEquals(if (mode == RequestMode.OFFICIAL_APP) "3" else null, request.url.parameters["platform"])
                assertNull(request.url.parameters["q"])
                assertNull(request.url.parameters["q_type"])
                assertNull(request.url.parameters["_update"])
                assertEquals(if (mode == RequestMode.OFFICIAL_APP) "3" else "1", request.headers["platform"])
                val offset = request.url.parameters["offset"]!!.toInt()
                offsets += offset
                respond("""{"code":200,"results":{"list":[{"name":"Book","path_word":"book$offset"}],"offset":$offset,"limit":21,"total":42}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
            })
            val client = CopyMangaClient(http, PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = mode))), identity = identity)
            try {
                val repo = PortableComicDiscoveryRepository(client)
                val first = repo.getOriginalWorkComics(path, "-datetime_updated", 0).getOrThrow()
                assertEquals(21, first.nextOffset)
                assertEquals(42, first.total)
                assertNull(repo.getOriginalWorkComics(path, "-datetime_updated", first.nextOffset!!).getOrThrow().nextOffset)
                assertEquals(listOf(0, 21), offsets)
            } finally { client.close(); http.close() }
        }
    }

    @Test fun businessFailureAndInvalidCursorAreNotPublishedAsSuccessfulLists() = runTest {
        for (body in listOf(
            """{"code":400,"message":"not available","results":{"list":[],"offset":0,"limit":21,"total":0}}""",
            """{"code":200,"results":{"list":[{"name":"Book","path_word":"book"}],"offset":21,"limit":21,"total":42}}"""
        )) {
            val http = HttpClient(MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json")) })
            val client = CopyMangaClient(http, PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB))))
            try {
                assertTrue(PortableComicDiscoveryRepository(client).getOriginalWorkComics("LoveLive", "-datetime_updated", 0).isFailure)
            } finally { client.close(); http.close() }
        }
    }

    @Test fun riskGateRemainsActiveForOriginalWorkLists() = runTest {
        var calls = 0
        val http = HttpClient(MockEngine {
            calls++
            respond("""{"code":210,"message":"blocked"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val client = CopyMangaClient(http, PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB))))
        try {
            val repo = PortableComicDiscoveryRepository(client)
            assertIs<RiskControlException>(repo.getOriginalWorkComics("LoveLive", "-datetime_updated", 0).exceptionOrNull())
            assertNotNull(client.risk.notice.value)
            assertTrue(repo.getOriginalWorkComics("LoveLive", "-datetime_updated", 21).isFailure)
            assertEquals(1, calls)
        } finally { client.close(); http.close() }
    }
}
