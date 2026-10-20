package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PublicResourceTransportTest {
    @Test fun redirectResolutionMatrixDoesNotInheritQueriesOrBypassBoundaries() {
        val original = "https://cdn.example/pages/image.jpg?signature=synthetic&old=1"
        for ((location, expected) in listOf(
            "?replacement=2" to "https://cdn.example/pages/image.jpg?replacement=2",
            "//second.example/image.jpg" to "https://second.example/image.jpg",
            "//second.example/image.jpg?fresh=3" to "https://second.example/image.jpg?fresh=3",
            "../%2e/final.jpg" to "https://cdn.example/final.jpg"
        )) {
            val policy = PublicResourceRedirectPolicy(original, false, resourceBoundaryHosts())
            assertEquals(expected, policy.follow(location).toString(), "Location: $location")
        }
        for (location in listOf("#fragment", "#other", "/safe/%2e%2e/api/v3/quota",
            "/safe/.%2E/api/v3/quota", "/safe/%2E./api/v3/quota")) {
            val policy = PublicResourceRedirectPolicy(original, false, resourceBoundaryHosts())
            assertFailsWith<IllegalArgumentException>("Location: $location") { policy.follow(location) }
        }
    }

    @Test fun cdnAndRelativeRedirectsUseOnlySafeBrowserHeaders() = runTest {
        val visited = mutableListOf<String>()
        val http = HttpClient(MockEngine { request ->
            visited += request.url.toString()
            if (visited.size > 1) assertTrue(request.url.parameters.isEmpty())
            assertEquals(HttpMethod.Get, request.method)
            assertEquals(RESOURCE_USER_AGENT, request.headers[HttpHeaders.UserAgent])
            assertEquals(RESOURCE_LANGUAGE, request.headers[HttpHeaders.AcceptLanguage])
            assertEquals("bytes=0-10", request.headers[HttpHeaders.Range])
            listOf("Authorization", "Cookie", "Proxy-Authorization", "X-auth-signature", "Referer").forEach {
                assertNull(request.headers[it])
            }
            when (visited.size) {
                1 -> respond("ignored", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://next.example/pages/first"))
                2 -> respond("ignored", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "../final.jpg"))
                else -> respond("image", headers = headersOf(HttpHeaders.ETag, "final-tag"))
            }
        })
        val client = CopyMangaClient(http, PortableNetworkState())
        try {
            val result = client.resourceResponse("https://cdn.example/start?signature=synthetic", 8, requestHeaders = mapOf(
                "Authorization" to listOf("synthetic-secret"), "Cookie" to listOf("synthetic-cookie"),
                "Proxy-Authorization" to listOf("synthetic-proxy"), "X-auth-signature" to listOf("synthetic-signature"),
                "Referer" to listOf("https://private.example"), "Range" to listOf("bytes=0-10")
            ))
            assertEquals("image", result.bytes.decodeToString())
            assertEquals("https://next.example/final.jpg", visited.last())
            assertEquals(3, visited.size)
            assertEquals(listOf("final-tag"), result.headers.entries.first { it.key.equals("etag", true) }.value)
        } finally { client.close(); http.close() }
    }

    @Test fun everyConfiguredAndBuiltinBoundaryIsProtectedAnd304IsNotRedirected() = runTest {
        val settings = NetworkSettings(apiHost = "api.example", wwwHost = "www.example", loginHost = "login.example",
            fixedHost = "fixed.example", routes = listOf(listOf("route1.example", "route2.example")),
            officialApp = com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings(apiHost = "app.example", loginHost = "app-login.example"))
        val hosts = resourceBoundaryHosts(settings)
        for (host in listOf("api.example", "www.example", "login.example", "fixed.example", "route1.example",
            "route2.example", "app.example", "app-login.example") + resourceBoundaryHosts()) {
            assertTrue(host in hosts)
            assertFailsWith<IllegalArgumentException> {
                PublicResourceRedirectPolicy("https://cdn.example/start", false, hosts).follow("https://$host/image")
            }
        }
        var calls = 0
        val http = HttpClient(MockEngine { calls++; respond("", HttpStatusCode.NotModified, headersOf(HttpHeaders.Location, "/unused")) })
        val client = CopyMangaClient(http, PortableNetworkState())
        try {
            assertEquals(304, client.resourceResponse("https://cdn.example/cached", 32).status)
            assertEquals(1, calls)
        } finally { client.close(); http.close() }
    }

    @Test fun unsafeRedirectTargetsAreRejectedBeforeSecondRequest() = runTest {
        for (target in listOf("http://next.example/image", "https://user:password@next.example/image",
            "file:///tmp/image", "https://custom-api.example/image", "https://custom-www.example/image",
            "https://cdn.example/api/v3/chapter/quota", "https://custom-api.example./image",
            "https://@next.example/image", "https://cdn.example/safe/../api/v3/quota")) {
            var calls = 0
            val http = HttpClient(MockEngine { calls++; respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, target)) })
            val settings = NetworkSettings(apiHost = "custom-api.example", wwwHost = "custom-www.example")
            val client = CopyMangaClient(http, PortableNetworkState(NetworkSnapshot(settings = settings)))
            try {
                assertFailsWith<IllegalArgumentException> { client.resource("https://cdn.example/start", 32, allowHttp = true) }
                assertEquals(1, calls)
            } finally { client.close(); http.close() }
        }
    }

    @Test fun siteResourcesCannotEscapeBoundaryAndApiRedirectsRemainDisabled() = runTest {
        var calls = 0
        val http = HttpClient(MockEngine { calls++; respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://cdn.example/image")) })
        val client = CopyMangaClient(http, PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB))))
        try {
            assertFailsWith<IllegalArgumentException> { client.resource("https://${CopyMangaNetworkConfig.WWW_HOST}/comic/test", 32) }
            assertEquals(1, calls)
            assertFailsWith<ApiException> { client.raw(ApiRequest("/api/v3/register", method = "POST", form = listOf("username" to "synthetic"))) }
            assertEquals(2, calls)
        } finally { client.close(); http.close() }
    }

    @Test fun loopsAndRedirectLimitStopWithoutReadingLargeRedirectBodies() = runTest {
        for (loop in listOf(true, false)) {
            var calls = 0
            val http = HttpClient(MockEngine {
                calls++
                respond("x".repeat(64), HttpStatusCode.Found, headersOf(HttpHeaders.Location,
                    if (loop) "https://cdn.example/start" else "/hop-$calls"))
            })
            val client = CopyMangaClient(http, PortableNetworkState())
            try {
                assertFailsWith<IllegalArgumentException> { client.resource("https://cdn.example/start", 1) }
                assertEquals(if (loop) 1 else 6, calls)
            } finally { client.close(); http.close() }
        }
    }

    @Test fun generationChangeDuringRedirectCancelsBeforeNextHop() = runTest {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var calls = 0
        val http = HttpClient(MockEngine {
            calls++; started.complete(Unit); release.await()
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://next.example/image"))
        })
        val state = PortableNetworkState(); val client = CopyMangaClient(http, state)
        try {
            val request = async { client.resource("https://cdn.example/start", 32) }
            started.await()
            state.update { it.copy(settings = it.settings.copy(version = "changed")) }
            release.complete(Unit)
            assertFailsWith<CancellationException> { request.await() }
            assertEquals(1, calls)
        } finally { client.close(); http.close() }
    }
}
