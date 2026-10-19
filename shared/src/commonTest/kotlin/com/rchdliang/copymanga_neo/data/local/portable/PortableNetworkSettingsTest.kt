package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.data.repository.portable.PortableShareLinkRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PortableNetworkSettingsTest {
    @Test fun themeLoadingSettingsPersistRollbackAndDoNotCancelExistingRequests() = runTest {
        val storage = Storage()
        val state = PortableNetworkState()
        val repo = repository(storage, state)
        repo.initialize().getOrThrow()
        val generation = state.snapshot.generation
        val activeRequest = Job()
        state.register(activeRequest, generation)
        try {
            repo.save(repo.settings.value.copy(themeFetchConcurrency = 5, themeFetchIntervalMillis = 1200)).getOrThrow()
            assertEquals(generation, state.snapshot.generation)
            assertTrue(activeRequest.isActive)
            val restored = repository(storage, PortableNetworkState())
            restored.initialize().getOrThrow()
            assertEquals(5, restored.settings.value.themeFetchConcurrency)
            assertEquals(1200, restored.settings.value.themeFetchIntervalMillis)
            repo.rollback().getOrThrow()
            assertEquals(3, repo.settings.value.themeFetchConcurrency)
            assertEquals(500, repo.settings.value.themeFetchIntervalMillis)
            assertEquals(generation, state.snapshot.generation)
            assertTrue(repo.save(repo.settings.value.copy(themeFetchConcurrency = 7)).isFailure)
            assertTrue(repo.save(repo.settings.value.copy(themeFetchIntervalMillis = -1)).isFailure)
            assertEquals(3, repo.settings.value.themeFetchConcurrency)
        } finally { state.unregister(activeRequest); activeRequest.cancel() }
    }
    private class Storage : DocumentStorage {
        val files = mutableMapOf<String, String>()
        var afterWrite: (() -> Unit)? = null
        override suspend fun read(key: String) = files[key]
        override suspend fun write(key: String, value: String) { files[key] = value; afterWrite?.invoke() }
        override suspend fun remove(key: String) { files.remove(key) }
    }
    private val identity = object : PortableOfficialIdentity {
        override fun resolve(settings: OfficialAppSettings) = settings.copy(
            pseudoId = settings.pseudoId.ifBlank { "0123456789abcdef" },
            umString = settings.umString.ifBlank { "0123456789abcdef0123456789abcdef" },
            device = settings.device.ifBlank { "test" }, deviceInfo = settings.deviceInfo.ifBlank { "test" },
            buildTime = settings.buildTime.ifBlank { "2025.05.22" })
        override val tapString = ""
        override val loginDate = "2026-10-14"
        override fun dateHeader() = "2026.10.14"
    }
    private val diagnostics = object : NetworkDiagnostics {
        override suspend fun probe(settings: NetworkSettings) = emptyList<NetworkProbe>()
        override suspend fun discovery(settings: NetworkSettings) = error("not used")
        override fun officialReport(settings: NetworkSettings) = "test"
    }
    private fun repository(storage: Storage, state: PortableNetworkState) = PortableNetworkSettings(
        storage, state, identity, diagnostics, PortableRiskControlGate(), clearAppSession = {})

    @Test fun discoveredShareHostSurvivesRestartWithoutNetworkOrGenerationChange() = runTest {
        val storage = Storage(); val state = PortableNetworkState(); val repo = repository(storage, state)
        repo.initialize().getOrThrow()
        repo.save(state.snapshot.settings.copy(proxyMode = ProxyMode.DIRECT)).getOrThrow()
        val generation = state.snapshot.generation
        val ongoing = Job()
        state.register(ongoing, generation)
        var requests = 0
        val http = HttpClient(MockEngine {
            requests++
            respond("""{"code":200,"results":{"share":["share.example"]}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val client = CopyMangaClient(http, state, proxy = PortableProxyPolicy { })
        try {
            val shares = PortableShareLinkRepository(client, repo::persistShareHosts)
            assertEquals("https://share.example", shares.baseUrl().getOrThrow())
            assertEquals(generation, state.snapshot.generation)
            assertTrue(ongoing.isActive)
            assertEquals("https://share.example", shares.baseUrl().getOrThrow())
            assertEquals(1, requests)
            val restored = PortableNetworkState(); val restoredRepo = repository(storage, restored)
            restoredRepo.initialize().getOrThrow()
            val offlineHttp = HttpClient(MockEngine { error("Offline discovery must not be requested") })
            val offline = CopyMangaClient(offlineHttp, restored, proxy = PortableProxyPolicy { })
            try {
                assertEquals("https://share.example",
                    PortableShareLinkRepository(offline, restoredRepo::persistShareHosts).baseUrl().getOrThrow())
                // Automatic discovery must not replace the user's previous-settings rollback point.
                restoredRepo.rollback().getOrThrow()
                assertEquals(ProxyMode.SYSTEM, restored.snapshot.settings.proxyMode)
                assertTrue(restored.snapshot.settings.shareHosts.isEmpty())
            } finally { offline.close(); offlineHttp.close() }
        } finally { state.unregister(ongoing); ongoing.cancel(); client.close(); http.close() }
    }

    @Test fun failedShareDiscoveryFallsBackButCanRetryOnNextShare() = runTest {
        val storage = Storage(); val state = PortableNetworkState(); val repo = repository(storage, state)
        repo.initialize().getOrThrow()
        var requests = 0
        val http = HttpClient(MockEngine {
            requests++
            respond(if (requests == 1) """{"code":200,"results":{"share":[]}}"""
                else """{"code":200,"results":{"share":["share.example"]}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val client = CopyMangaClient(http, state)
        try {
            val shares = PortableShareLinkRepository(client, repo::persistShareHosts)
            assertEquals("https://${state.snapshot.settings.wwwHost}", shares.baseUrl().getOrThrow())
            assertEquals("https://share.example", shares.baseUrl().getOrThrow())
            assertEquals(2, requests)
        } finally { client.close(); http.close() }
    }

    @Test fun shareDiscoveryRejectsConcurrentSessionChangeAndRestoresDisk() = runTest {
        val storage = Storage(); val state = PortableNetworkState(); val repo = repository(storage, state)
        repo.initialize().getOrThrow()
        val before = storage.files.getValue("network-settings.json")
        val generation = state.snapshot.generation
        storage.afterWrite = { storage.afterWrite = null; state.update { it.copy(webSession = PortableSession("new", "user:new")) } }
        assertFails { repo.persistShareHosts(listOf("share.example"), generation) }
        assertEquals(before, storage.files["network-settings.json"])
        assertTrue(state.snapshot.settings.shareHosts.isEmpty())
        assertEquals("new", state.snapshot.webSession.token)
    }

    @Test fun concurrentSessionChangeRollsBackSettingsFile() = runTest {
        val storage = Storage(); val state = PortableNetworkState(); val repo = repository(storage, state)
        repo.initialize().getOrThrow()
        val before = storage.files.getValue("network-settings.json")
        val previous = state.snapshot.settings
        storage.afterWrite = { storage.afterWrite = null; state.update { it.copy(webSession = PortableSession("new", "user:new")) } }
        assertTrue(repo.save(previous.copy(proxyMode = ProxyMode.DIRECT)).isFailure)
        assertEquals(before, storage.files["network-settings.json"])
        assertEquals(previous, state.snapshot.settings)
        assertEquals("new", state.snapshot.webSession.token)
    }
    @Test fun importedSettingsWriteRejectsGenerationChangeAndRestoresDisk() = runTest {
        val storage = Storage(); val state = PortableNetworkState(); val repo = repository(storage, state)
        repo.initialize().getOrThrow()
        val before = storage.files.getValue("network-settings.json")
        val generation = state.snapshot.generation
        storage.afterWrite = { storage.afterWrite = null; state.update { it.copy(webSession = PortableSession("new", "user:new")) } }
        assertFails { repo.persistImportedConfiguration(state.snapshot.settings.copy(proxyMode = ProxyMode.DIRECT), generation) }
        assertEquals(before, storage.files["network-settings.json"])
    }
    @Test fun successfulSettingsSaveAndRollbackRemainDurable() = runTest {
        val storage = Storage(); val state = PortableNetworkState(); val repo = repository(storage, state)
        repo.initialize().getOrThrow()
        val before = state.snapshot.settings
        repo.save(before.copy(proxyMode = ProxyMode.DIRECT)).getOrThrow()
        assertEquals(ProxyMode.DIRECT, state.snapshot.settings.proxyMode)
        repo.rollback().getOrThrow()
        assertEquals(before, state.snapshot.settings)
        val restored = PortableNetworkState()
        repository(storage, restored).initialize().getOrThrow()
        assertEquals(before, restored.snapshot.settings)
    }
}
