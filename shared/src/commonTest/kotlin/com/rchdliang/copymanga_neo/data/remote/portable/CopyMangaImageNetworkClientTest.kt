package com.rchdliang.copymanga_neo.data.remote.portable

import coil3.network.*
import com.rchdliang.copymanga_neo.data.remote.portable.util.ProtocolCrypto
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okio.Buffer
import kotlin.test.*

class CopyMangaImageNetworkClientTest {
    // Match the engine dispatcher to virtual time; a real IO dispatcher races runTest's timeout.
    private fun TestScope.engine(handler:suspend MockRequestHandleScope.(HttpRequestData)->HttpResponseData) =
        MockEngine(MockEngineConfig().apply { dispatcher=StandardTestDispatcher(testScheduler);addHandler(handler) })
    @Test fun preservesActualResponseAndCacheHeadersWithoutLeakingRequestCredentials()=runTest {
        val engine=engine { request ->
            assertNull(request.headers["Authorization"]);assertNull(request.headers["Cookie"]);assertNull(request.headers["X-Api-Key"])
            assertEquals("test-etag",request.headers["If-None-Match"])
            respond("missing image",HttpStatusCode.NotFound,headersOf("ETag" to listOf("server-etag"),"Content-Type" to listOf("text/plain")))
        }
        val client=CopyMangaClient(HttpClient(engine),PortableNetworkState())
        val image=CopyMangaImageNetworkClient(client)
        val headers=NetworkHeaders.Builder().set("Authorization","secret").set("Cookie","secret").set("X-Api-Key","secret").set("If-None-Match","test-etag").build()
        image.executeRequest(NetworkRequest("https://cdn.example/missing.jpg",headers=headers)) { response ->
            assertEquals(404,response.code);assertEquals("server-etag",response.headers["ETag"])
            val buffer=Buffer();requireNotNull(response.body).writeTo(buffer);assertEquals("missing image",buffer.readUtf8())
        }
        client.close()
    }
    @Test fun followsPublicImageRedirectAndPreservesFinalStatus()=runTest {
        var calls=0;val engine=engine{request ->
            calls++
            if(request.url.host=="cdn.example")respond("",HttpStatusCode.Found,headersOf(HttpHeaders.Location,"https://other.example/image.jpg"))
            else respond("missing",HttpStatusCode.NotFound)
        }
        val client=CopyMangaClient(HttpClient(engine),PortableNetworkState())
        CopyMangaImageNetworkClient(client).executeRequest(NetworkRequest("https://cdn.example/image.jpg")){assertEquals(404,it.code)}
        assertEquals(2,calls);client.close()
    }
    @Test fun imageTimeoutUsesReaderPreferenceAndCancelsRequest()=runTest {
        val engine=engine{delay(10_000);respond("late")};val client=CopyMangaClient(HttpClient(engine),PortableNetworkState())
        assertFailsWith<TimeoutCancellationException>{CopyMangaImageNetworkClient(client,timeoutSeconds={5}).executeRequest(NetworkRequest("https://cdn.example/slow.jpg")){}}
        client.close()
    }
    @Test fun imageAdapterRejectsNonGetBeforeOpeningConnection()=runTest {
        var calls=0;val engine=engine{calls++;respond("unused")};val client=CopyMangaClient(HttpClient(engine),PortableNetworkState())
        assertFailsWith<IllegalArgumentException>{CopyMangaImageNetworkClient(client).executeRequest(NetworkRequest("https://cdn.example/image.jpg",method="POST")){}}
        assertEquals(0,calls);client.close()
    }
    @Test fun gzipApiResponseIsDecodedBeforeJsonParsing()=runTest {
        val compressed=ProtocolCrypto.unhex("1f8b0800000000000203ab564ace4f4955b2323230d0512a4a2d2ecd292956b2aa56cacf56b22a292a4dadad0500306556f422000000")
        val engine=engine{respond(compressed,headers=headersOf(HttpHeaders.ContentEncoding to listOf("gzip"),HttpHeaders.ContentType to listOf("application/json")))}
        val client=CopyMangaClient(HttpClient(engine),PortableNetworkState())
        assertEquals("true",client.results(ApiRequest("/api/v3/comics"))["ok"].toString());client.close()
    }
    @Test fun cacheOnlyMissNeverConnectsEvenWithMixedCaseDirectives() = runTest {
        var calls = 0
        val engine = engine { calls++; respond("must not connect") }
        val client = CopyMangaClient(HttpClient(engine), PortableNetworkState())
        try {
            for (value in listOf("only-if-cached, max-stale=2147483647", "no-cache, ONLY-IF-CACHED")) {
                val headers = NetworkHeaders.Builder().set("cAcHe-CoNtRoL", value).build()
                assertFailsWith<okio.IOException> {
                    CopyMangaImageNetworkClient(client).executeRequest(
                        NetworkRequest("https://cdn.example/synthetic.jpg", headers = headers)
                    ) { error("Cache miss must not supply image bytes") }
                }
            }
            assertEquals(0, calls)
        } finally { client.close() }
    }

}
