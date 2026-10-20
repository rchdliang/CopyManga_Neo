package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.*

/** Synthetic requests only: never contacts a service or uses real credentials. */
class PortableLoginRetryTest {
    // Web 会话测试显式固定协议，不依赖产品的首次启动默认值。
    private fun webState() = PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB)))

    private class Storage(initial: Map<RequestMode, StoredCredential> = emptyMap()) : SecureCredentialStorage {
        override val persistent = true
        var values = initial
        var writes = 0
        override suspend fun readAll() = values
        override suspend fun writeAll(next: Map<RequestMode, StoredCredential>) { values = next; writes++ }
    }
    private fun password(request: io.ktor.client.request.HttpRequestData): String {
        val form = parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
        val salt = requireNotNull(form["salt"])
        return Base64.Default.decode(requireNotNull(form["password"])).decodeToString().removeSuffix("-$salt")
    }
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    private val success = """{"code":200,"results":{"token":"synthetic-new-token","username":"dummy-user"}}"""

    @Test fun wrongThenCorrectUsesEditedPasswordAndOnlyPersistsVerifiedSuccess() = runTest {
        // Also cover an existing good login: a failed replacement must not erase it.
        for (existing in listOf(false, true)) {
            val initial = if (!existing) emptyMap() else mapOf(RequestMode.WEB to StoredCredential(
                PortableSession("synthetic-old-token", "user:dummy-user", "dummy-user"),
                "dummy-user", "synthetic-old-password", true, true
            ))
            val storage = Storage(initial)
            val sent = mutableListOf<String>()
            val http = HttpClient(MockEngine { request ->
                if (request.url.encodedPath == "/api/v3/login") {
                    assertNull(request.headers[HttpHeaders.Cookie])
                    val raw = password(request); sent += raw
                    respond(if (raw == "synthetic-wrong") """{"code":210,"message":"密码错误"}""" else success, headers = headers)
                } else respond(success, headers = headers)
            })
            val client = CopyMangaClient(http, webState())
            try {
                val repository = PortableUserRepository(client, storage)
                repository.restoreSecureSessions()
                val generation = repository.sessionVersionSync()
                val failed = repository.loginWithOptions("dummy-user", "synthetic-wrong", true, true, generation)
                assertTrue(failed.isFailure)
                assertEquals(initial, storage.values)
                assertEquals(0, storage.writes)
                assertEquals(generation, repository.sessionVersionSync())
                assertEquals(existing, repository.isLoggedInSync())
                val retried = repository.loginWithOptions("dummy-user", "synthetic-correct", true, true, generation)
                assertEquals(listOf("synthetic-wrong", "synthetic-correct"), sent)
                retried.getOrThrow()
                assertEquals("密码错误", failed.exceptionOrNull()?.message)
                assertNull(client.risk.notice.value)
                assertEquals("synthetic-correct", repository.getSavedPasswordSync())
                assertTrue(repository.getAutoLoginSync())
                assertEquals(generation + 1, repository.sessionVersionSync())
                assertEquals(1, storage.writes)
                val restoredHttp = HttpClient(MockEngine { error("Restoring must not log in") })
                val restoredClient = CopyMangaClient(restoredHttp, webState())
                try {
                    val restored = PortableUserRepository(restoredClient, storage)
                    restored.restoreSecureSessions()
                    assertEquals("synthetic-correct", restored.getSavedPasswordSync())
                    assertTrue(restored.isLoggedInSync())
                } finally { restoredClient.close() }
            } finally { client.close() }
        }
    }

    @Test fun pendingAutomaticFailureCannotOverwriteSuccessfulManualRetry() = runTest {
        val stored = StoredCredential(PortableSession(), "dummy-user", "synthetic-old-password", true, true)
        val storage = Storage(mapOf(RequestMode.WEB to stored))
        val automaticStarted = CompletableDeferred<Unit>()
        val releaseAutomatic = CompletableDeferred<Unit>()
        val sent = mutableListOf<String>()
        val http = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/v3/login") {
                val raw = password(request); sent += raw
                if (raw == "synthetic-old-password") {
                    automaticStarted.complete(Unit)
                    releaseAutomatic.await()
                    respond("""{"code":210,"message":"密码错误"}""", headers = headers)
                } else respond(success, headers = headers)
            } else respond(success, headers = headers)
        })
        val client = CopyMangaClient(http, webState())
        try {
            val repository = PortableUserRepository(client, storage)
            repository.restoreSecureSessions()
            val generation = repository.sessionVersionSync()
            // Isolate cancellation of the obsolete request from the test's parent scope.
            val automatic = async {
                runCatching { repository.login("dummy-user", "synthetic-old-password", generation) }
            }
            automaticStarted.await()
            repository.loginWithOptions("dummy-user", "synthetic-correct", true, true, generation).getOrThrow()
            releaseAutomatic.complete(Unit)
            runCatching { automatic.await() }
            assertEquals(listOf("synthetic-old-password", "synthetic-correct"), sent)
            assertTrue(repository.isLoggedInSync())
            assertEquals("synthetic-correct", repository.getSavedPasswordSync())
            assertEquals(generation + 1, repository.sessionVersionSync())
            assertEquals(1, storage.writes)
        } finally { releaseAutomatic.complete(Unit); client.close() }
    }

    @Test fun automaticPasswordFailureDoesNotCancelManualLoginAlreadyInFlight() = runTest {
        val storage = Storage(mapOf(RequestMode.WEB to StoredCredential(
            PortableSession(), "dummy-user", "synthetic-old-password", true, true
        )))
        val automaticStarted = CompletableDeferred<Unit>()
        val manualStarted = CompletableDeferred<Unit>()
        val releaseAutomatic = CompletableDeferred<Unit>()
        val releaseManual = CompletableDeferred<Unit>()
        val http = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/v3/login") {
                if (password(request) == "synthetic-old-password") {
                    automaticStarted.complete(Unit); releaseAutomatic.await()
                    respond("""{"code":210,"message":"密码错误"}""", headers = headers)
                } else {
                    manualStarted.complete(Unit); releaseManual.await()
                    respond(success, headers = headers)
                }
            } else respond(success, headers = headers)
        })
        val client = CopyMangaClient(http, webState())
        try {
            val repository = PortableUserRepository(client, storage)
            repository.restoreSecureSessions()
            val generation = repository.sessionVersionSync()
            val automatic = async { repository.login("dummy-user", "synthetic-old-password", generation) }
            automaticStarted.await()
            val manual = async {
                repository.loginWithOptions("dummy-user", "synthetic-correct", true, true, generation)
            }
            manualStarted.await()
            releaseAutomatic.complete(Unit)
            assertEquals("密码错误", automatic.await().exceptionOrNull()?.message)
            assertNull(client.risk.notice.value)
            assertEquals(0, storage.writes)
            releaseManual.complete(Unit)
            manual.await().getOrThrow()
            assertEquals("synthetic-correct", repository.getSavedPasswordSync())
            assertEquals(generation + 1, repository.sessionVersionSync())
        } finally {
            releaseAutomatic.complete(Unit); releaseManual.complete(Unit); client.close()
        }
    }


    @Test fun genuineLoginRiskStillBlocksSubsequentRequestsAndPreservesCredentials() = runTest {
        for (reason in listOf("密码错误次数过多", "访问频繁，请稍后重试", "unknown risk")) {
            val storage = Storage()
            var requests = 0
            val http = HttpClient(MockEngine {
                requests++
                respond("""{"code":210,"message":"$reason"}""", headers = headers)
            })
            val client = CopyMangaClient(http, webState())
            try {
                val repository = PortableUserRepository(client, storage)
                repository.restoreSecureSessions()
                val generation = repository.sessionVersionSync()
                repeat(2) {
                    val result = repository.loginWithOptions("dummy-user", "synthetic-password", true, true, generation)
                    assertTrue(result.exceptionOrNull() is com.rchdliang.copymanga_neo.domain.repository.RiskControlException)
                }
                assertEquals(1, requests)
                assertEquals(reason, client.risk.notice.value?.reason)
                assertEquals(0, storage.writes)
                assertEquals(generation, repository.sessionVersionSync())
            } finally { client.close() }
        }
    }

}
