package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundDownloadServicesTest {
    @Test fun foregroundLossRevokesNetworkAndReturnRechecksReachability() = runTest {
        var probes = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { probes++; respond("", HttpStatusCode.OK) }
        }))
        try {
            val active = MutableStateFlow(false)
            val monitor = ForegroundDownloadNetworkMonitor(backgroundScope, active, { client }, { "https://example.invalid/" }, "wifi unavailable")
            runCurrent(); assertEquals(0, probes); assertFalse(monitor.connections.value.connected)
            active.value = true; runCurrent()
            assertEquals(1, probes); assertTrue(monitor.connections.value.connected)
            assertFalse(monitor.connections.value.wifi)
            active.value = false; runCurrent()
            assertFalse(monitor.connections.value.connected)
            advanceTimeBy(60_000); runCurrent(); assertEquals(1, probes)
            active.value = true; runCurrent(); assertEquals(2, probes)
        } finally { client.close() }
    }
    @Test fun changedClientProviderIsUsedOnEveryTransfer() {
        val first = HttpClient(MockEngine { respond("") })
        val second = HttpClient(MockEngine { respond("") })
        try {
            var current = first
            val provider = UnboundDownloadClientProvider({ current }, "wifi unavailable")
            val allowed = DownloadNetworkState(allowed = true, wifiOnly = false)
            assertSame(first, provider.client(allowed))
            current = second
            assertSame(second, provider.client(allowed))
            assertFailsWith<PlatformIOException> { provider.client(allowed.copy(wifiOnly = true)) }
            assertFailsWith<PlatformIOException> { provider.client(allowed.copy(allowed = false)) }
        } finally { first.close(); second.close() }
    }
    @Test fun stoppedRuntimeNeverClaimsToStartBackgroundWork() = runTest {
        val job = SupervisorJob()
        val runtime = ForegroundDownloadRuntime(CoroutineScope(coroutineContext + job))
        runtime.onWorkAvailable()
        job.cancel()
        assertFailsWith<IllegalStateException> { runtime.onWorkAvailable() }
    }
}
