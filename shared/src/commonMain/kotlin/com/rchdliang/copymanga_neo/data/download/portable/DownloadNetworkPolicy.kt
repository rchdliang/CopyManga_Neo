package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Native monitors must report validated connectivity; Wi-Fi plus cellular VPN is not Wi-Fi-only. */
data class DownloadConnection(
    val connected: Boolean, val wifi: Boolean, val bindingId: String? = null,
    val unavailableReason: String? = null
)
interface DownloadNetworkMonitor { val connections: StateFlow<DownloadConnection> }

data class DownloadNetworkState(
    val allowed: Boolean = false, val wifiOnly: Boolean = true, val bindingId: String? = null,
    val unavailableReason: String? = null
)

fun downloadNetworkAllowed(wifiOnly: Boolean, connected: Boolean, wifi: Boolean): Boolean =
    connected && (!wifiOnly || wifi)

/** Client selection must preserve Wi-Fi binding and disable transport retries for official calls. */
interface DownloadClientProvider {
    fun client(policy: DownloadNetworkState): HttpClient
}

/** Android starts/maintains its foreground service; desktop hosts keep an app-owned runner alive. */
interface DownloadRuntime {
    fun onWorkAvailable()
}

class DownloadNetworkPolicy(
    settings: AppSettingsRepository, monitor: DownloadNetworkMonitor, scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(DownloadNetworkState())
    val state = mutableState.asStateFlow()
    val ready = CompletableDeferred<Unit>()
    init {
        scope.launch {
            combine(settings.settings, monitor.connections) { preferences, connection ->
                DownloadNetworkState(
                    downloadNetworkAllowed(preferences.wifiOnlyDownloads, connection.connected, connection.wifi),
                    preferences.wifiOnlyDownloads, connection.bindingId,
                    if (preferences.wifiOnlyDownloads && !connection.wifi) connection.unavailableReason else null
                )
            }.distinctUntilChanged().catch { error ->
                // Fail closed: an unreadable network policy must never authorize cellular traffic.
                mutableState.value = DownloadNetworkState()
                ready.complete(Unit)
                DownloadLog.e("DownloadNetworkPolicy", "读取下载网络策略失败", error)
            }.collect {
                mutableState.value = it
                ready.complete(Unit)
            }
        }
    }
}
