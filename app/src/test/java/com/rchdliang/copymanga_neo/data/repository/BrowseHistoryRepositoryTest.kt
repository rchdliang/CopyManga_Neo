package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.testing.webRepositoryFixture
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class BrowseHistoryRepositoryTest {
    private var payload = ""
    private var status = 200
    private var request: Request? = null
    private val service = Retrofit.Builder()
        .baseUrl("https://example.com/")
        .client(OkHttpClient.Builder().addInterceptor { chain ->
            request = chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("test")
                .body(payload.toResponseBody("application/json".toMediaType())).build()
        }.build())
        .addConverterFactory(GsonConverterFactory.create()).build()
        .create(MangaApiService::class.java)
    private val repository = webRepositoryFixture(service)

    @Test
    fun capturedListContractKeepsReadChapterSeparateFromLatestChapter() = runBlocking {
        payload = page(total = 1)
        val result = repository.getBrowseHistory().fold({ it }, { throw AssertionError(it) })
        val actual = requireNotNull(request)
        assertEquals("GET", actual.method)
        assertEquals("/api/v3/member/browse/comics", actual.url.encodedPath)
        assertEquals("1", actual.url.queryParameter("free_type"))
        assertEquals("20", actual.url.queryParameter("limit"))
        assertEquals("0", actual.url.queryParameter("offset"))
        assertEquals("true", actual.url.queryParameter("_update"))
        assertEquals("1", actual.header("platform"))
        assertNull(actual.header("X-No-Auth"))
        val item = result.items.single()
        assertEquals("read-chapter", item.lastReadChapterId)
        assertEquals("第01话", item.lastReadChapterName)
        assertEquals("latest-chapter", item.latestChapterId)
        assertEquals("第68话", item.latestChapterName)
        assertEquals("example-comic", item.pathWord)
        assertNull(result.nextOffset)
    }

    @Test
    fun paginationUsesServerSpanAndTotalEvenForShortPage() = runBlocking {
        payload = page(total = 21)
        assertEquals(20, repository.getBrowseHistory().getOrNull()?.nextOffset)
        payload = page(offset = 20, total = 21)
        assertNull(repository.getBrowseHistory(20).getOrNull()?.nextOffset)
        assertEquals("20", request?.url?.queryParameter("offset"))
    }

    @Test
    fun emptyOrStalePagesCannotLoop() = runBlocking {
        payload = page(total = 100, empty = true)
        assertNull(repository.getBrowseHistory().getOrNull()?.nextOffset)
        payload = page(offset = 0, total = 100)
        assertNull(repository.getBrowseHistory(20).getOrNull()?.nextOffset)
    }

    @Test
    fun clearUsesDeleteWithCapturedFormAndAcceptsNullResults() = runBlocking {
        payload = """{"code":200,"message":"UPDATE_SUCCESS","results":null}"""
        assertTrue(repository.clearBrowseHistory().isSuccess)
        val actual = requireNotNull(request)
        assertEquals("DELETE", actual.method)
        assertEquals("/api/v3/member/browse/comics", actual.url.encodedPath)
        assertNull(actual.url.query)
        val body = requireNotNull(actual.body)
        assertEquals("application/x-www-form-urlencoded", body.contentType().toString())
        val buffer = Buffer()
        body.writeTo(buffer)
        assertEquals("free_type=1&_update=true", buffer.readUtf8())
    }

    @Test
    fun businessErrorsAndMissingResultsAreNotSuccessfulLoads() = runBlocking {
        payload = """{"code":210,"message":"稍后重试","results":null}"""
        assertEquals("稍后重试", repository.getBrowseHistory().exceptionOrNull()?.message)
        assertTrue(repository.clearBrowseHistory().isFailure)
        payload = """{"code":200,"results":null}"""
        assertTrue(repository.getBrowseHistory().isFailure)
    }

    @Test
    fun httpFailureCannotClearHistory() = runBlocking {
        status = 503
        payload = """{"code":200,"results":null}"""
        assertTrue(repository.clearBrowseHistory().isFailure)
        assertTrue(repository.getBrowseHistory().isFailure)
    }

    private fun page(offset: Int = 0, total: Int, empty: Boolean = false): String {
        val item =
            if (empty) "" else """{"id":1,"last_chapter_id":"read-chapter","last_chapter_name":"第01话","comic":{"name":"示例漫画","path_word":"example-comic","author":[{"name":"示例作者"}],"last_chapter_id":"latest-chapter","last_chapter_name":"第68话"}}"""
        return """{"code":200,"results":{"list":[$item],"total":$total,"limit":20,"offset":$offset}}"""
    }
}
