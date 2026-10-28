package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PortableUserRepositoryTest {
    @Test fun accountPasswordPreferencesAreSharedButModeTokensAndOtherAccountsAreNot() = runTest {
        val identity = AppCredentialIdentity("1.0", "1", "pseudo", "um", "device", "info", "build")
        val storage = object : SecureCredentialStorage {
            override val persistent = true
            var values = mapOf(
                RequestMode.WEB to StoredCredential(PortableSession("web-token", "user:alice", "alice")),
                RequestMode.OFFICIAL_APP to StoredCredential(PortableSession("app-token", "user:alice", "alice"), appIdentity = identity))
            override suspend fun readAll() = values
            override suspend fun writeAll(values: Map<RequestMode, StoredCredential>) { this.values = values }
        }
        val state = PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB)))
        val engine = HttpClient(MockEngine { error("No request should be sent") })
        val client = CopyMangaClient(engine, state)
        try {
            val user = PortableUserRepository(client, storage)
            user.restoreSecureSessions()
            user.saveCredentials("alice", "shared-password", true, true, state.snapshot.generation)
            assertEquals("web-token", storage.read(RequestMode.WEB)!!.session.token)
            assertEquals("app-token", storage.read(RequestMode.OFFICIAL_APP)!!.session.token)
            state.update { it.copy(settings = it.settings.copy(requestMode = RequestMode.OFFICIAL_APP)) }
            assertEquals("shared-password", user.getSavedPasswordSync())
            assertTrue(user.getAutoLoginSync())
            user.saveCredentials("alice", "", false, false, state.snapshot.generation)
            assertTrue(storage.values.values.all { !it.rememberPassword && it.savedPassword == null })
            assertEquals("web-token", storage.read(RequestMode.WEB)!!.session.token)

            storage.values = storage.values + (RequestMode.WEB to StoredCredential(
                PortableSession("bob-token", "user:bob", "bob"), "bob", "bob-password", true))
            val reloaded = PortableUserRepository(client, storage)
            reloaded.restoreSecureSessions()
            reloaded.saveCredentials("alice", "alice-password", true, false, state.snapshot.generation)
            assertEquals("bob-password", storage.read(RequestMode.WEB)!!.savedPassword)
            assertEquals("bob-token", storage.read(RequestMode.WEB)!!.session.token)
        } finally { client.close(); engine.close() }
    }
    @Test fun savingPreferencesRollsBackIfNetworkChangesDuringSecureWrite()=runTest {
        val initial=StoredCredential(PortableSession("existing-token","owner","user"),savedUsername="user")
        val state=PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB)))
        var onWrite:(()->Unit)?=null
        val storage=object:SecureCredentialStorage {
            override val persistent=true
            val values=mutableMapOf(RequestMode.WEB to initial)
            override suspend fun readAll()=values.toMap()
            override suspend fun writeAll(values:Map<RequestMode,StoredCredential>) {
                this.values.clear(); this.values.putAll(values)
                onWrite?.also{onWrite=null}?.invoke()
            }
        }
        val engine=HttpClient(MockEngine { error("No request should be sent") })
        val client=CopyMangaClient(engine,state)
        val user=PortableUserRepository(client,storage)
        user.restoreSecureSessions()
        val generation=state.snapshot.generation
        onWrite={state.update{it.copy(settings=it.settings.copy(version="changed"))}}
        assertFailsWith<IllegalStateException> { user.saveCredentials("new-name","new-password",true,false,generation) }
        assertEquals(initial,storage.read(RequestMode.WEB))
        assertEquals("user",user.getSavedUsernameSync())
        assertNull(user.getSavedPasswordSync())
        client.close();engine.close()
    }

    @Test fun memoryFallbackRejectsPasswordPersistenceWithoutWritingNetwork()=runTest {
        val engine=HttpClient(MockEngine { error("No request should be sent") })
        val client=CopyMangaClient(engine,PortableNetworkState())
        val user=PortableUserRepository(client)
        assertFailsWith<IllegalArgumentException> { user.saveCredentials("user","password",true,false,user.sessionVersionSync()) }
        assertNull(user.getSavedPasswordSync())
        client.close();engine.close()
    }
}
