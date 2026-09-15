package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ChapterMetadataRepositoryTest {
    private var total = 605
    private var pageSize = 100
    private var repeatPage = false
    private var emptyPage = false
    private val offsets = mutableListOf<Int>()
    private val service = Retrofit.Builder().baseUrl("https://example.com/").client(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertTrue(request.url.encodedPath.contains("/group/extra/chapters"))
            val requestedOffset = request.url.queryParameter("offset")!!.toInt()
            offsets += requestedOffset
            val offset = if (repeatPage) 0 else requestedOffset
            val list = if (emptyPage) "" else (offset until minOf(
                offset + pageSize,
                total
            )).joinToString(",") { i ->
                val next = if (i + 1 < total) "\"c${i + 1}\"" else "null"
                """{"uuid":"c$i","index":$i,"name":"chapter $i","comic_id":"comic","comic_path_word":"comic","group_path_word":"extra","prev":null,"next":$next}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                .message("test")
                .body(
                    """{"code":200,"results":{"list":[$list],"total":$total,"limit":100,"offset":$offset}}""".toResponseBody(
                        "application/json".toMediaType()
                    )
                )
                .build()
        }.build()
    ).addConverterFactory(GsonConverterFactory.create()).build().create(MangaApiService::class.java)
    private val repository = MangaRepositoryImpl(service, service)

    @Test
    fun findsChaptersBeyondTheFormerFiveHundredLimit() = runBlocking {
        val chapter = repository.getChapterInfo("comic", "c601", "extra")
            .fold({ it }, { throw AssertionError(it) })
        assertEquals("c602", chapter.nextChapter)
        assertEquals(listOf(0, 100, 200, 300, 400, 500, 600), offsets)
    }

    @Test
    fun shortPagesAdvanceByReceivedCount() = runBlocking {
        pageSize = 20
        val chapter = repository.getChapterInfo("comic", "c45", "extra")
            .fold({ it }, { throw AssertionError(it) })
        assertEquals("c45", chapter.uuid)
        assertEquals(listOf(0, 20, 40), offsets)
    }

    @Test
    fun oldLastChapterGetsANewSuccessorOnRefresh() = runBlocking {
        total = 1
        assertNull(repository.getChapterInfo("comic", "c0", "extra").getOrNull()?.nextChapter)
        total = 2
        assertEquals(
            "c1",
            repository.getChapterInfo("comic", "c0", "extra").getOrNull()?.nextChapter
        )
    }

    @Test
    fun emptyOrRepeatedPagesTerminateLookup() = runBlocking {
        repeatPage = true
        assertTrue(repository.getChapterInfo("comic", "absent", "extra").isFailure)
        assertEquals(listOf(0, 100), offsets)
        offsets.clear()
        emptyPage = true
        assertTrue(repository.getChapterInfo("comic", "absent", "extra").isFailure)
        assertEquals(listOf(0), offsets)
    }
}
