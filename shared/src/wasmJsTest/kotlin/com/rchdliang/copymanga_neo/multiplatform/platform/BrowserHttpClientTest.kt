@file:OptIn(io.ktor.utils.io.InternalAPI::class, kotlin.js.ExperimentalWasmJsInterop::class)

package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.data.remote.portable.PortableTransportException
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.JsError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.JsFun
import kotlin.js.JsAny
import kotlin.test.*

class BrowserHttpClientTest {
    @Test
    fun navigationHeadersAreRemovedButApiHeadersRemain() = runTest {
        val engine = MockEngine { request ->
            assertNull(request.headers["Upgrade-Insecure-Requests"])
            assertNull(request.headers["Priority"])
            assertNull(request.headers["Sec-Fetch-Mode"])
            assertNull(request.headers["Cookie"])
            assertEquals("Token test", request.headers["Authorization"])
            assertEquals("1", request.headers["platform"])
            assertEquals("catalog-token", request.headers["dnts"])
            respond("ok", HttpStatusCode.OK)
        }
        val client = HttpClient(browserTransportEngine(engine))
        try {
            val response = client.get("https://www.example.com/comic/test") {
                header("Upgrade-Insecure-Requests", "1")
                header("Priority", "u=0, i")
                header("Sec-Fetch-Mode", "navigate")
                header("Cookie", "webp=1")
                header("Authorization", "Token test")
                header("platform", "1")
                header("dnts", "catalog-token")
            }
            assertEquals("ok", response.bodyAsText())
            assertEquals("www.example.com", response.call.request.url.host)
        } finally { client.close(); engine.close() }
    }

    @Test
    fun fetchErrorBecomesRepositoryCompatibleException() = runTest {
        val engine = MockEngine { throw Error("Fail to fetch", JsError(fetchFailure())) }
        val client = HttpClient(browserTransportEngine(engine))
        try {
            assertFailsWith<PortableTransportException> { client.get("https://www.example.com/") }
        } finally { client.close(); engine.close() }
    }

    @Test
    fun cancellationAndProgrammingErrorsAreNotConverted() = runTest {
        for (failure in listOf(CancellationException("cancelled"), AssertionError("bug"))) {
            val engine = MockEngine { throw failure }
            val client = HttpClient(browserTransportEngine(engine))
            try {
                val thrown = try { client.get("https://www.example.com/"); null } catch (error: Throwable) { error }
                assertTrue(thrown is CancellationException || thrown is AssertionError)
                assertFalse(thrown is PortableTransportException)
            } finally { client.close(); engine.close() }
        }
    }
}

@JsFun("() => new TypeError('Failed to fetch')")
private external fun fetchFailure(): JsAny
