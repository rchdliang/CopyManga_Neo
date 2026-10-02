package com.rchdliang.copymanga_neo.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class AuthDataStoreLogoutTest {
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var auth: AuthDataStore

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = context.preferencesDataStoreFile("auth-logout-test-${UUID.randomUUID()}")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        auth = AuthDataStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        )
        withTimeout(5.seconds) { auth.cacheReadyFlow.first { it } }
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun manualLogoutDisablesAutoLoginAndRejectsOldResponses() = runBlocking {
        assertTrue(auth.saveAuthIfCurrent(auth.getAuthGeneration(), "token", "user", null))
        auth.saveCredentials("user", "password", rememberPassword = true, autoLogin = true)
        val oldGeneration = auth.getAuthGeneration()

        auth.clearAuth(manualLogout = true)

        assertFalse(auth.isLoggedInSync())
        assertFalse(auth.getAutoLoginSync())
        assertFalse(auth.getAutoLogin().first())
        assertTrue(auth.getRememberPassword().first())
        assertEquals("user", auth.getSavedUsername().first())
        assertEquals("password", auth.getSavedPasswordSync())
        assertNull(auth.getToken().first())
        assertFalse(auth.saveAuthIfCurrent(oldGeneration, "late-token", "user", null))
        assertFalse(auth.updateMemberInfoIfCurrent(oldGeneration, "token", "user", null))
        assertNull(auth.getToken().first())
    }

    @Test
    fun expiredSessionKeepsAutoLoginPreference() = runBlocking {
        assertTrue(auth.saveAuthIfCurrent(auth.getAuthGeneration(), "expired-token", "user", null))
        auth.saveCredentials("user", "password", rememberPassword = true, autoLogin = true)

        assertTrue(auth.clearAuthSyncIfTokenMatches("expired-token"))
        withTimeout(5.seconds) { auth.getToken().first { it == null } }

        assertTrue(auth.getAutoLoginSync())
        assertTrue(auth.getAutoLogin().first())
        assertFalse(auth.clearAuthSyncIfTokenMatches("expired-token"))
    }
}
