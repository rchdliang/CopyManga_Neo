package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareRequest
import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

/** No OS background service is implied: the queue runs only in this live application's scope. */
class ForegroundDownloadRuntime(private val applicationScope: CoroutineScope) : DownloadRuntime {
    override fun onWorkAvailable() {
        check(applicationScope.coroutineContext[Job]?.isActive == true) { "下载进程已停止，请重新打开应用后继续" }
    }
}

/** Foreground loss revokes permission and cancels the probe immediately. */
class ForegroundDownloadNetworkMonitor(
    scope: CoroutineScope,
    active: StateFlow<Boolean>,
    private val client: () -> HttpClient,
    private val probeUrl: () -> String,
    private val wifiUnavailable: String
) : DownloadNetworkMonitor {
    private val mutable = MutableStateFlow(DownloadConnection(false, false, unavailableReason = wifiUnavailable))
    override val connections = mutable.asStateFlow()
    init {
        scope.launch {
            active.collectLatest { foreground ->
                mutable.value = DownloadConnection(false, false, unavailableReason = wifiUnavailable)
                if (!foreground) return@collectLatest
                try {
                    while (currentCoroutineContext().isActive) {
                        val connected = try {
                            withTimeout(8.seconds) { client().prepareRequest(probeUrl()) { method = HttpMethod.Head }.execute { it.status.value in 100..599 } }
                        } catch (cancel: CancellationException) {
                            currentCoroutineContext().ensureActive()
                            false
                        } catch (_: Exception) { false }
                        mutable.value = DownloadConnection(connected, false, unavailableReason = wifiUnavailable)
                        delay(30.seconds)
                    }
                } finally {
                    mutable.value = DownloadConnection(false, false, unavailableReason = wifiUnavailable)
                }
            }
        }
    }
}

/** A generic HTTP engine cannot bind Wi-Fi: unsupported Wi-Fi-only transfers must fail closed. */
class UnboundDownloadClientProvider(
    private val client: () -> HttpClient,
    private val wifiUnavailable: String
) : DownloadClientProvider {
    override fun client(policy: DownloadNetworkState): HttpClient {
        if (!policy.allowed) throw PlatformIOException(policy.unavailableReason ?: "当前网络或应用状态不允许下载")
        if (policy.wifiOnly) throw PlatformIOException(wifiUnavailable)
        return client()
    }
}
