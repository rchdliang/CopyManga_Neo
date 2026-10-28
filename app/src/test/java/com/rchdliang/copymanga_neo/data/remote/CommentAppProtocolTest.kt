package com.rchdliang.copymanga_neo.data.remote

import com.google.gson.Gson
import com.rchdliang.copymanga_neo.data.remote.api.CommentApiService
import com.rchdliang.copymanga_neo.data.remote.dto.CommentResponseDto
import com.rchdliang.copymanga_neo.domain.model.MangaComment
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.OfficialAppSettings
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder

class CommentAppProtocolTest {
    private val secret = "synthetic-comment-test-secret"
    private val profile = OfficialAppSettings(
        version = "3.1.0", versionCode = "84", pseudoId = "synthetic-pseudo",
        umString = "synthetic-um", device = "synthetic-device",
        deviceInfo = "synthetic-model", buildTime = "2025.05.22"
    )
    private val success = """{"code":200,"message":"ok","results":null}"""

    private class Harness(val api: CommentApiService, val http: OkHttpClient, val requests: MutableList<Request>) {
        fun close() { http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll() }
    }
    /** 末端拦截器直接返回本地响应，测试不会访问真实服务或举报评论。 */
    private fun harness(mode: RequestMode, response: String = success): Harness {
        val requests = mutableListOf<Request>()
        val official = OfficialAppInterceptor({ it }, { "2026-10-28 12:00:00.000Z" }, { secret })
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val selected = request.tag(RequestMode::class.java) ?: mode
                chain.proceed(request.newBuilder().tag(NetworkRequestContext::class.java,
                    NetworkRequestContext(0, true, false, request.url.host,
                        NetworkSettings(requestMode = selected, officialApp = profile))
                ).build())
            }
            .addInterceptor(official)
            .addInterceptor { chain ->
                requests += chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(response.toResponseBody("application/json".toMediaType())).build()
            }.build()
        val api = Retrofit.Builder().baseUrl(CopyMangaNetworkConfig.API_BASE_URL)
            .client(http).callbackExecutor { it.run() }
            .addConverterFactory(GsonConverterFactory.create()).build().create(CommentApiService::class.java)
        return Harness(api, http, requests)
    }
    private fun body(request: Request) = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
    private fun form(request: Request): Map<String, String> = body(request).split('&').associate {
        val pair = it.split('=', limit = 2)
        URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
    }

    @Test fun reportsUseOfficialProtocolInAppAndPreserveWebFields() = runBlocking {
        for (mode in RequestMode.entries) {
            val h = harness(mode)
            try {
                val app = mode == RequestMode.OFFICIAL_APP
                val headers = CommentApiService.reportHeaders(app)
                val responses = listOf(
                    h.api.reportComment("target", "item", 6, "reason + &", "Token synthetic", headers = headers, requestMode = mode),
                    h.api.reportChapterComment("item", "target", 6, "reason + &", "Token synthetic", headers = headers, requestMode = mode),
                    h.api.reportNovelComment("target", "item", 6, "reason + &", "Token synthetic", headers = headers, requestMode = mode)
                )
                assertTrue(responses.all { it.body()?.code == 200 })
                val paths = listOf("comment", "roast", "bookcomment")
                h.requests.forEachIndexed { index, request ->
                    val fields = form(request)
                    assertEquals("POST", request.method)
                    assertEquals("/api/v3/member/${paths[index]}/report", request.url.encodedPath)
                    assertEquals(mode, request.tag(RequestMode::class.java))
                    assertNull(request.header("X-Copy-Comment"))
                    assertEquals(if(app)"3" else "1", request.header("platform"))
                    assertEquals(if(app)"3" else null, request.url.queryParameter("platform"))
                    assertEquals("target", fields[listOf("comic_id", "chapter_id", "book_id")[index]])
                    assertEquals("item", fields[if(index == 1)"roast_id" else "comment_id"])
                    assertEquals("6", fields["report_type"])
                    assertEquals("reason + &", fields["report_reason"])
                    assertEquals(if(app)null else "true", fields["_update"])
                    assertEquals(if(app)secret else null, fields["tapString"])
                    assertEquals(if(app)"Token synthetic" else null, fields["authorization"])
                    if(app) assertNotNull(request.header("X-auth-signature"))
                    else assertNull(request.header("X-auth-signature"))
                }
                assertEquals(3, h.requests.size)
            } finally { h.close() }
        }
    }

    @Test fun deletesCarryFormBodiesAndAcceptSuccessfulNullResults() = runBlocking {
        for (mode in RequestMode.entries) {
        val app = mode == RequestMode.OFFICIAL_APP
        val h = harness(mode)
        try {
            val headers = CommentApiService.postHeaders(app)
            assertEquals(200, h.api.deleteComicComment("comic", "item", "Token synthetic", headers, mode).body()?.code)
            assertEquals(200, h.api.deleteChapterComment("chapter", "item", "Token synthetic", headers, mode,
                update = true.takeIf { !app }).body()?.code)
            h.requests.forEachIndexed { index, request ->
                assertEquals("DELETE", request.method)
                val fields = form(request)
                assertEquals("item", fields[if(index == 0)"comment_id" else "roast_id"])
                assertEquals(if(index == 0)"comic" else "chapter", fields[if(index == 0)"comic_id" else "chapter_id"])
                assertEquals(if(app)"Token synthetic" else null, fields["authorization"])
                assertEquals(if(app)secret else null, fields["tapString"])
                assertEquals(if(!app && index == 1)"true" else null, fields["_update"])
                assertEquals(if(app)"3" else null, request.url.queryParameter("platform"))
                assertNull(request.url.queryParameter("comment_id"))
            }
            assertEquals(2, h.requests.size)
        } finally { h.close() }
        }
    }

    @Test fun topLevelComicUsesBareReplyOnlyInAppAndRepliesRetainTheirId() = runBlocking {
        for (mode in RequestMode.entries) {
            val h = harness(mode)
            try {
                val app = mode == RequestMode.OFFICIAL_APP
                h.api.postComicComment("comic", "text + &", "", "Token synthetic", headers = CommentApiService.postHeaders(app))
                h.api.postComicComment("comic", "reply", "parent", "Token synthetic", headers = CommentApiService.postHeaders(app))
                assertEquals("text + &", form(h.requests[0])["comment"])
                if(app) assertTrue("reply_id" in body(h.requests[0]).split('&'))
                else assertTrue("reply_id=" in body(h.requests[0]).split('&'))
                assertEquals(if(app)"3" else null, h.requests[0].url.queryParameter("platform"))
                assertEquals("parent", form(h.requests[1])["reply_id"])
            } finally { h.close() }
        }
    }

    @Test fun fullChapterViewAndAuthorFieldsSurviveProtocolAndParsing() = runBlocking {
        val response = """{"code":200,"results":{"list":[{"id":7,"user_id":"author","user_name":"nickname","comment":"text"}],"total":55,"limit":20,"offset":20}}"""
        for (mode in RequestMode.entries) {
        val h = harness(mode, response)
        try {
            val result = h.api.chapterComments("chapter", offset = 20, limit = 20,
                headers = CommentApiService.readHeaders(mode == RequestMode.OFFICIAL_APP), view = "list")
            val request = h.requests.single()
            assertEquals("list", request.url.queryParameter("view"))
            assertEquals("20", request.url.queryParameter("limit"))
            assertEquals("20", request.url.queryParameter("offset"))
            assertNull(request.url.queryParameter("_update"))
            assertEquals("author", result.body()?.results?.list?.single()?.userId)
            assertNull(Gson().fromJson("""{"id":"7","username":"nickname","content":"text"}""", MangaComment::class.java).userId)
        } finally { h.close() }
        }
    }

    @Test fun reportBusinessFailureIsPreservedWithoutRetry() = runBlocking {
        val h = harness(RequestMode.OFFICIAL_APP, """{"code":400,"message":"synthetic rejection","results":null}""")
        try {
            val result = h.api.reportChapterComment("item", "chapter", 6, "", "Token synthetic",
                headers = CommentApiService.reportHeaders(true), requestMode = RequestMode.OFFICIAL_APP)
            assertEquals(400, result.body()?.code)
            assertEquals(1, h.requests.size)
        } finally { h.close() }
    }

    @Test fun oldWebDefaultMigratesButCustomVersionAndAppProfileStayUnchanged() {
        val original = NetworkSettings(version = "2026.08.24")
        val migrated = NetworkSettingsCodec.decode(NetworkSettingsCodec.encode(original))
        assertEquals("2026.10.16", migrated.version)
        assertEquals(original.officialApp, migrated.officialApp)
        assertEquals("custom-version", NetworkSettingsCodec.decode(
            NetworkSettingsCodec.encode(original.copy(version = "custom-version"))
        ).version)
    }

    @Test fun webChapterPostContainsOnlyCapturedBusinessFields() = runBlocking {
        val h = harness(RequestMode.WEB)
        try {
            h.api.postChapterComment("chapter", "synthetic text", "Token synthetic",
                headers = CommentApiService.postHeaders(false))
            val request = h.requests.single()
            assertEquals(setOf("chapter_id", "roast"), form(request).keys)
            assertNull(request.url.queryParameter("platform"))
            assertEquals("2026.10.16", request.header("version"))
        } finally { h.close() }
    }
}
