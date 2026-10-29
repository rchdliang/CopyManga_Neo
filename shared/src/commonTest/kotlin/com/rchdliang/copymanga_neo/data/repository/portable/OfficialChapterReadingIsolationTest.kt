package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import com.rchdliang.copymanga_neo.data.local.portable.PortableReadingProgress
import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*

/** 基于登录/游客抓包的协议差异；所有账号、漫画标识和图片地址均为合成数据。 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfficialChapterReadingIsolationTest {
    private class Documents : DocumentStorage {
        private val values = mutableMapOf<String, String>()
        override suspend fun read(key: String) = values[key]
        override suspend fun write(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }

    private class Fixture(loggedIn: Boolean = true) {
        val state = PortableNetworkState(NetworkSnapshot(
            settings = NetworkSettings(requestMode = RequestMode.OFFICIAL_APP),
            appSession = if (loggedIn) PortableSession("app-test-token", "user:app") else PortableSession(),
            webSession = PortableSession("web-test-token", "user:web"),
        ))
        val contentRequests = mutableListOf<Pair<String, String>>()
        val reportedChapters = mutableListOf<String>()
        var remoteChapter = "98"
        var quota = 0
        var quotaRejected = false
        var downloadCalls = 0
        var guestStatus = HttpStatusCode.OK
        var guestBody: String? = null
        private val identity = object : PortableOfficialIdentity {
            override val tapString = "chapter-test-only"
            override val loginDate = "2026-10-29"
            override fun dateHeader() = "2026.10.29"
            override fun resolve(settings: OfficialAppSettings) = settings.copy(
                pseudoId = "test-pseudo-id", umString = "test-um", device = "test-device",
                deviceInfo = "test-model", buildTime = "test-build")
        }
        val client = CopyMangaClient(HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val authorization = assertNotNull(request.headers[HttpHeaders.Authorization])
            assertEquals("3", request.url.parameters["platform"])
            assertNotNull(request.headers["X-auth-signature"])
            assertNull(request.headers[HttpHeaders.Cookie])
            assertNull(request.headers["X-No-Auth"])
            val json = when {
                path.contains("/chapter2/") -> {
                    val chapter = path.substringAfterLast('/')
                    contentRequests += chapter to authorization
                    val loggedIn = authorization == "Token app-test-token"
                    if (loggedIn) {
                        remoteChapter = chapter
                        reportedChapters += chapter
                    } else assertEquals("Token", authorization)
                    if (!loggedIn && guestStatus != HttpStatusCode.OK) {
                        return@MockEngine respond(
                            """{"code":${guestStatus.value},"message":"guest failure"}""",
                            guestStatus, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                    if (!loggedIn && guestBody != null) guestBody!! else chapterJson(chapter, loggedIn)
                }
                path == "/api/v3/comic2/book/query" -> {
                    assertEquals("Token app-test-token", authorization)
                    """{"code":200,"results":{"is_lock":false,"is_login":true,"is_mobile_bind":false,"is_vip":false,
                        "browse":{"path_word":"book","chapter_uuid":"$remoteChapter","chapter_name":"第${remoteChapter}话"}}}"""
                }
                path == "/api/v3/member/info" -> {
                    assertEquals("Token app-test-token", authorization)
                    """{"code":200,"results":{"day_downloads":$quota}}"""
                }
                path.contains("/chapter2download/") -> {
                    downloadCalls++
                    assertEquals("Token app-test-token", authorization)
                    if (quotaRejected) """{"code":400,"message":"下载次数已用完"}"""
                    else chapterJson(path.substringAfterLast('/'), true)
                }
                else -> error("Unexpected request: $path")
            }
            respond(json, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }), state, identity = identity)
        val repository = PortableMangaRepository(client, downloadClient = { client })
        fun close() = client.close()
    }

    @Test fun guestContentKeepsAllPagesAndOrderingWithEitherLoginState() = runTest {
        for (loggedIn in listOf(true, false)) {
            val f = Fixture(loggedIn)
            try {
                for ((chapter, size) in listOf("100" to 34, "101" to 26)) {
                    val content = f.repository.getChapterContent("book", chapter, "default").getOrThrow()
                    assertEquals((0 until size).map { "https://images.invalid/$chapter/$it.webp" }, content.imageUrls)
                    assertEquals((chapter.toInt() - 1).toString(), content.prevUuid)
                    assertEquals((chapter.toInt() + 1).toString(), content.nextUuid)
                }
                assertEquals(listOf("100" to "Token", "101" to "Token"), f.contentRequests)
                assertTrue(f.reportedChapters.isEmpty())
                assertEquals("98", f.remoteChapter)
            } finally { f.close() }
        }
    }

    @Test fun adjacentLoadsBeforeAndAfterConfirmedReadingCannotOverwriteCloudChapter() = runTest {
        val f = Fixture()
        val progress = PortableReadingProgress(Documents(), f.state, f.repository, backgroundScope)
        try {
            progress.awaitReady("user:app")
            f.repository.getChapterContent("book", "100", "default").getOrThrow()
            f.repository.getChapterContent("book", "99", "default").getOrThrow()
            progress.recordRead("user:app", "book", "100", "第100话", "default")
            progress.progress.first { it.records["book"]?.let { r -> r.chapterUuid == "100" && !r.pendingSync } == true }
            for (chapter in listOf("101", "99", "100")) {
                f.repository.getChapterContent("book", chapter, "default").getOrThrow()
                assertEquals("100", f.remoteChapter)
            }
            assertEquals(listOf("100"), f.reportedChapters)
            // 真正进入预载的下一话后，仍会使用 App 账号正常上报。
            progress.recordRead("user:app", "book", "101", "第101话", "default")
            progress.progress.first { it.records["book"]?.let { r -> r.chapterUuid == "101" && !r.pendingSync } == true }
            assertEquals("101", f.remoteChapter)
            assertEquals(listOf("100", "101"), f.reportedChapters)
        } finally { f.close() }
    }

    @Test fun guestReadingNeverUsesStoredWebAccountToReport() = runTest {
        val f = Fixture(loggedIn = false)
        val progress = PortableReadingProgress(Documents(), f.state, f.repository, backgroundScope)
        try {
            progress.awaitReady("guest")
            f.repository.getChapterContent("book", "100", "default").getOrThrow()
            progress.recordRead("guest", "book", "100", "第100话", "default")
            runCurrent()
            assertEquals(listOf("100" to "Token"), f.contentRequests)
            assertTrue(f.reportedChapters.isEmpty())
        } finally { f.close() }
    }

    @Test fun guestHttpFailureNeverRetriesWithAccountOrExpiresLogin() = runTest {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.ServiceUnavailable)) {
            val f = Fixture()
            try {
                f.guestStatus = status
                assertTrue(f.repository.getChapterContent("book", "100", "default").isFailure)
                assertEquals(listOf("100" to "Token"), f.contentRequests)
                assertEquals("app-test-token", f.state.snapshot.appSession.token)
                assertEquals("98", f.remoteChapter)
            } finally { f.close() }
        }
    }

    @Test fun incompleteOrLockedGuestChapterFailsWithoutAuthenticatedFallback() = runTest {
        for (body in listOf(
            """{"code":200,"results":{"is_lock":false,"is_login":false,"chapter":{"uuid":"100","size":34,"contents":[{"url":"https://images.invalid/0.webp"}],"words":[0]}}}""",
            """{"code":200,"results":{"is_lock":true,"is_login":false}}""",
            """{"code":200,"results":{"is_lock":false,"is_banned":true,"is_login":false}}""",
        )) {
            val f = Fixture()
            try {
                f.guestBody = body
                assertTrue(f.repository.getChapterContent("book", "100", "default").isFailure)
                assertEquals(listOf("100" to "Token"), f.contentRequests)
                assertTrue(f.reportedChapters.isEmpty())
            } finally { f.close() }
        }
    }

    @Test fun exhaustedDownloadQuotaUsesGuestContentAndCachesIt() = runTest {
        val f = Fixture()
        try {
            val first = f.repository.getChapterDownloadContent("book", "100", "default").getOrThrow()
            assertEquals(34, first.imageUrls.size)
            assertEquals(first, f.repository.getChapterDownloadContent("book", "100", "default").getOrThrow())
            assertEquals(0, f.downloadCalls)
            assertEquals(listOf("100" to "Token"), f.contentRequests)
            assertEquals("98", f.remoteChapter)
        } finally { f.close() }
    }

    @Test fun quotaRejectionFallsBackToGuestAndPreservesAccount() = runTest {
        val f = Fixture()
        try {
            f.quota = 1
            f.quotaRejected = true
            assertEquals(34, f.repository.getChapterDownloadContent("book", "100", "default").getOrThrow().imageUrls.size)
            assertEquals(1, f.downloadCalls)
            assertEquals(listOf("100" to "Token"), f.contentRequests)
            assertEquals("app-test-token", f.state.snapshot.appSession.token)
            assertEquals("98", f.remoteChapter)
        } finally { f.close() }
    }

    @Test fun availableDownloadQuotaStillUsesAuthenticatedDownloadEndpoint() = runTest {
        val f = Fixture()
        try {
            f.quota = 1
            assertEquals(34, f.repository.getChapterDownloadContent("book", "100", "default").getOrThrow().imageUrls.size)
            assertEquals(1, f.downloadCalls)
            assertTrue(f.contentRequests.isEmpty())
            assertEquals("98", f.remoteChapter)
        } finally { f.close() }
    }

    companion object {
        private fun chapterJson(chapter: String, loggedIn: Boolean): String {
            val size = if (chapter == "101") 26 else 34
            // 抓包中 contents 会随请求打乱，但 words 还原后的 34/26 页完全一致。
            val order = if (loggedIn) (0 until size).toList() else (0 until size).reversed().toList()
            val contents = order.joinToString(",") { """{"url":"https://images.invalid/$chapter/$it.webp"}""" }
            return """{"code":200,"results":{"is_lock":false,"is_banned":false,"is_login":$loggedIn,
                "is_mobile_bind":false,"is_vip":false,
                "comic":{"name":"Book","uuid":"book-id","path_word":"book","restrict":{"value":0,"display":"free"}},
                "chapter":{"index":0,"uuid":"$chapter","count":200,"ordered":1,"size":$size,"name":"第${chapter}话",
                    "comic_id":"book-id","comic_path_word":"book","prev":"${chapter.toInt() - 1}","next":"${chapter.toInt() + 1}",
                    "contents":[$contents],"words":[${order.joinToString(",")}]}}}"""
        }
    }
}
