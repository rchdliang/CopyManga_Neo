package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.data.remote.portable.api.*
import com.rchdliang.copymanga_neo.data.remote.portable.util.ProtocolCrypto
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import com.rchdliang.copymanga_neo.data.repository.portable.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

class CopyMangaClientTest {
    private val identity=object:PortableOfficialIdentity {
        override val tapString="test-only-not-a-production-secret"
        override val loginDate="2026-10-14 12:00:00.000Z"
        override fun dateHeader()="2026.10.14"
        override fun resolve(settings:OfficialAppSettings)=settings.copy(pseudoId="stablePseudoId00",umString="0123456789abcdef0123456789abcdef",device="test-device",deviceInfo="test-model",buildTime="2025.05.22")
    }
    private fun state(app:Boolean=false,token:String="")=PortableNetworkState(NetworkSnapshot(settings=NetworkSettings(requestMode=if(app)RequestMode.OFFICIAL_APP else RequestMode.WEB),webSession=PortableSession(token),appSession=PortableSession(token)))
    @Test fun webHeadersAndSearchPageWindowMatchUpstream()=runTest {
        val engine=MockEngine { request ->
            assertEquals("api.copy4000.com",request.url.host);assertEquals("1",request.headers["platform"])
            assertEquals(CopyMangaNetworkConfig.WEB_API_USER_AGENT,request.headers["User-Agent"])
            assertEquals("2026.08.24",request.headers["version"]);assertEquals("author",request.url.parameters["q_type"])
            assertEquals("hello world",request.url.parameters["q"]);assertNull(request.headers["Authorization"])
            respond("""{"code":200,"results":{"list":[],"total":0,"limit":20,"offset":0}}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }
        val client=CopyMangaClient(HttpClient(engine),state())
        // QueryType wire values are delegated to the original enum's toString contract.
        val response=MangaApiService(client).search(query="hello world",queryType=QueryType.AUTHOR)
        assertEquals(200,response.body()?.code);client.close()
    }
    @Test fun webApiIdentityAppliesToCustomHostsLoginAndMemberWithoutBrowserHeaders()=runTest {
        val observed=mutableListOf<String>()
        val engine=MockEngine { request ->
            observed+=request.url.host
            assertEquals(listOf(CopyMangaNetworkConfig.WEB_API_USER_AGENT),request.headers.getAll("User-Agent"))
            assertNull(request.headers["Sec-Fetch-Mode"])
            assertNull(request.headers["Cookie"])
            if(request.url.encodedPath=="/api/v3/login")assertNull(request.headers["Authorization"])
            else assertEquals("Token synthetic-token",request.headers["Authorization"])
            respond("""{"code":200,"results":{}}""")
        }
        val settings=NetworkSettings(apiHost="custom-api.example",wwwHost="custom-www.example",loginHost="custom-login.example")
        val state=PortableNetworkState(NetworkSnapshot(settings=settings,webSession=PortableSession("synthetic-token")))
        val client=CopyMangaClient(HttpClient(engine),state)
        try {
            client.raw(ApiRequest("/api/v3/member/info",headers=CopyMangaNetworkConfig.apiHeaders()+mapOf("user-agent" to "obsolete")))
            client.raw(ApiRequest("/api/v3/login",method="POST",form=listOf("username" to "synthetic"),headers=CopyMangaNetworkConfig.noAuthApiHeaders()))
            assertEquals(listOf("custom-api.example","custom-login.example"),observed)
        }finally{client.close()}
    }

    @Test fun wwwDetailAndReaderHtmlReceiveSourceBrowserNavigationHeaders()=runTest {
        var calls=0
        val engine=MockEngine { request ->
            calls++
            assertEquals("custom-www.example",request.url.host)
            assertEquals(RESOURCE_USER_AGENT,request.headers["User-Agent"])
            assertEquals(RESOURCE_LANGUAGE,request.headers["Accept-Language"])
            assertNull(request.headers["Authorization"])
            assertNull(request.headers["Cookie"])
            assertNull(request.headers["X-auth-signature"])
            if(request.url.encodedPath.startsWith("/comic/")) {
                assertTrue(request.headers["Accept"].orEmpty().startsWith("text/html,"))
                assertEquals("navigate",request.headers["Sec-Fetch-Mode"])
                assertEquals("document",request.headers["Sec-Fetch-Dest"])
                assertEquals("?1",request.headers["Sec-Fetch-User"])
                assertEquals("1",request.headers["Upgrade-Insecure-Requests"])
                assertTrue(request.headers["sec-ch-ua"].orEmpty().contains("Chromium"))
            }else {
                assertNull(request.headers["Sec-Fetch-Mode"])
                assertEquals("*/*",request.headers["Accept"])
            }
            respond("<html>content</html>")
        }
        val state=PortableNetworkState(NetworkSnapshot(settings=NetworkSettings(wwwHost="custom-www.example"),webSession=PortableSession("synthetic-token")))
        val client=CopyMangaClient(HttpClient(engine),state)
        try {
            val api=MangaApiService(client,www=true)
            api.getComicHtml("book");api.getChapterHtml("book","chapter")
            client.raw(ApiRequest("/comicdetail/book/chapters",www=true,headers=CopyMangaNetworkConfig.chapterCatalogHeaders("book","synthetic")))
            assertEquals(3,calls)
        }finally{client.close()}
    }

    @Test fun officialHeadersRewriteAndRepeatedIdsArePreserved()=runTest {
        val events=mutableListOf<NetworkDiagnostic>()
        val engine=MockEngine { request ->
            assertEquals("api.copy202601.com",request.url.host);assertEquals("3",request.url.parameters["platform"])
            assertEquals("COPY/3.0.9",request.headers["User-Agent"])
            assertNull(request.url.parameters["_update"]);assertNull(request.headers["Origin"])
            assertEquals("Token token-for-test",request.headers["Authorization"])
            val timestamp=requireNotNull(request.headers["X-auth-timestamp"])
            assertEquals(ProtocolCrypto.hex(ProtocolCrypto.hmacSha256(identity.tapString.encodeToByteArray(),timestamp.encodeToByteArray())),request.headers["X-auth-signature"])
            val body=(request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            val form=parseQueryString(body)
            assertEquals(listOf("10","20"),form.getAll("ids"));assertEquals(identity.tapString,form["tapString"])
            assertEquals("Token token-for-test",form["authorization"])
            respond("""{"code":200,"results":[10,20]}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }
        val client=CopyMangaClient(HttpClient(engine),state(true,"token-for-test"),identity=identity,nowMillis={1234000},diagnostics=events::add)
        assertEquals(200,MemberWriteApiService(client).removeFavorites(listOf(10,20),"Token token-for-test").body()?.code)
        assertEquals(listOf("request","response"),events.map{it.phase})
        assertFalse(events.toString().contains("token-for-test"));assertFalse(events.toString().contains(identity.tapString));client.close()
    }
    @Test fun officialEmptyReadNeverFallsBackToWeb()=runTest {
        var calls=0;val engine=MockEngine{calls++;respond("""{"code":200,"results":null}""")}
        val client=CopyMangaClient(HttpClient(engine),state(true),identity=identity)
        assertFailsWith<IllegalStateException>{client.raw(ApiRequest("/api/v3/h5/homeIndex"))};assertEquals(1,calls);client.close()
    }
    @Test fun riskStopsLaterRequestsAndUserCanResume()=runTest {
        var calls=0;val engine=MockEngine{calls++;respond("""{"code":210,"message":"slow down"}""")}
        val client=CopyMangaClient(HttpClient(engine),state())
        repeat(2){assertFailsWith<RiskControlException>{client.raw(ApiRequest("/api/v3/comics"))}}
        assertEquals(1,calls);assertEquals("slow down",client.risk.notice.value?.reason)
        client.risk.resumeByUser();assertFailsWith<RiskControlException>{client.raw(ApiRequest("/api/v3/comics"))};assertEquals(2,calls);client.close()
    }
    @Test fun validation210DoesNotCloseRiskGate()=runTest {
        val engine=MockEngine{respond("""{"code":210,"message":"评论长度为3-200"}""")}
        val client=CopyMangaClient(HttpClient(engine),state(token="test"))
        assertFailsWith<ApiException>{client.raw(ApiRequest("/api/v3/member/comment",method="POST",form=listOf("comment" to "hi")))}
        assertNull(client.risk.notice.value);client.close()
    }
    @Test fun redirectsAndUnauthenticatedMemberRequestsDoNotReplay()=runTest {
        var calls=0;val engine=MockEngine{calls++;respond("",HttpStatusCode.Found,headersOf(HttpHeaders.Location,"https://other.example/"))}
        val client=CopyMangaClient(HttpClient(engine),state())
        assertFailsWith<IllegalStateException>{client.raw(ApiRequest("/api/v3/member/info"))};assertEquals(0,calls)
        assertFailsWith<ApiException>{client.raw(ApiRequest("/api/v3/register",method="POST",form=listOf("username" to "test")))};assertEquals(1,calls);client.close()
    }
    @Test fun generationChangeCancelsInFlightResponse()=runTest {
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val engine=MockEngine{started.complete(Unit);release.await();respond("""{"code":200,"results":{}}""")}
        val state=state();val client=CopyMangaClient(HttpClient(engine),state)
        val call=async{client.raw(ApiRequest("/api/v3/comics"))};started.await();state.update{it.copy(settings=it.settings.copy(apiHost="new.example"))};release.complete(Unit)
        assertFailsWith<CancellationException>{call.await()};client.close()
    }
    @Test fun externalResourcesReceiveNoCredentials()=runTest {
        val engine=MockEngine{request->assertEquals("cdn.example",request.url.host);assertNull(request.headers["Authorization"]);assertNull(request.headers["Cookie"]);assertNull(request.headers["X-auth-signature"]);respond("image")}
        val client=CopyMangaClient(HttpClient(engine),state(true,"secret"),identity=identity)
        assertEquals("image",client.resource("https://cdn.example/page.jpg",1024).decodeToString());client.close()
    }
    @Test fun manualProxyDoesNotSilentlyUseDirectConnection()=runTest {
        var calls=0;val engine=MockEngine{calls++;respond("unused")}
        val state=PortableNetworkState(NetworkSnapshot(settings=NetworkSettings(proxyMode=ProxyMode.MANUAL,proxyAddress="http://127.0.0.1:1234")))
        val client=CopyMangaClient(HttpClient(engine),state)
        assertFailsWith<IllegalArgumentException>{client.raw(ApiRequest("/api/v3/comics"))};assertEquals(0,calls);client.close()
    }
    @Test fun rememberingPasswordWithoutSecureStorageFailsBeforeLogin()=runTest {
        var calls=0;val engine=MockEngine{calls++;respond("unused")};val client=CopyMangaClient(HttpClient(engine),state())
        val users=PortableUserRepository(client)
        assertTrue(users.loginWithOptions("testuser","testpassword",true,true,0).isFailure);assertEquals(0,calls);client.close()
    }
    @Test fun changingProxySettingsRebuildsEngineAndBoundCloneKeepsBinding()=runTest {
        val configured=mutableListOf<ProxyMode>();val requests=mutableListOf<String>()
        fun clientFor(label:String)=HttpClient(MockEngine{requests+=label;respond("{\"code\":200,\"results\":{}}")})
        val state=state()
        val client=CopyMangaClient(clientFor("unused"),state,proxy=PortableProxyPolicy{},engineProvider={settings->configured+=settings.proxyMode;clientFor(settings.proxyMode.name)})
        client.raw(ApiRequest("/api/v3/comics"))
        state.update{it.copy(settings=it.settings.copy(proxyMode=ProxyMode.DIRECT))}
        client.raw(ApiRequest("/api/v3/comics"))
        val bound=client.withEngineClient(clientFor("bound"),PortableProxyPolicy{})
        bound.raw(ApiRequest("/api/v3/comics"))
        assertEquals(listOf(ProxyMode.SYSTEM,ProxyMode.DIRECT),configured)
        assertEquals(listOf("SYSTEM","DIRECT","bound"),requests)
        bound.close();client.close()
    }
    @Test fun wwwCookiesRespectPathAndDoNotReachApi()=runTest {
        var calls=0
        val engine=MockEngine { request ->
            calls++
            when(calls) {
                1->{assertNull(request.headers["Cookie"]);respond("{\"code\":200,\"results\":{}}",headers=headersOf(HttpHeaders.SetCookie,"site=abc; Path=/comic; Secure"))}
                2->{assertEquals("site=abc",request.headers["Cookie"]);respond("{\"code\":200,\"results\":{}}")}
                else->{assertNull(request.headers["Cookie"]);respond("{\"code\":200,\"results\":{}}")}
            }
        }
        val client=CopyMangaClient(HttpClient(engine),state())
        client.raw(ApiRequest("/comic/book",www=true))
        client.raw(ApiRequest("/comic/book/chapter/id",www=true))
        client.raw(ApiRequest("/h5",www=true))
        client.raw(ApiRequest("/api/v3/comics"))
        assertEquals(4,calls);client.close()
    }

    @Test fun retiredBoundClientFailsBeforeOpeningAnyConnection()=runTest {
        var calls=0
        val source=HttpClient(MockEngine{calls++;respond("unused")})
        val state=state();val client=CopyMangaClient(source,state)
        val bound=client.withEngineClient(source,generation=state.snapshot.generation)
        state.update{it.copy(settings=it.settings.copy(apiHost="changed.example"))}
        assertFailsWith<IllegalStateException>{bound.raw(ApiRequest("/api/v3/comics"))}
        assertFailsWith<IllegalStateException>{bound.resource("https://cdn.example/image",1024)}
        assertEquals(0,calls)
        state.awaitRetiredRequests(state.snapshot.generation)
        bound.close();client.close();source.close()
    }

    @Test fun resourceLimitStopsStreamingBeforeEntireBodyIsBuffered()=runTest {
        val channel=ByteChannel(autoFlush=true)
        // Ktor 3.6 ByteChannel buffers 1 MiB before applying backpressure. A body of
        // exactly that size can be fully produced before the reader gets scheduled,
        // even when the response is streamed correctly. Exceed that transport buffer.
        val totalBytes=8*1024*1024
        var produced=0
        val producer=backgroundScope.launch {
            try {
                repeat(totalBytes/1024) { channel.writeFully(ByteArray(1024));produced+=1024 }
            } catch(_:Exception) { /* Reader cancellation is expected when the size limit is reached. */ }
            finally { channel.close() }
        }
        val engine=HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher=StandardTestDispatcher(testScheduler)
            addHandler { respond(channel) }
        }))
        val client=CopyMangaClient(engine,state())
        try {
            val failure=assertFailsWith<IllegalStateException>{client.resource("https://cdn.example/oversized.jpg",64)}
            assertEquals("响应超过大小限制",failure.message)
            // Join without cancelling: the bounded reader must close the channel and
            // release the blocked producer itself, rather than relying on test cleanup.
            producer.join()
            assertNotNull(channel.closedCause,"The bounded reader must cancel the oversized response")
            assertTrue(produced<totalBytes,"An oversized response must stop before downloading its entire body")
        } finally {
            producer.cancelAndJoin();client.close();engine.close()
        }
    }

}
