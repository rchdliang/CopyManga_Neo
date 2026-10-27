package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PortableAccountPersistenceTest {
    // Web 会话测试显式固定协议，不依赖产品的首次启动默认值。
    private fun webState() = PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB)))

    private val web = RequestMode.WEB
    private val app = RequestMode.OFFICIAL_APP
    private val identity = AppCredentialIdentity("1.0", "1", "pseudo", "um", "device", "info", "build")
    private fun credential(username: String, mode: RequestMode) = StoredCredential(
        PortableSession("old-${mode.name}", "user:$username", username, "old-avatar"), username, "old-password", true, true,
        if (mode == app) identity else null
    )
    private class Storage(initial: Map<RequestMode, StoredCredential>) : SecureCredentialStorage {
        override val persistent = true
        val values = initial.toMutableMap()
        var afterWrite: (() -> Unit)? = null
        override suspend fun readAll() = values.toMap()
        override suspend fun writeAll(values: Map<RequestMode, StoredCredential>) {
            this.values.clear(); this.values.putAll(values)
            afterWrite?.invoke()
        }
    }
    private fun engine() = HttpClient(MockEngine {
        respond("""{"code":200,"results":{"token":"new-token","username":"alice","avatar":"new-avatar"}}""",
            headers = headersOf(HttpHeaders.ContentType, "application/json"))
    })
    private fun imported(password: Boolean = true) = PortableCredentialCodec.encode(LoginCredentialBundle(
        web, listOf(LoginCredential(web, "imported-token", "alice")),
        savedLogin = if (password) SavedLoginCredential("alice", "imported-password") else null
    ))

    @Test fun webOnlyImportRejectsAppBeforeVerificationButAcceptsWebCredentials() = runTest {
        var requests = 0
        val http = HttpClient(MockEngine {
            requests++
            respond("""{"code":200,"results":{"username":"alice"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val state = PortableNetworkState(webOnly = true)
        val client = CopyMangaClient(http, state)
        try {
            val repository = PortableUserRepository(client)
            val appBundle = PortableCredentialCodec.encode(LoginCredentialBundle(
                app, listOf(LoginCredential(app, "app-token", "alice")), appIdentity = identity))
            assertIs<IllegalArgumentException>(repository.importLoginCredential(appBundle).exceptionOrNull())
            assertEquals(0, requests)
            assertEquals(RequestMode.WEB, state.snapshot.settings.requestMode)
            assertTrue(state.snapshot.webSession.token.isEmpty())
            repository.importLoginCredential(imported(password = false)).getOrThrow()
            assertEquals(1, requests)
            assertEquals("imported-token", state.snapshot.webSession.token)
            assertEquals(RequestMode.WEB, state.snapshot.settings.requestMode)
        } finally { client.close(); http.close() }
    }

    @Test fun verifiedLoginInvalidatesOnlySameAccountOtherModeAndRetainsItsPassword() = runTest {
        for (otherName in listOf("alice", "bob")) {
            val other = credential(otherName, app)
            val storage = Storage(mapOf(web to credential("alice", web), app to other))
            val http = engine(); val state = webState(); val client = CopyMangaClient(http, state)
            try {
                val repository = PortableUserRepository(client, storage)
                repository.restoreSecureSessions()
                repository.loginWithOptions("alice", "new-password", true, true, repository.sessionVersionSync()).getOrThrow()
                assertEquals("new-token", state.snapshot.webSession.token)
                val saved = storage.values.getValue(app)
                if (otherName == "alice") {
                    assertEquals("", state.snapshot.appSession.token)
                    assertEquals("", saved.session.token)
                    assertFalse(saved.autoLogin)
                    assertEquals("old-password", saved.savedPassword)
                } else assertEquals(other, saved)
            } finally { client.close(); http.close() }
        }
    }

    @Test fun uncheckedRememberClearsSavedLoginAcrossLogoutAndRestart() = runTest {
        for (clearViaPreferences in listOf(false, true)) {
            val storage = Storage(mapOf(web to credential("alice", web)))
            val http = engine(); val client = CopyMangaClient(http, webState())
            try {
                val repository = PortableUserRepository(client, storage)
                repository.restoreSecureSessions()
                if (clearViaPreferences) {
                    repository.saveCredentials("alice", "unused-password", false, false, repository.sessionVersionSync())
                } else {
                    repository.loginWithOptions("alice", "unused-password", false, false, repository.sessionVersionSync()).getOrThrow()
                }
                assertTrue(repository.isLoggedInSync())
                assertNull(repository.getSavedUsernameSync())
                assertNull(repository.getSavedPasswordSync())
                assertFalse(repository.getRememberPasswordSync())
                assertFalse(repository.getAutoLoginSync())
                repository.logout().getOrThrow()
                val before = storage.values.toMap()
                // The original store ignores preference edits once the active session is logged out.
                storage.afterWrite = { error("Logged-out preferences must not write") }
                repository.saveCredentials("unexpected-user", "unexpected-password", true, true, repository.sessionVersionSync())
                assertEquals(before, storage.values)
                val secondHttp = engine(); val secondClient = CopyMangaClient(secondHttp, webState())
                try {
                    val restored = PortableUserRepository(secondClient, storage)
                    restored.restoreSecureSessions()
                    assertFalse(restored.isLoggedInSync())
                    assertNull(restored.getSavedUsernameSync())
                    assertNull(restored.getSavedPasswordSync())
                    assertFalse(restored.getRememberPasswordSync())
                    assertFalse(restored.getAutoLoginSync())
                } finally { secondClient.close(); secondHttp.close() }
            } finally { client.close(); http.close() }
        }
    }

    @Test fun unrememberedLoginStillInvalidatesOtherModeUsingTransientInputAlias() = runTest {
        for (otherName in listOf("login-alias", "alice", "bob")) {
            val other = credential(otherName, app)
            val storage = Storage(mapOf(app to other))
            val http = engine(); val state = webState(); val client = CopyMangaClient(http, state)
            try {
                val repository = PortableUserRepository(client, storage)
                repository.restoreSecureSessions()
                repository.loginWithOptions("login-alias", "synthetic-password", false, false, repository.sessionVersionSync()).getOrThrow()
                assertNull(storage.values.getValue(web).savedUsername)
                assertNull(storage.values.getValue(web).savedPassword)
                val saved = storage.values.getValue(app)
                if (otherName == "bob") assertEquals(other, saved) else {
                    assertEquals("", saved.session.token)
                    assertFalse(saved.autoLogin)
                    assertEquals(other.savedUsername, saved.savedUsername)
                    assertEquals(other.savedPassword, saved.savedPassword)
                }
            } finally { client.close(); http.close() }
        }
    }

    @Test fun credentialImportPropagatesPasswordOnlyToCompatibleOtherMode() = runTest {
        for (other in listOf<StoredCredential?>(null, credential("alice", app), credential("bob", app),
            credential("alice", app).copy(savedUsername = "bob"))) {
            val storage = Storage(if (other == null) emptyMap() else mapOf(app to other))
            val http = engine(); val state = webState(); val client = CopyMangaClient(http, state)
            try {
                val repository = PortableUserRepository(client, storage)
                repository.restoreSecureSessions()
                repository.importLoginCredential(imported()).getOrThrow()
                val saved = storage.values.getValue(app)
                if (other == null || other.savedUsername == "alice") {
                    assertEquals("imported-password", saved.savedPassword)
                    assertTrue(saved.rememberPassword)
                    assertFalse(saved.autoLogin)
                    assertTrue(saved.session.token.isEmpty())
                } else {
                    assertEquals(other.savedPassword, saved.savedPassword)
                    assertEquals(other.savedUsername, saved.savedUsername)
                    if (other.session.username == "bob") assertEquals(other, saved)
                }
            } finally { client.close(); http.close() }
        }
    }

    @Test fun failedSnapshotWriteRollsBackBothModesBeforePublishing() = runTest {
        val initial = mapOf(web to credential("alice", web), app to credential("alice", app))
        val storage = Storage(initial)
        val http = engine(); val state = webState(); val client = CopyMangaClient(http, state)
        try {
            val repository = PortableUserRepository(client, storage)
            repository.restoreSecureSessions()
            val before = state.snapshot
            storage.afterWrite = {
                storage.afterWrite = null
                assertEquals("imported-token", storage.values.getValue(web).session.token)
                assertEquals("", storage.values.getValue(app).session.token)
                error("snapshot acknowledgement failed")
            }
            assertTrue(repository.importLoginCredential(imported()).isFailure)
            assertEquals(initial, storage.values)
            assertEquals(before, state.snapshot)
            assertEquals("old-password", repository.getSavedPasswordSync())
        } finally { client.close(); http.close() }
    }

    @Test fun metadataRefreshPersistsAndSurvivesPreferenceWritesAndRestart() = runTest {
        val storage = Storage(mapOf(web to credential("alice", web)))
        val http = engine(); val client = CopyMangaClient(http, webState())
        try {
            val repository = PortableUserRepository(client, storage)
            repository.restoreSecureSessions()
            val generation = repository.sessionVersionSync()
            repository.getMemberInfo().getOrThrow()
            assertEquals(generation, repository.sessionVersionSync())
            repository.saveCredentials("alice", "password", true, false, generation)
            assertEquals("new-avatar", storage.values.getValue(web).session.avatar)
            val secondHttp = engine(); val secondState = webState(); val secondClient = CopyMangaClient(secondHttp, secondState)
            try {
                PortableUserRepository(secondClient, storage).restoreSecureSessions()
                assertEquals("new-avatar", secondState.snapshot.webSession.avatar)
            } finally { secondClient.close(); secondHttp.close() }
        } finally { client.close(); http.close() }
    }

    @Test fun metadataWriteFailureOrConcurrentSessionChangeDoesNotPublishStaleProfile() = runTest {
        for (changeSession in listOf(false, true)) {
            val old = credential("legacy-alias", web)
            val storage = Storage(mapOf(web to old))
            val http = engine(); val state = webState(); val client = CopyMangaClient(http, state)
            try {
                val repository = PortableUserRepository(client, storage)
                repository.restoreSecureSessions()
                storage.afterWrite = {
                    storage.afterWrite = null
                    if (changeSession) state.update { it.copy(webSession = PortableSession("bob-token", "user:bob", "bob", "bob-avatar")) }
                    else error("metadata disk failure")
                }
                assertTrue(repository.getMemberInfo().isFailure)
                assertEquals(old, storage.values[web])
                assertEquals(if (changeSession) "user:bob" else "user:legacy-alias", state.snapshot.webSession.owner)
                assertEquals(if (changeSession) "bob-avatar" else "old-avatar", state.snapshot.webSession.avatar)
            } finally { client.close(); http.close() }
        }
    }
    @Test fun verifiedNameCorrectionUpdatesReadingOwnerAndFencesOldRequestsAcrossRestart() = runTest {
        // This can occur with credentials restored under an older/canonicalized account name.
        val old = credential("legacy-alias", web)
        val otherMode = credential("bob", app)
        val storage = Storage(mapOf(web to old, app to otherMode))
        val http = engine()
        val state = webState()
        val client = CopyMangaClient(http, state)
        val pendingOldRequest = kotlinx.coroutines.Job()
        try {
            val repository = PortableUserRepository(client, storage)
            repository.restoreSecureSessions()
            val generation = repository.sessionVersionSync()
            state.register(pendingOldRequest, generation)
            repository.getMemberInfo().getOrThrow() // Verified response contains username alice.
            assertEquals("alice", state.snapshot.webSession.username)
            assertEquals("user:alice", state.snapshot.webSession.owner)
            assertEquals("user:alice", com.rchdliang.copymanga_neo.data.local.portable.PortableReadingSession(state).getReadingSessionSync().owner)
            assertTrue(repository.sessionVersionSync() > generation)
            assertTrue(pendingOldRequest.isCancelled)
            val canonicalGeneration = repository.sessionVersionSync()
            repository.getMemberInfo().getOrThrow()
            assertEquals(canonicalGeneration, repository.sessionVersionSync(),
                "A session-change-triggered profile refresh must become idempotent")
            assertEquals(old.session.token, state.snapshot.webSession.token)
            assertEquals(otherMode, storage.values[app])
            assertEquals("legacy-alias", repository.getSavedUsernameSync(),
                "Remembered login input is distinct from verified reading identity")
            val restartedHttp = engine()
            val restartedState = webState()
            val restartedClient = CopyMangaClient(restartedHttp, restartedState)
            try {
                PortableUserRepository(restartedClient, storage).restoreSecureSessions()
                assertEquals("user:alice", restartedState.snapshot.webSession.owner)
                assertEquals("alice", restartedState.snapshot.webSession.username)
            } finally { restartedClient.close(); restartedHttp.close() }
        } finally {
            state.unregister(pendingOldRequest)
            pendingOldRequest.cancel()
            client.close(); http.close()
        }
    }

}
