package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareRequest
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.NetworkInterface
import kotlin.time.Duration.Companion.seconds

/** Downloads live only for the lifetime of the desktop application's supplied scope. */
class DesktopDownloadRuntime(private val applicationScope: CoroutineScope) : DownloadRuntime {
    override fun onWorkAvailable() {
        check(applicationScope.coroutineContext[Job]?.isActive == true) {
            "桌面下载进程已停止，请重新打开应用后继续"
        }
    }
}

/**
 * Standard JVM networking cannot prove or bind Wi-Fi routing on Windows/Linux.
 * We explicitly report that missing capability rather than guessing from interface names.
 * A lightweight HEAD to the configured service verifies reachability without reading account data.
 */
class DesktopDownloadNetworkMonitor(
    scope: CoroutineScope,
    private val client: () -> HttpClient,
    private val probeUrl: () -> String
) : DownloadNetworkMonitor {
    private val mutable = MutableStateFlow(DownloadConnection(false, false, unavailableReason = WIFI_UNAVAILABLE))
    override val connections = mutable.asStateFlow()
    init {
        scope.launch {
            while (isActive) {
                val connected = try {
                    val networkPresent = withContext(Dispatchers.IO) {
                        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any {
                            it.isUp && !it.isLoopback && it.inetAddresses.toList().any { address -> !address.isLoopbackAddress }
                        }
                    }
                    networkPresent && withTimeout(8.seconds) { client().prepareRequest(probeUrl()) { method = HttpMethod.Head }.execute { it.status.value in 100..599 } }
                } catch (cancel: CancellationException) {
                    if (!isActive) throw cancel
                    false
                } catch (_: Exception) { false }
                mutable.value = DownloadConnection(connected, false, unavailableReason = WIFI_UNAVAILABLE)
                delay(30.seconds)
            }
        }
    }
    companion object {
        const val WIFI_UNAVAILABLE = "此桌面平台无法验证并绑定 Wi-Fi；如需下载，请在设置中关闭“仅 Wi-Fi 下载”"
    }
}

/** The supplied app client must have automatic retries disabled. Wi-Fi-only is rejected explicitly. */
class DesktopDownloadClientProvider(private val client: () -> HttpClient) : DownloadClientProvider {
    override fun client(policy: DownloadNetworkState): HttpClient {
        if (!policy.allowed) throw PlatformIOException(policy.unavailableReason ?: "当前网络不允许下载")
        if (policy.wifiOnly) throw PlatformIOException(DesktopDownloadNetworkMonitor.WIFI_UNAVAILABLE)
        return client()
    }
}

data class DesktopDownloadServices(
    val runtime: DesktopDownloadRuntime,
    val monitor: DesktopDownloadNetworkMonitor,
    val networkPolicy: DownloadNetworkPolicy,
    val clientProvider: DesktopDownloadClientProvider
)

fun desktopDownloadServices(
    applicationScope: CoroutineScope,
    settings: AppSettingsRepository,
    client: () -> HttpClient,
    probeUrl: () -> String
): DesktopDownloadServices {
    val monitor = DesktopDownloadNetworkMonitor(applicationScope, client, probeUrl)
    return DesktopDownloadServices(
        DesktopDownloadRuntime(applicationScope), monitor,
        DownloadNetworkPolicy(settings, monitor, applicationScope), DesktopDownloadClientProvider(client)
    )
}
