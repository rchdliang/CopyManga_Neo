package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.CommentDeletedCacheException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class PortableCommentContractTest {
    private val identity = object : PortableOfficialIdentity {
        override val tapString = "synthetic-comment-test-secret"
        override val loginDate = "2026-10-28 12:00:00.000Z"
        override fun dateHeader() = "2026.10.28"
        override fun resolve(settings: OfficialAppSettings) = settings.copy(
            pseudoId = "synthetic-pseudo", umString = "synthetic-um", device = "synthetic-device",
            deviceInfo = "synthetic-model", buildTime = "2025.05.22"
        )
    }
    private fun state(mode: RequestMode = RequestMode.OFFICIAL_APP) = PortableNetworkState(NetworkSnapshot(
        settings = NetworkSettings(requestMode = mode),
        appSession = PortableSession("synthetic-token"), webSession = PortableSession("synthetic-token")
    ))
    private fun comment(author: String? = "author-id") =
        MangaComment("comment-id", "same nickname", null, "synthetic comment", "", userId = author)
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    private val member = """{"code":200,"results":{"user_id":"author-id","username":"synthetic-user"}}"""
    private val success = """{"code":200,"message":"ok","results":null}"""

    @Test fun fullChapterFeedAndPaginationUseListViewInBothModes() = runTest {
        for (mode in RequestMode.entries) {
            var calls = 0
            val engine = MockEngine { request ->
                assertEquals("/api/v3/roasts", request.url.encodedPath)
                assertEquals("list", request.url.parameters["view"])
                assertEquals("20", request.url.parameters["limit"])
                assertNull(request.url.parameters["_update"])
                assertNull(request.url.parameters["request_id"])
                val offset = if (calls++ == 0) 0 else 20
                assertEquals(offset.toString(), request.url.parameters["offset"])
                respond("""{"code":200,"results":{"list":[{"id":17,"user_id":"author-id","user_name":"same nickname","comment":"hello"}],"total":55,"limit":20,"offset":$offset}}""", headers = headers)
            }
            val client = CopyMangaClient(HttpClient(engine), state(mode), identity = identity)
            try {
                val repository = PortableCommentRepository(client)
                val target = CommentTarget(CommentKind.CHAPTER, "chapter-id")
                // 仓库在 Default 执行；超时也使用真实调度器，避免测试虚拟时钟抢跑。
                val first = withContext(Dispatchers.Default) { repository.getComments(target).getOrThrow() }
                assertEquals("author-id", first.comments.single().userId)
                assertEquals(20, first.nextOffset)
                withContext(Dispatchers.Default) { repository.getComments(target, first.nextOffset!!).getOrThrow() }
                assertEquals(2, calls)
            } finally { client.close() }
        }
    }

    @Test fun comicTopLevelAndReplyFormsMatchCaptureWithoutChangingWebEncoding() = runTest {
        for (mode in RequestMode.entries) {
            var calls = 0
            val engine = MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/api/v3/member/comment", request.url.encodedPath)
                assertEquals(if(mode == RequestMode.OFFICIAL_APP)"3" else null, request.url.parameters["platform"])
                val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                val form = parseQueryString(body)
                assertEquals("text + & reply_id=", form["comment"])
                if (calls++ == 0) {
                    if (mode == RequestMode.OFFICIAL_APP) assertTrue("reply_id" in body.split('&'))
                    else assertTrue("reply_id=" in body.split('&'))
                } else assertEquals("parent-id", form["reply_id"])
                assertEquals(if(mode == RequestMode.OFFICIAL_APP) identity.tapString else null, form["tapString"])
                respond(success, headers = headers)
            }
            val client = CopyMangaClient(HttpClient(engine), state(mode), identity = identity)
            try {
                val repository = PortableCommentRepository(client)
                val target = CommentTarget(CommentKind.COMIC, "comic-id")
                repository.postComment(target, "text + & reply_id=").getOrThrow()
                repository.postComment(target, "text + & reply_id=", "parent-id").getOrThrow()
                assertEquals(2, calls)
            } finally { client.close() }
        }
    }

    @Test fun deletesVerifyAuthorAndSendAuthenticatedDeleteFormsAcceptingNullResults() = runTest {
        for (mode in RequestMode.entries) for (kind in listOf(CommentKind.COMIC, CommentKind.CHAPTER)) {
            var deletes = 0
            var members = 0
            val engine = MockEngine { request ->
                assertEquals("Token synthetic-token", request.headers[HttpHeaders.Authorization])
                if (request.method == HttpMethod.Get) {
                    assertEquals("/api/v3/member/info", request.url.encodedPath)
                    members++
                    respond(member, headers = headers)
                } else {
                    deletes++
                    val chapter = kind == CommentKind.CHAPTER
                    assertEquals(HttpMethod.Delete, request.method)
                    assertEquals(if(chapter)"/api/v3/member/roast" else "/api/v3/member/comment", request.url.encodedPath)
                    val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    val form = parseQueryString(body)
                    assertEquals("target-id", form[if(chapter)"chapter_id" else "comic_id"])
                    assertEquals("comment-id", form[if(chapter)"roast_id" else "comment_id"])
                    val app = mode == RequestMode.OFFICIAL_APP
                    assertEquals(if(app)"Token synthetic-token" else null, form["authorization"])
                    assertEquals(if(app)identity.tapString else null, form["tapString"])
                    assertEquals(if(app)"3" else null, form["platform"])
                    assertEquals(if(app)"3" else null, request.url.parameters["platform"])
                    assertEquals(if(!app && chapter)"true" else null, form["_update"])
                    assertNull(request.url.parameters["comment_id"])
                    respond(success, headers = headers)
                }
            }
            val client = CopyMangaClient(HttpClient(engine), state(mode), identity = identity)
            try {
                PortableCommentRepository(client).deleteComment(CommentTarget(kind, "target-id"), comment()).getOrThrow()
                assertEquals(1, members)
                assertEquals(1, deletes)
            } finally { client.close() }
        }
    }

    @Test fun wrongAuthorMissingAuthorAndUnsupportedKindsNeverSendDelete() = runTest {
        var gets = 0
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            gets++
            respond(member, headers = headers)
        }
        val network = state()
        val client = CopyMangaClient(HttpClient(engine), network, identity = identity)
        try {
            val repository = PortableCommentRepository(client)
            val target = CommentTarget(CommentKind.COMIC, "target-id")
            assertTrue(repository.deleteComment(target, comment("another-id")).isFailure)
            assertTrue(repository.deleteComment(target, comment(null)).isFailure)
            assertTrue(repository.deleteComment(CommentTarget(CommentKind.NOVEL, "book"), comment()).isFailure)
            network.update { it.copy(settings = it.settings.copy(requestMode = RequestMode.WEB)) }
            assertTrue(repository.supportsDeletion(target))
            assertTrue(repository.deleteComment(target, comment("another-id")).isFailure)
            assertEquals(2, gets)
        } finally { client.close() }
    }

    @Test fun accountChangeDuringAuthorVerificationPreventsDelete() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val network = state()
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            started.complete(Unit)
            release.await()
            respond(member, headers = headers)
        }
        val client = CopyMangaClient(HttpClient(engine), network, identity = identity)
        try {
            val request = async {
                PortableCommentRepository(client).deleteComment(CommentTarget(CommentKind.COMIC, "comic"), comment())
            }
            started.await()
            network.update { it.copy(appSession = PortableSession("different-token")) }
            release.complete(Unit)
            assertFailsWith<kotlinx.coroutines.CancellationException> { request.await() }
        } finally { release.complete(Unit); client.close() }
    }

    @Test fun chapterDeletionCleansCachedRowsAndDistinguishesCacheFailureAfterSuccess() = runTest {
        for (failSave in listOf(false, true)) {
            var snapshot = DownloadedChapterComments(listOf(comment()), 1, true, savedAt = 1)
            val store = object : PortableDownloadedCommentStore {
                override suspend fun hasCompleteChapter(uuid: String) = true
                override suspend fun read(uuid: String) = snapshot
                override suspend fun save(uuid: String, value: DownloadedChapterComments) {
                    if(failSave) error("synthetic disk failure")
                    snapshot = value
                }
            }
            var deletes = 0
            val engine = MockEngine { request ->
                if (request.method == HttpMethod.Delete) {
                    deletes++
                    respond(success, headers = headers)
                } else respond(member, headers = headers)
            }
            val client = CopyMangaClient(HttpClient(engine), state(), identity = identity)
            try {
                val result = PortableCommentRepository(client, store)
                    .deleteComment(CommentTarget(CommentKind.CHAPTER, "chapter"), comment())
                if(failSave) assertIs<CommentDeletedCacheException>(result.exceptionOrNull())
                else {
                    result.getOrThrow()
                    assertTrue(snapshot.comments.isEmpty())
                    assertEquals(0, snapshot.total)
                }
                assertEquals(1, deletes)
            } finally { client.close() }
        }
    }

    @Test fun oldDownloadedCommentsRemainReadableWithoutAuthorAndCannotImplyOwnership() {
        val old = """{"id":"17","username":"same nickname","avatar":null,"content":"text","createdAt":""}"""
        assertNull(Json.decodeFromString<MangaComment>(old).userId)
    }

    @Test fun reportRequestsUseSelectedProtocolForComicChapterAndNovel() = runTest {
        for (mode in RequestMode.entries) for (kind in CommentKind.entries) {
            var calls = 0
            val engine = MockEngine { request ->
                calls++
                val name = when(kind) {
                    CommentKind.COMIC -> "comment"
                    CommentKind.CHAPTER -> "roast"
                    CommentKind.NOVEL -> "bookcomment"
                }
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/api/v3/member/$name/report", request.url.encodedPath)
                val app = mode == RequestMode.OFFICIAL_APP
                assertEquals(if(app)"api.copy202601.com" else "api.copy4000.com", request.url.host)
                assertEquals(if(app)"3" else "1", request.headers["platform"])
                assertEquals(if(app)"3" else null, request.url.parameters["platform"])
                assertEquals("Token synthetic-token", request.headers["Authorization"])
                val form = parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                assertEquals("target-id", form[when(kind) {
                    CommentKind.COMIC -> "comic_id"
                    CommentKind.CHAPTER -> "chapter_id"
                    CommentKind.NOVEL -> "book_id"
                }])
                assertEquals("item-id", form[if(kind == CommentKind.CHAPTER)"roast_id" else "comment_id"])
                assertEquals("6", form["report_type"])
                assertEquals("synthetic reason + &", form["report_reason"])
                assertEquals(if(app)null else "true", form["_update"])
                assertEquals(if(app)identity.tapString else null, form["tapString"])
                assertEquals(if(app)"Token synthetic-token" else null, form["authorization"])
                if(app) assertNotNull(request.headers["X-auth-signature"])
                else assertNull(request.headers["X-auth-signature"])
                respond(success, headers = headers)
            }
            val client = CopyMangaClient(HttpClient(engine), state(mode), identity = identity)
            try {
                PortableCommentRepository(client).reportComment(
                    CommentTarget(kind, "target-id"), "item-id", 6, "synthetic reason + &"
                ).getOrThrow()
                assertEquals(1, calls)
            } finally { client.close() }
        }
    }

    @Test fun reportBusinessFailureIsNotSuccessAndIsNeverRetried() = runTest {
        var calls = 0
        val client = CopyMangaClient(HttpClient(MockEngine {
            calls++
            respond("""{"code":400,"message":"synthetic rejection","results":null}""", headers = headers)
        }), state(), identity = identity)
        try {
            val result = PortableCommentRepository(client).reportComment(
                CommentTarget(CommentKind.CHAPTER, "chapter"), "item", 6, ""
            )
            assertTrue(result.isFailure)
            assertEquals("synthetic rejection", result.exceptionOrNull()?.message)
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test fun webProtocolMigrationPreservesCustomValuesAndAppIdentity() {
        val old = NetworkSettings(version = "2026.08.24")
        val normalized = com.rchdliang.copymanga_neo.data.local.portable.PortableNetworkValidation.normalize(old)
        assertEquals("2026.10.16", normalized.version)
        assertEquals(old.officialApp, normalized.officialApp)
        assertEquals("custom-version", com.rchdliang.copymanga_neo.data.local.portable.PortableNetworkValidation
            .normalize(old.copy(version = "custom-version")).version)
    }

    @Test fun webChapterPostOmitsUpdateAndAppIdentityFields() = runTest {
        val client = CopyMangaClient(HttpClient(MockEngine { request ->
            assertEquals("/api/v3/member/roast", request.url.encodedPath)
            val form = parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            assertEquals(setOf("chapter_id", "roast"), form.names())
            assertEquals("synthetic text", form["roast"])
            assertNull(request.url.parameters["platform"])
            respond(success, headers = headers)
        }), state(RequestMode.WEB))
        try {
            PortableCommentRepository(client).postComment(
                CommentTarget(CommentKind.CHAPTER, "chapter"), "synthetic text"
            ).getOrThrow()
        } finally { client.close() }
    }
}
