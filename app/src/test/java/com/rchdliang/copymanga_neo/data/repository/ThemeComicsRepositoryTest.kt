package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.domain.model.QueryType
import com.rchdliang.copymanga_neo.testing.webRepositoryFixture
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ThemeComicsRepositoryTest {
    private var payload = ""
    private var status = 200
    private var lastRequest: Request? = null
    private val service = Retrofit.Builder()
        .baseUrl("https://example.com/")
        .client(
            OkHttpClient.Builder().addInterceptor { chain ->
                lastRequest = chain.request()
                Response.Builder()
                    .request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(status).message("test")
                    .body(payload.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()
        )
        .addConverterFactory(GsonConverterFactory.create()).build()
        .create(MangaApiService::class.java)
    private val repository = webRepositoryFixture(service)

    @Test
    fun themeUsesCapturedEndpointAndContinuesAfterShortPage() = runBlocking {
        payload = page(count = 20, total = 5450, offset = 0)
        val result =
            repository.getThemeComics("xiaoyuan").fold({ it }, { throw AssertionError(it) })
        val request = requireNotNull(lastRequest)
        assertEquals("/api/v3/comics", request.url.encodedPath)
        assertEquals("xiaoyuan", request.url.queryParameter("theme"))
        assertEquals("1", request.url.queryParameter("free_type"))
        assertEquals("21", request.url.queryParameter("limit"))
        assertEquals("-datetime_updated", request.url.queryParameter("ordering"))
        assertEquals("1", request.header("platform"))
        assertNull(request.header("X-No-Auth"))
        assertNull(request.url.queryParameter("q"))
        assertEquals(20, result.comics.size)
        assertEquals(21, result.nextOffset)
    }

    @Test
    fun nextPageUsesOffsetAndStopsAtTotal() = runBlocking {
        payload = page(count = 3, total = 24, offset = 21)
        val result = repository.getThemeComics("校园 & school", 21)
            .fold({ it }, { throw AssertionError(it) })
        assertEquals("21", lastRequest?.url?.queryParameter("offset"))
        assertEquals("校园 & school", lastRequest?.url?.queryParameter("theme"))
        assertEquals(3, result.comics.size)
        assertNull(result.nextOffset)
    }

    @Test
    fun emptyPageStopsEvenIfServerTotalIsLarger() = runBlocking {
        payload = page(count = 0, total = 100, offset = 21)
        val result = repository.getThemeComics("xiaoyuan", 21)
            .fold({ it }, { throw AssertionError(it) })
        assertNull(result.nextOffset)
    }

    @Test
    fun staleServerCursorDoesNotRepeatPages() = runBlocking {
        payload = page(count = 20, total = 100, offset = 0)
        val result = repository.getThemeComics("xiaoyuan", 21)
            .fold({ it }, { throw AssertionError(it) })
        assertNull(result.nextOffset)
    }

    @Test
    fun apiAndHttpErrorsRemainFailures() = runBlocking {
        payload = """{"code":210,"message":"稍后重试","results":null}"""
        assertEquals("稍后重试", repository.getThemeComics("xiaoyuan").exceptionOrNull()?.message)
        status = 503
        assertTrue(repository.getThemeComics("xiaoyuan").isFailure)
    }

    @Test
    fun keywordSearchKeepsItsOwnEndpointAndPagination() = runBlocking {
        payload = page(count = 1, total = 50, offset = 20)
        val result = repository.searchComics("测试", 2, QueryType.NAME)
        assertTrue(result.isSuccess)
        assertEquals("/api/v3/search/comic", lastRequest?.url?.encodedPath)
        assertEquals("测试", lastRequest?.url?.queryParameter("q"))
        assertEquals("20", lastRequest?.url?.queryParameter("offset"))
        assertNull(lastRequest?.url?.queryParameter("theme"))
    }

    private fun page(count: Int, total: Int, offset: Int): String {
        val comics = (0 until count).joinToString(",") {
            """{"name":"漫画$it","path_word":"comic-${offset + it}","theme":[]}"""
        }
        return """{"code":200,"results":{"list":[$comics],"total":$total,"limit":21,"offset":$offset}}"""
    }
}
