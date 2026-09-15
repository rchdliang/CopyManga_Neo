package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.ui.favorites.FavoritesUiState
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FavoritesRepositoryTest {
    private val requestedOffsets = mutableListOf<Int>()
    private var responseFor: (Request) -> Pair<Int, String> = { 200 to page(0, 0) }
    private val service = Retrofit.Builder()
        .baseUrl("https://example.com/")
        .client(
            OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                requestedOffsets += request.url.queryParameter("offset")!!.toInt()
                val (status, payload) = responseFor(request)
                Response.Builder()
                    .request(request).protocol(Protocol.HTTP_1_1)
                    .code(status).message("test")
                    .body(payload.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()
        )
        .addConverterFactory(GsonConverterFactory.create()).build()
        .create(MangaApiService::class.java)
    private val repository = MangaRepositoryImpl(service, service)

    @Test
    fun updateOnLaterPageIsIncludedEvenWhenFirstPageIsShort() = runBlocking {
        responseFor = { request ->
            val offset = request.url.queryParameter("offset")!!.toInt()
            assertEquals("21", request.url.queryParameter("limit"))
            200 to if (offset == 0) page(0, 22, comic(1, "latest"))
            else page(21, 22, comic(2, "older"))
        }

        val favorites = repository.getFavoriteComics().fold({ it }, { throw AssertionError(it) })
        assertEquals(listOf(0, 21), requestedOffsets)
        assertEquals(2, favorites.size)
        assertEquals(
            1,
            FavoritesUiState(isLoggedIn = true, favorites = favorites).updatedComicCount
        )
    }

    @Test
    fun badgeCountsOnlyReadComicsWithUnreadLatestChapter() = runBlocking {
        responseFor = {
            200 to page(
                0, 5,
                comic(1, null), comic(2, ""), comic(3, "latest"),
                comic(4, "older"), comic(5, "older", latestChapter = "")
            )
        }
        val favorites = repository.getFavoriteComics().fold({ it }, { throw AssertionError(it) })
        assertEquals(
            listOf(false, false, false, true, false),
            favorites.map { it.comic.hasUnreadUpdate })
        assertEquals(
            1,
            FavoritesUiState(isLoggedIn = true, favorites = favorites).updatedComicCount
        )
        assertEquals(
            0,
            FavoritesUiState(isLoggedIn = false, favorites = favorites).updatedComicCount
        )
    }

    @Test
    fun repeatedComicAcrossPagesIsCountedOnce() = runBlocking {
        responseFor = { request ->
            200 to if (request.url.queryParameter("offset") == "0") {
                page(0, 23, comic(1, "older"))
            } else {
                page(21, 23, comic(1, "latest"), comic(2, "older"))
            }
        }
        val favorites = repository.getFavoriteComics().fold({ it }, { throw AssertionError(it) })
        assertEquals(2, favorites.size)
        assertFalse(favorites.first().comic.hasUnreadUpdate)
        assertEquals(
            1,
            FavoritesUiState(isLoggedIn = true, favorites = favorites).updatedComicCount
        )
    }

    @Test
    fun emptyBookshelfSucceedsWithoutMoreRequests() = runBlocking {
        val result = repository.getFavoriteComics()
        assertTrue(result.isSuccess)
        assertEquals(emptyList<Any>(), result.getOrNull())
        assertEquals(listOf(0), requestedOffsets)
    }

    @Test
    fun laterPageFailureDoesNotPublishPartialCount() = runBlocking {
        responseFor = { request ->
            if (request.url.queryParameter("offset") == "0") {
                200 to page(0, 22, comic(1, "older"))
            } else {
                503 to """{"code":503,"message":"unavailable","results":null}"""
            }
        }
        assertTrue(repository.getFavoriteComics().isFailure)
        assertEquals(listOf(0, 21), requestedOffsets)
    }

    @Test
    fun staleCursorDoesNotLoopOrPublishPartialCount() = runBlocking {
        responseFor = { 200 to page(0, 100, comic(1, "older")) }
        assertTrue(repository.getFavoriteComics().isFailure)
        assertEquals(listOf(0, 21), requestedOffsets)
    }

    @Test
    fun emptyIntermediatePageDoesNotPublishPartialCount() = runBlocking {
        responseFor = { 200 to page(0, 100) }
        assertTrue(repository.getFavoriteComics().isFailure)
        assertEquals(listOf(0), requestedOffsets)
    }

    private fun page(offset: Int, total: Int, vararg comics: String): String =
        """{"code":200,"results":{"list":[${comics.joinToString(",")}],"total":$total,"limit":21,"offset":$offset}}"""

    private fun comic(id: Int, readChapter: String?, latestChapter: String = "latest"): String {
        val browse = readChapter?.let { """{"chapter_uuid":"$it"}""" } ?: "null"
        return """{"uuid":$id,"comic":{"uuid":"comic-$id","name":"漫画$id","path_word":"comic-$id","status":0,"last_chapter_id":"$latestChapter","last_chapter_name":"最新话","browse":$browse}}"""
    }
}
