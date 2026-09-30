package com.rchdliang.copymanga_neo.domain.usecase

import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.HomeComicsData
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.testing.webRepositoryFixture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class FilterHomeComicsTest {
    private val blocked = listOf(Theme("校园", "xiaoyuan"))
    private val requests = Collections.synchronizedList(mutableListOf<String>())
    private var responseFor: (String) -> Pair<Int, String> = { path ->
        200 to detail(if (path == "blocked") "xiaoyuan" else "maoxian")
    }
    private val api = Retrofit.Builder()
        .baseUrl("https://example.com/")
        .client(OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.pathSegments.last()
            requests.add(path)
            val (status, body) = responseFor(path)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build())
        .addConverterFactory(GsonConverterFactory.create()).build()
        .create(MangaApiService::class.java)
    private val repository = webRepositoryFixture(api)
    private val filter = FilterHomeComics(repository)

    @Test
    fun emptyHomeThemesAreResolvedAndBlockedComicIsHiddenInEverySection() = runBlocking {
        // 模拟抓包：首页 theme=[]，完结栏目 theme 缺失，详情才返回题材。
        val data = HomeComicsData(
            recComics = listOf(comic("blocked"), comic("allowed")),
            hotComics = listOf(comic("blocked")),
            newComics = listOf(comic("allowed"), comic("blocked")),
            finishComics = listOf(comic("blocked", null))
        )
        val result = filter.filter(data, blocked)
        assertEquals(listOf("allowed"), result.comics.recComics.map { it.pathWord })
        assertTrue(result.comics.hotComics.isEmpty())
        assertEquals(listOf("allowed"), result.comics.newComics.map { it.pathWord })
        assertTrue(result.comics.finishComics.isEmpty())
        assertEquals(setOf("blocked", "allowed"), requests.toSet())
        assertEquals(2, requests.size)
        assertEquals(0, result.unresolvedCount)
        assertEquals(2, data.recComics.size)
    }

    @Test
    fun disabledBlacklistDoesNotFetchDetailsAndRestoresOriginalLists() = runBlocking {
        val data = HomeComicsData(recComics = listOf(comic("blocked")))
        filter.filter(data, blocked)
        val callsBefore = requests.size
        assertSame(data, filter.filter(data, emptyList()).comics)
        assertEquals(callsBefore, requests.size)
    }

    @Test
    fun knownThemesNeedNoRequestAndUnknownEntriesNeverAppearWhileLoading() = runBlocking {
        val data = HomeComicsData(
            recComics = listOf(comic("blocked", blocked), comic("unknown")),
            hotComics = listOf(comic("allowed", listOf(Theme("冒险", "maoxian"))))
        )
        val preview = filter.verifiedPreview(data, blocked)
        assertTrue(preview.recComics.isEmpty())
        assertEquals("allowed", preview.hotComics.single().pathWord)
        filter.filter(data, blocked)
        assertEquals(listOf("unknown"), requests)
    }

    @Test
    fun detailConfirmedEmptyThemesAreAllowedAndCached() = runBlocking {
        responseFor = { 200 to """{"code":200,"results":{"comic":{"theme":[]}}}""" }
        val data = HomeComicsData(recComics = listOf(comic("no-themes")))
        repeat(2) {
            val result = filter.filter(data, blocked)
            assertEquals("no-themes", result.comics.recComics.single().pathWord)
            assertEquals(0, result.unresolvedCount)
        }
        assertEquals(1, requests.size)
    }

    @Test
    fun failedDetailsRemainVisibleAndRetriesRespectCooldown() = runBlocking {
        val data = HomeComicsData(recComics = listOf(comic("blocked")))
        responseFor = { 503 to "{}" }
        val failed = filter.filter(data, blocked)
        assertEquals("blocked", failed.comics.recComics.single().pathWord)
        assertEquals(1, failed.unresolvedCount)
        responseFor = { 200 to detail("xiaoyuan") }
        val retried = filter.filter(data, blocked)
        assertEquals("blocked", retried.comics.recComics.single().pathWord)
        assertEquals(1, retried.unresolvedCount)
        assertEquals(1, requests.size)
    }

    @Test
    fun requestsAreProcessedOneAtATime() = runBlocking {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val fake = object : MangaRepository by unsupported() {
            override suspend fun getComicThemes(pathWord: String): Result<List<Theme>> {
                val count = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, count) }
                try {
                    delay(10)
                    return Result.success(emptyList())
                } finally {
                    active.decrementAndGet()
                }
            }
        }
        val data = HomeComicsData(recComics = (1..12).map { comic("comic-$it") })
        assertEquals(12, FilterHomeComics(fake).filter(data, blocked).comics.recComics.size)
        assertEquals(1, maximum.get())
        assertEquals(0, active.get())
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotTreatedAsMissingTags() = runBlocking {
        val fake = object : MangaRepository by unsupported() {
            override suspend fun getComicThemes(pathWord: String): Result<List<Theme>> =
                throw CancellationException("cancelled")
        }
        FilterHomeComics(fake).filter(HomeComicsData(recComics = listOf(comic("x"))), blocked)
        Unit
    }

    private fun comic(path: String, themes: List<Theme>? = emptyList()) =
        ComicSummary(path, path, null, null, null, null, null, themes, null)

    private fun detail(path: String) =
        """{"code":200,"results":{"comic":{"theme":[{"name":"题材","path_word":"$path"}]}}}"""

    private fun unsupported(): MangaRepository = Proxy.newProxyInstance(
        MangaRepository::class.java.classLoader, arrayOf(MangaRepository::class.java)
    ) { _, method, _ -> error("Unexpected call: ${method.name}") } as MangaRepository
}
