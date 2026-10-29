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
    @Test fun legacyAppVersionMigratesOnRestartAndRollbackAndIsPersisted() = runTest {
        val storage = Storage()
        val settings = """{"officialApp":{"version":"3.0.9","versionCode":"83","pseudoId":"saved-pseudo","umString":"saved-um"},"version":"custom-web","proxyMode":"DIRECT"}"""
        storage.files["network-settings.json"] = """{"current":$settings,"previous":$settings}"""
        val state = PortableNetworkState()
        val repo = repository(storage, state)
        repo.initialize().getOrThrow()
        val app = state.snapshot.settings.officialApp
        assertEquals("3.1.0", app.version)
        assertEquals("84", app.versionCode)
        assertEquals("saved-pseudo", app.pseudoId)
        assertEquals("saved-um", app.umString)
        assertEquals("custom-web", state.snapshot.settings.version)
        assertEquals(ProxyMode.DIRECT, state.snapshot.settings.proxyMode)
        repo.rollback().getOrThrow()
        assertEquals(app, state.snapshot.settings.officialApp)
        val restored = PortableNetworkState()
        repository(storage, restored).initialize().getOrThrow()
        assertEquals(app, restored.snapshot.settings.officialApp)
        assertFalse(storage.files.getValue("network-settings.json").contains("3.0.9"))
    }

    @Test fun customAppVersionPairsSurviveNormalizationAndIdentityImport() {
        for ((version, code) in listOf("3.0.9" to "99", "custom" to "83", "3.1.0" to "84")) {
            val app = OfficialAppSettings(version = version, versionCode = code, pseudoId = "saved-pseudo")
            assertEquals(app, PortableNetworkValidation.normalize(NetworkSettings(officialApp = app)).officialApp)
            val imported = AppCredentialIdentity(version, code, app.pseudoId, app.umString,
                app.device, app.deviceInfo, app.buildTime)
            assertEquals(app, imported.applyTo(OfficialAppSettings()))
        }
    }

    @Test fun webOnlyRestoresLegacyAppSettingsAndKeepsWebAcrossSaveRollbackAndReset() = runTest {
        val storage = Storage()
        val native = repository(storage, PortableNetworkState())
        native.initialize().getOrThrow()
        assertEquals(RequestMode.OFFICIAL_APP, native.settings.value.requestMode)
        native.save(native.settings.value.copy(version = "legacy")).getOrThrow()

        val state = PortableNetworkState(webOnly = true)
        val browser = repository(storage, state)
        browser.initialize().getOrThrow()
        assertEquals(listOf(RequestMode.WEB), browser.supportedRequestModes)
        assertEquals(RequestMode.WEB, browser.settings.value.requestMode)
        assertEquals("legacy", browser.settings.value.version)
        browser.rollback().getOrThrow()
        assertEquals(RequestMode.WEB, browser.settings.value.requestMode)
        browser.save(browser.settings.value.copy(requestMode = RequestMode.OFFICIAL_APP, version = "edited")).getOrThrow()
        assertEquals(RequestMode.WEB, browser.settings.value.requestMode)
        assertEquals("edited", browser.settings.value.version)
        browser.reset().getOrThrow()
        assertEquals(RequestMode.WEB, browser.settings.value.requestMode)
        // 已保存的当前模式也应为 Web，而不只是内存投影。
        val restarted = repository(storage, PortableNetworkState())
        restarted.initialize().getOrThrow()
        assertEquals(RequestMode.WEB, restarted.settings.value.requestMode)
    }

    @Test fun webOnlyStateRejectsAppProtocolBeforeTransportAndSurvivesDirectUpdates() = runTest {
        val state = PortableNetworkState(webOnly = true)
        state.update { it.copy(settings = it.settings.copy(requestMode = RequestMode.OFFICIAL_APP)) }
        assertEquals(RequestMode.WEB, state.snapshot.settings.requestMode)
        assertTrue(state.updateIfCurrent(state.snapshot.generation) {
            it.copy(settings = it.settings.copy(requestMode = RequestMode.OFFICIAL_APP))
        })
        assertEquals(RequestMode.WEB, state.snapshot.settings.requestMode)
        var requests = 0
        val http = HttpClient(MockEngine {
            requests++
            assertEquals("1", it.headers["platform"])
            respond("""{"code":200,"results":{}}""")
        })
        val client = CopyMangaClient(http, state)
        try {
            assertFailsWith<IllegalArgumentException> {
                client.raw(ApiRequest("/api/v3/comics", mode = RequestMode.OFFICIAL_APP))
            }
            assertEquals(0, requests)
            client.raw(ApiRequest("/api/v3/comics"))
            assertEquals(1, requests)
        } finally { client.close(); http.close() }
    }

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
