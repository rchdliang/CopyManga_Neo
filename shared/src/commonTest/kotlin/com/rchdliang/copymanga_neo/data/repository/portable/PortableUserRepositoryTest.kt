package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PortableUserRepositoryTest {
    @Test fun savingPreferencesRollsBackIfNetworkChangesDuringSecureWrite()=runTest {
        val initial=StoredCredential(PortableSession("existing-token","owner","user"),savedUsername="user")
        val state=PortableNetworkState(NetworkSnapshot(settings = NetworkSettings(requestMode = RequestMode.WEB)))
        var onWrite:(()->Unit)?=null
        val storage=object:SecureCredentialStorage {
            override val persistent=true
            val values=mutableMapOf(RequestMode.WEB to initial)
            override suspend fun readAll()=values.toMap()
            override suspend fun writeAll(next:Map<RequestMode,StoredCredential>) {
                values.clear(); values.putAll(next)
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
