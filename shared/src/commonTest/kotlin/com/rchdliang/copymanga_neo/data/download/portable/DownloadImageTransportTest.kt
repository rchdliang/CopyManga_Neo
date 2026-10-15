package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DownloadImageTransportTest {
    private val policy = DownloadNetworkState(allowed = true, wifiOnly = false)
    private fun transport(client: HttpClient, maximum: Int = 1024) = KtorDownloadImageTransport(object : DownloadClientProvider {
        override fun client(policy: DownloadNetworkState) = client
    }, maximum)
    @Test fun completeImageReturnsExactBytesAndChunkAccounting() = runTest {
        val expected = byteArrayOf(0, 1, 2, 3)
        val client = HttpClient(MockEngine { respond(expected, HttpStatusCode.OK, headersOf("Content-Type", "image/jpeg")) })
        try {
            var count = 0
            assertContentEquals(expected, transport(client).read("https://example.invalid/image", policy) { count += it })
            assertEquals(expected.size, count)
        } finally { client.close() }
    }
    @Test fun redirectsStreamOnlyFinalImageAndUseSafeHeaders() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls++
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(request.headers[HttpHeaders.UserAgent].orEmpty().startsWith("Mozilla/5.0"))
            assertEquals("zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7", request.headers[HttpHeaders.AcceptLanguage])
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[HttpHeaders.Cookie])
            if (calls == 1) respond("redirect body excluded", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://second.example/dir/page"))
            else if (calls == 2) respond("", HttpStatusCode.PermanentRedirect, headersOf(HttpHeaders.Location, "../image"))
            else {
                assertEquals("https://second.example/image", request.url.toString())
                respond("image", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
            }
        })
        try {
            var count = 0
            assertEquals("image", transport(client, 8).read("https://first.example/image", policy) { count += it }.decodeToString())
            assertEquals(5, count)
            assertEquals(3, calls)
        } finally { client.close() }
    }

    @Test fun forbiddenRedirectNeverReachesTargetEvenWithDefaultAutoRedirectClient() = runTest {
        for (target in listOf("http://cdn.example/image", "https://name:secret@cdn.example/image",
            "https://api.copy4000.com/api/v3/chapter/quota")) {
            var calls = 0
            val client = HttpClient(MockEngine { calls++; respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, target)) })
            try {
                assertFailsWith<PlatformIOException> { transport(client).read("https://first.example/image", policy) {} }
                assertEquals(1, calls)
            } finally { client.close() }
        }
    }

    @Test fun changedNetworkGenerationStopsDownloadBeforeRedirectTarget() = runTest {
        val state = com.rchdliang.copymanga_neo.data.remote.portable.PortableNetworkState()
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            state.update { it.copy(settings = it.settings.copy(version = "changed")) }
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://second.example/image"))
        })
        val provider = object : DownloadClientProvider { override fun client(policy: DownloadNetworkState) = client }
        try {
            assertFailsWith<PlatformIOException> { KtorDownloadImageTransport(provider, networkState = state).read("https://first.example/image", policy) {} }
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test fun riskResponsesNeverRetry() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { calls++; respond("", HttpStatusCode.TooManyRequests) })
        try {
            assertFailsWith<RiskControlException> { transport(client).read("https://example.invalid/image", policy) {} }
            assertEquals(1, calls)
        } finally { client.close() }
    }
    @Test fun disguisedRiskHtmlPausesRatherThanMarkingSuccess() = runTest {
        val client = HttpClient(MockEngine { respond("<!doctype html><html>访问频繁</html>", HttpStatusCode.OK) })
        try {
            assertFailsWith<RiskControlException> { transport(client).read("https://example.invalid/image", policy) {} }
        } finally { client.close() }
    }
    @Test fun overLimitEmptyAndNonImageBodiesFail() = runTest {
        for ((content, type) in listOf("x".repeat(33) to "image/jpeg", "" to "image/jpeg", "{}" to "application/json")) {
            val client = HttpClient(MockEngine { respond(content, HttpStatusCode.OK, headersOf("Content-Type", type)) })
            try {
                assertFailsWith<PlatformIOException> { transport(client, 32).read("https://example.invalid/image", policy) {} }
            } finally { client.close() }
        }
    }
    @Test fun cancellationFromChunkGuardPropagatesWithoutRetry() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { calls++; respond("abc", HttpStatusCode.OK, headersOf("Content-Type", "image/jpeg")) })
        try {
            assertFailsWith<CancellationException> {
                transport(client).read("https://example.invalid/image", policy) { throw CancellationException("paused") }
            }
            assertEquals(1, calls)
        } finally { client.close() }
    }
}
