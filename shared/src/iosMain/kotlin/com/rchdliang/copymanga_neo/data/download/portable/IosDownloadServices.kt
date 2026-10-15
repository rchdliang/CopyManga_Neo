@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState

/** URLSession reachability is probed only while the app is active; no background entitlement is claimed. */
class IosDownloadNetworkMonitor(
    scope: CoroutineScope,
    client: () -> HttpClient,
    probeUrl: () -> String
) : DownloadNetworkMonitor {
    private val active = MutableStateFlow(false)
    private val delegate = ForegroundDownloadNetworkMonitor(scope, active, client, probeUrl, WIFI_UNAVAILABLE)
    override val connections = delegate.connections
    init {
        scope.launch(Dispatchers.Main) {
            val center = NSNotificationCenter.defaultCenter
            val foreground = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
                active.value = true
            }
            val background = center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) {
                active.value = false
            }
            active.value = UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateBackground
            try { awaitCancellation() }
            finally {
                active.value = false
                center.removeObserver(foreground)
                center.removeObserver(background)
            }
        }
    }
    companion object {
        const val WIFI_UNAVAILABLE = "当前 iOS 下载引擎无法绑定 Wi-Fi；如需下载，请关闭‘仅 Wi-Fi 下载’，并保持应用在前台"
    }
}

data class IosDownloadServices(
    val runtime: ForegroundDownloadRuntime,
    val monitor: IosDownloadNetworkMonitor,
    val networkPolicy: DownloadNetworkPolicy,
    val clientProvider: UnboundDownloadClientProvider
)

/** The supplied HTTP client is unauthenticated and has no automatic retries. */
fun iosDownloadServices(
    applicationScope: CoroutineScope,
    settings: AppSettingsRepository,
    client: () -> HttpClient,
    probeUrl: () -> String
): IosDownloadServices {
    val monitor = IosDownloadNetworkMonitor(applicationScope, client, probeUrl)
    return IosDownloadServices(ForegroundDownloadRuntime(applicationScope), monitor,
        DownloadNetworkPolicy(settings, monitor, applicationScope),
        UnboundDownloadClientProvider(client, IosDownloadNetworkMonitor.WIFI_UNAVAILABLE))
}
